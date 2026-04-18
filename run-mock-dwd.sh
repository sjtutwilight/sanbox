#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SANDBOX_DIR="${ROOT_DIR}"
DATAPLATFORM_DIR="${ROOT_DIR}/../DataPlatform"
SANDBOX_COMPOSE="${SANDBOX_DIR}/docker-compose.yml"
DATAPLATFORM_COMPOSE="${DATAPLATFORM_DIR}/docker-compose.yml"

NETWORK_NAME="${EXPERIMENT_NETWORK:-realtime-bus}"
SANDBOX_PROJECT="${SANDBOX_PROJECT:-sandbox}"
DATAPLATFORM_PROJECT="${DATAPLATFORM_PROJECT:-dataplatform}"

SANDBOX_SERVICES=()
DATAPLATFORM_SERVICES=(zookeeper kafka kafka-ui redis redisinsight postgres init-db jobmanager taskmanager)

FLINK_JOB_JAR="${FLINK_JOB_JAR:-/opt/flink/custom-lib/realtime-pipeline.jar}"
FLINK_JOB_MAIN="${FLINK_JOB_MAIN:-com.twilight.realtime.jobs.DexSwapDwdJob}"
FLINK_CONFIG_PATH="${FLINK_CONFIG_PATH:-/opt/flink/conf/realtime-pipeline.properties}"
LOAD_EXECUTOR_URL="${LOAD_EXECUTOR_URL:-http://localhost:18082}"

MOCK_REQUEST_PAYLOAD=${MOCK_REQUEST_PAYLOAD:-'{
  "chainIds": [1, 42161],
  "swapsPerChain": 120,
  "initMetadata": true,
  "refreshAccountTags": true,
  "produceTokenPrices": true,
  "priceUpdateCycles": 1,
  "emitDelayMillis": 50
}'}

log() {
  printf '[run-mock-dwd] %s\n' "$*" >&2
}

ensure_network() {
  if ! docker network inspect "${NETWORK_NAME}" >/dev/null 2>&1; then
    log "creating shared network ${NETWORK_NAME}"
    docker network create "${NETWORK_NAME}" >/dev/null
  else
    log "shared network ${NETWORK_NAME} already exists"
  fi
}

compose_cmd() {
  local compose_file=$1
  local project=$2
  shift 2
  docker compose --project-name "${project}" -f "${compose_file}" "$@"
}

start_services() {
  ensure_network
  if [ "${#SANDBOX_SERVICES[@]}" -gt 0 ]; then
    log "starting sandbox services: ${SANDBOX_SERVICES[*]}"
    compose_cmd "${SANDBOX_COMPOSE}" "${SANDBOX_PROJECT}" up -d "${SANDBOX_SERVICES[@]}"
    attach_to_bus "${SANDBOX_COMPOSE}" "${SANDBOX_PROJECT}" "${SANDBOX_SERVICES[@]}"
  else
    log "no sandbox services configured"
  fi
  log "starting dataplatform services: ${DATAPLATFORM_SERVICES[*]}"
  compose_cmd "${DATAPLATFORM_COMPOSE}" "${DATAPLATFORM_PROJECT}" up -d "${DATAPLATFORM_SERVICES[@]}"
  attach_to_bus "${DATAPLATFORM_COMPOSE}" "${DATAPLATFORM_PROJECT}" "${DATAPLATFORM_SERVICES[@]}"
  wait_for_infra
}

stop_services() {
  log "stopping dataplatform services"
  compose_cmd "${DATAPLATFORM_COMPOSE}" "${DATAPLATFORM_PROJECT}" stop "${DATAPLATFORM_SERVICES[@]}"
  if [ "${#SANDBOX_SERVICES[@]}" -gt 0 ]; then
    log "stopping sandbox services"
    compose_cmd "${SANDBOX_COMPOSE}" "${SANDBOX_PROJECT}" stop "${SANDBOX_SERVICES[@]}"
  fi
}

attach_to_bus() {
  local compose_file=$1
  local project=$2
  shift 2
  for service in "$@"; do
    if ! container_ids=$(compose_cmd "${compose_file}" "${project}" ps -q "${service}"); then
      continue
    fi
    for cid in ${container_ids}; do
      if [ -n "${cid}" ]; then
        docker network connect "${NETWORK_NAME}" "${cid}" >/dev/null 2>&1 || true
      fi
    done
  done
}

wait_for_infra() {
  log "waiting for Postgres..."
  until docker exec postgres pg_isready -U twilight -d twilight >/dev/null 2>&1; do
    sleep 2
  done
  log "waiting for Kafka..."
  until docker exec crypto-kafka kafka-broker-api-versions --bootstrap-server kafka:29092 >/dev/null 2>&1; do
    sleep 2
  done
  log "waiting for Redis..."
  until docker exec crypto-redis redis-cli ping >/dev/null 2>&1; do
    sleep 2
  done
  log "waiting for Flink JobManager..."
  until curl -sf http://localhost:8081 >/dev/null 2>&1; do
    sleep 2
  done
}

submit_dex_swap_job() {
  if [[ "${SKIP_FLINK_SUBMIT:-0}" == "1" ]]; then
    log "SKIP_FLINK_SUBMIT=1, skipping Flink job submission"
    return
  fi
  if ! docker exec flink-jobmanager test -f "${FLINK_JOB_JAR}" >/dev/null 2>&1; then
    log "Flink jar ${FLINK_JOB_JAR} not found inside flink-jobmanager, skipping job submission"
    return
  fi
  log "submitting dex_swap_dwd_job to Flink"
  docker exec flink-jobmanager bash -c "/opt/flink/bin/flink run -d -c ${FLINK_JOB_MAIN} ${FLINK_JOB_JAR} --config ${FLINK_CONFIG_PATH}" >/dev/null
}

trigger_mock_generator() {
  if [[ "${SKIP_MOCK_TRIGGER:-0}" == "1" ]]; then
    log "SKIP_MOCK_TRIGGER=1, skipping mock data generation"
    return
  fi
  log "checking load-executor health at ${LOAD_EXECUTOR_URL}/actuator/health"
  until curl -sf "${LOAD_EXECUTOR_URL}/actuator/health" >/dev/null 2>&1; do
    log "waiting for load-executor..."
    sleep 2
  done
  log "triggering /datagenerator/onchain/mock"
  curl -sf -H "Content-Type: application/json" \
    -d "${MOCK_REQUEST_PAYLOAD}" \
    "${LOAD_EXECUTOR_URL}/datagenerator/onchain/mock" >/dev/null
  log "mock request sent successfully"
}

show_status() {
  log "active containers on ${NETWORK_NAME}:"
  docker ps --filter "network=${NETWORK_NAME}"
}

usage() {
  cat <<EOF
Usage: $0 <command>

Commands:
  up        Start Postgres/Kafka/Redis/Flink (DataPlatform), attach shared network,
            submit the dex_swap_dwd Flink job, and trigger onchain mock generation.
  infra     Only start/attach the infrastructure (no job submission or mock trigger).
  down      Stop the services started by this script.
  status    Show containers currently attached to ${NETWORK_NAME}.

Environment overrides:
  EXPERIMENT_NETWORK        Shared docker network name (default: ${NETWORK_NAME})
  FLINK_JOB_JAR             Jar path inside jobmanager container
  FLINK_JOB_MAIN            Fully qualified Flink main class
  FLINK_CONFIG_PATH         Config file path passed to the job
  LOAD_EXECUTOR_URL         Base URL for load-executor service
  SKIP_FLINK_SUBMIT         Set to 1 to skip automatic Flink submission
  SKIP_MOCK_TRIGGER         Set to 1 to skip calling /datagenerator/onchain/mock
  MOCK_REQUEST_PAYLOAD      JSON payload sent to the mock endpoint
EOF
}

main() {
  local cmd=${1:-help}
  case "${cmd}" in
    up)
      start_services
      submit_dex_swap_job
      trigger_mock_generator
      ;;
    infra)
      start_services
      ;;
    down)
      stop_services
      ;;
    status)
      show_status
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"

# load-execution Specification

## Purpose

Define how the load executor accepts commands, generates load plans, and executes experiment operations with metrics.

## Requirements

### Requirement: Command Intake
The system SHALL accept run control commands through a dedicated API.

#### Scenario: Submit command
- **WHEN** a client submits `POST /commands` with experiment, operation, and load shape
- **THEN** the executor validates the command
- **AND** creates an experiment run with an assigned run id
- **AND** starts execution based on generated load plan

#### Scenario: Pause, resume, and stop
- **WHEN** a client calls `/commands/{runId}/pause`, `/commands/{runId}/resume`, or `/commands/{runId}/stop`
- **THEN** the executor transitions run status accordingly
- **AND** persists status for subsequent query APIs

### Requirement: Load Plan Generation
The system SHALL translate load shape into executable phases.

#### Scenario: Constant load shape
- **WHEN** load shape type is `CONSTANT`
- **THEN** planner creates a single phase with target qps and concurrency

#### Scenario: Ramp load shape
- **WHEN** load shape type is `RAMP` with duration and step parameters
- **THEN** planner creates multiple sequential phases with interpolated qps

### Requirement: Runtime Execution and Metrics
The system SHALL dispatch invocations according to phase constraints and publish runtime metrics.

#### Scenario: Tick-based dispatch
- **WHEN** scheduler tick executes for an active run
- **THEN** executor calculates dispatch count from target qps and tick duration
- **AND** respects max concurrency constraints

#### Scenario: Metrics publication
- **WHEN** invocations execute
- **THEN** executor records request totals, success/failure counts, and latency metrics
- **AND** exposes metrics via actuator/prometheus endpoints

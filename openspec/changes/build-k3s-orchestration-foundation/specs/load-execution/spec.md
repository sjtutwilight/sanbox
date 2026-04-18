## MODIFIED Requirements

### Requirement: Command Intake
The system SHALL accept run control commands through a dedicated API with explicit platform/scenario profile context.

#### Scenario: Submit command
- **WHEN** a client submits `POST /commands` with experiment, operation, load shape, and profile context (`platform`, `scenario`)
- **THEN** the executor validates the command and profile compatibility
- **AND** creates an experiment run with an assigned run id
- **AND** starts execution based on generated load plan and selected datasource strategy

#### Scenario: Pause, resume, and stop
- **WHEN** a client calls `/commands/{runId}/pause`, `/commands/{runId}/resume`, or `/commands/{runId}/stop`
- **THEN** the executor transitions run status accordingly
- **AND** persists status for subsequent query APIs

### Requirement: Runtime Execution and Metrics
The system SHALL dispatch invocations according to phase constraints and publish runtime metrics with orchestration dimensions.

#### Scenario: Tick-based dispatch
- **WHEN** scheduler tick executes for an active run
- **THEN** executor calculates dispatch count from target qps and tick duration
- **AND** respects max concurrency constraints

#### Scenario: Metrics publication
- **WHEN** invocations execute
- **THEN** executor records request totals, success/failure counts, and latency metrics
- **AND** exposes metrics via actuator/prometheus endpoints
- **AND** tags runtime telemetry with `platform`, `scenario`, and `experimentRunId`

## ADDED Requirements

### Requirement: Profile-Driven Datasource Strategy
The system SHALL select datasource connection strategy by profile instead of hard-coding a single deployment mode.

#### Scenario: Select Redis strategy by scenario profile
- **WHEN** run profile indicates `scenario=redis-sharding`
- **THEN** executor uses Redis cluster-capable connection strategy
- **AND** experiment operation logic remains unchanged at business invocation layer

#### Scenario: Select non-cluster strategy for other profiles
- **WHEN** run profile indicates a scenario that does not require Redis cluster semantics
- **THEN** executor uses the configured non-cluster strategy (such as standalone or sentinel)
- **AND** strategy selection does not require experiment-level branching logic

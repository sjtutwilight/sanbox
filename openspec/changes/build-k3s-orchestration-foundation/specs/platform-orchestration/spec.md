## ADDED Requirements

### Requirement: Platform Profile Catalog
The system SHALL expose a platform orchestration profile catalog that is independent from any single middleware implementation.

#### Scenario: List available platform and scenario profiles
- **WHEN** a client requests platform orchestration metadata
- **THEN** the system returns a catalog containing `platform` and `scenario` profile identifiers
- **AND** each profile includes capability tags, required dependencies, and observability labels

### Requirement: K3s Baseline Orchestration
The system SHALL define a reusable k3s baseline orchestration contract for namespace isolation, deployment boundaries, and configuration injection.

#### Scenario: Prepare baseline orchestration resources
- **WHEN** orchestration setup is executed for `platform=k3s`
- **THEN** the system provisions the required namespace boundaries and component deployment groups
- **AND** configuration and secret injection paths are defined through a stable contract

### Requirement: Scenario-Pluggable Validation
The system SHALL support running validation scenarios as pluggable overlays on top of a selected platform profile.

#### Scenario: Run redis sharding as first validation scenario
- **WHEN** an execution is started with `platform=k3s` and `scenario=redis-sharding`
- **THEN** the system applies scenario-specific dependencies and parameters without changing platform baseline semantics
- **AND** the scenario is treated as one selectable validation path rather than a mandatory platform dependency

### Requirement: Unified Observability Labels
The system SHALL enforce unified observability labels for all orchestrated executions.

#### Scenario: Emit metrics and logs with platform dimensions
- **WHEN** a run is executed under platform orchestration
- **THEN** emitted metrics and logs include `platform`, `scenario`, and `experimentRunId` dimensions
- **AND** the dimensions are consistent across control plane and execution plane telemetry

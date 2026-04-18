# experiment-orchestration Specification

## Purpose

Define how the control plane discovers experiments and orchestrates operation lifecycle against the load executor.

## Requirements

### Requirement: Experiment Catalog Availability
The system SHALL expose a discoverable experiment catalog to UI clients.

#### Scenario: List experiments
- **WHEN** a client requests `GET /api/experiments`
- **THEN** the control plane returns the experiment list aggregated from load executor metadata
- **AND** each experiment includes groups and operations metadata required for rendering and invocation

### Requirement: Operation Lifecycle Orchestration
The system SHALL orchestrate start/stop/status operations through a stable API boundary.

#### Scenario: Start operation
- **WHEN** a client submits `POST /api/experiments/{expId}/groups/{groupId}/operations/{opId}/start`
- **THEN** the control plane maps request parameters to an executor command
- **AND** forwards the command to load executor `POST /commands`
- **AND** returns the created run/task identity to the client

#### Scenario: Stop operation
- **WHEN** a client submits `POST /api/experiments/{expId}/groups/{groupId}/operations/{opId}/stop`
- **THEN** the control plane resolves run identity for the task
- **AND** forwards stop to load executor
- **AND** returns updated task status

#### Scenario: Query operation status
- **WHEN** a client submits `GET /api/experiments/{expId}/groups/{groupId}/operations/{opId}/status`
- **THEN** the control plane returns current task status
- **AND** status is mapped from executor run status consistently

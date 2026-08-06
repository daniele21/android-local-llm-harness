# Harness resource diagnostics composition

Status: active
Document type: feature-specification
Owner: apps/local-llm-phone-test
Canonical scope: phone.diagnostics.resources
Read when: changing connected-app memory, thermal or resource diagnostics
Last reviewed: 2026-08-06

## Decision

The connected phone-test application composes the existing `AndroidResourceSnapshotProvider` and `ResourceSnapshotRecorder` directly inside the application boundary.

Resource capture is explicit and user-driven. Opening Diagnostics or refreshing ordinary telemetry does not create a resource sample, and no timer, service or global background loop is introduced.

## Ownership

- Android measurement capability: `AndroidResourceSnapshotProvider`.
- Recording boundary: `ResourceSnapshotRecorder`.
- Retention: the process-scoped `TelemetryRepository` owned by `HarnessRuntimeGraph`.
- UI mapping: `HarnessResourceSource`.
- Trigger and lifecycle: the phone-test Activity diagnostics executor.

The provider and recorder do not own the runtime, model store or telemetry lifecycle.

## Measurements

A snapshot may contain:

- process PSS;
- native heap allocated bytes;
- Java heap used bytes;
- Android available memory;
- Android low-memory flag;
- Android thermal status.

Measurements unavailable on a device or Android version remain nullable in the contract and are rendered as `Unavailable`. The UI must not convert missing values to zero because zero would imply a real measurement.

Thermal state remains `UNKNOWN` where the platform API is unavailable or cannot be read.

## Threading and interactions

Capture runs on the same dedicated single-thread diagnostics executor used for Health. Health, resource capture and inference/validation actions are mutually excluded by the Activity state so diagnostic work does not compete with an active local generation operation.

Activity destruction calls `shutdownNow()` on the executor. There is no cross-process capture and no hidden work after process death.

## Persistence and privacy

Snapshots are bounded by the in-memory telemetry retention policy and are lost on Android process death. They contain numeric process/device measurements and coarse thermal state only.

They do not contain:

- prompts or generated output;
- file names or paths;
- model bytes;
- arbitrary exception messages;
- user document content.

A future Room-backed history may reuse the same `TelemetryRepository` contract, but is deliberately not introduced in this Compose/runtime ownership slice.

## Validation

Host tests verify formatting, persistence and unavailable-value semantics. Representative PSS, heap, available-memory and thermal behavior must still be validated on physical Android arm64 hardware; emulator or host values are not production performance evidence.

# Health engine

`observability/health-engine` is the Phase 2 control-plane boundary for independently testable health checks. It depends on stable observability and model-store contracts and does not depend on Android UI, transport implementations or `llama.cpp` internals.

## Responsibilities

The module:

- registers health checks by stable ID;
- runs all checks or a selected subset;
- measures check duration with an injectable monotonic clock;
- aggregates the suite using the worst result (`FAIL`, `WARN`, `NOT_RUN`, `PASS`);
- persists every result through `TelemetryRepository.saveHealth`;
- converts unexpected exceptions into a privacy-safe failure;
- exposes installed-model integrity through `ModelStore.verify`.

It does not own Room, runtime generation, console rendering, cross-application transport, benchmark history, memory or thermal probes.

## Core API

```kotlin
val engine = HealthEngine(
    checks = listOf(ModelIntegrityHealthCheck(modelStore)),
    telemetryRepository = telemetryRepository,
)

val fullReport = engine.runAll()
val selectedReport = engine.run(listOf("model-integrity"))
```

`HealthCheck` implementations return a `HealthAssessment` containing only a status and a privacy-safe summary. The engine converts it to the stable `HealthCheckResult` contract, records its duration and persists it.

Unknown IDs produce an explicit `NOT_RUN` result rather than throwing. Duplicate or blank registered IDs are rejected during engine construction.

## Model integrity check

`ModelIntegrityHealthCheck` evaluates every artifact returned by `ModelStore.snapshot()` using `ModelStore.verify()`.

Outcomes:

- no installed artifacts: `WARN`;
- every artifact verifies: `PASS`;
- one or more artifacts fail verification: `FAIL`.

The persisted detail includes aggregate counts only. It does not expose model paths, bytes, expected digests, actual digests or arbitrary verification details.

## Failure isolation and privacy

A check may fail independently without breaking inference or another health check. Unexpected exceptions are converted to:

```text
status = FAIL
detail = Health check failed unexpectedly
```

The original exception message is deliberately excluded because it may contain private paths, prompts or implementation details.

Persistence uses the existing `TelemetryRepository` boundary. The Room-backed implementation therefore makes health results available to the owning embedded application without coupling this module to Room.

## Threading

`HealthEngine` executes checks synchronously on the caller's thread. Callers must choose an appropriate worker or control-plane executor for checks that perform file verification or other blocking work. This slice does not introduce hidden global executors.

## Testing

The module includes deterministic tests for:

- suite aggregation;
- duration measurement;
- result persistence;
- unknown check IDs;
- privacy-safe exception handling;
- no-model, valid-model and invalid-model integrity outcomes;
- absence of private model paths from persisted details.

The aggregate repository gate runs the tests, Android Lint and AAR assembly.

## Current limitations

This slice provides the orchestration foundation and the first concrete integrity check. It does not yet provide:

- generation sanity prompts and expected-output assertions;
- cache health checks;
- memory or thermal checks;
- cold-versus-warm benchmark classification;
- periodic scheduling;
- console controls or visualization;
- cross-application access.

The separate console application still requires the planned signature-protected diagnostics bridge to access another application's private control-plane data.

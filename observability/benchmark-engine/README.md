# observability/benchmark-engine

This Android library converts persisted generation telemetry into scoped performance baselines and regression health checks.

## Responsibilities

- build deterministic benchmark snapshots from completed runs;
- keep cold and warm measurements separate;
- persist one active baseline per benchmark key;
- compare post-baseline samples against configurable policies;
- expose regression outcomes through the shared health-check contract.

## Dependencies

The module depends only on:

- `core/contracts` for application, use-case, model and load identifiers;
- `observability/contracts` for telemetry, baseline and repository contracts;
- `observability/health-engine` for `HealthCheck` and `HealthAssessment`.

It must not depend on the runtime implementation, Room, Android UI, Capacitor or `llama.cpp` internals.

## Entry points

- `BenchmarkPolicy`
- `BenchmarkBaselineRecorder`
- `BenchmarkCaptureResult`
- `BenchmarkRegressionHealthCheck`

See [`docs/benchmark-engine.md`](../../docs/benchmark-engine.md) for behavior, examples, privacy rules and limitations.

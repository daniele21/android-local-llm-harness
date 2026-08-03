# Benchmark baselines and regression checks

## Purpose

`observability/benchmark-engine` turns completed generation telemetry into repeatable performance baselines and privacy-safe health results.

The module does not execute inference and does not select models. It consumes the metrics already recorded through `TelemetryRepository`, preserving the separation between the runtime data plane and the observability control plane.

## Benchmark identity

Every baseline is scoped by a `BenchmarkKey` containing:

- `applicationId`;
- `useCaseId`;
- immutable model SHA-256 digest;
- explicit `ModelLoadKind.COLD` or `ModelLoadKind.WARM`.

`ModelLoadKind.UNKNOWN` is rejected when the key is created. This prevents mixed or ambiguous samples from becoming a baseline.

Cold and warm measurements are intentionally independent. A warm generation is never compared with a cold-load baseline and vice versa.

## Capturing a baseline

`BenchmarkBaselineRecorder` reads recent completed runs matching the key, limits them to the configured baseline window and persists a `BenchmarkBaseline` when the minimum sample count is available.

```kotlin
val key = BenchmarkKey(
    applicationId = ApplicationId("com.example.app"),
    useCaseId = UseCaseId("assistant"),
    modelDigest = modelDigest,
    modelLoadKind = ModelLoadKind.WARM,
)

val result = BenchmarkBaselineRecorder(
    repository = telemetryRepository,
    policy = BenchmarkPolicy(
        baselineWindowSize = 20,
        minimumBaselineSamples = 5,
    ),
).capture(key)
```

The recorder calculates:

- median time to first token;
- nearest-rank p95 time to first token;
- median total latency;
- nearest-rank p95 total latency;
- median decode throughput.

Metrics that were not available in the source runs remain `null`; they are not fabricated or replaced with zero.

## Regression health check

`BenchmarkRegressionHealthCheck` implements the common `HealthCheck` contract. It loads the baseline for one key and compares only completed runs whose completion timestamp is later than the baseline capture timestamp.

The default policy reports:

- `WARN` when no baseline exists;
- `WARN` when there are not enough post-baseline samples;
- `WARN` when no metric is comparable;
- `PASS` when all comparable metrics remain within policy;
- `FAIL` when one or more metrics regress beyond policy.

The configurable comparisons are:

- current median TTFT divided by baseline median TTFT;
- current p95 total latency divided by baseline p95 total latency;
- current median decode throughput divided by baseline median decode throughput.

The health detail names only metric classes such as `median TTFT` or `p95 total latency`. It does not include prompts, generated output, model paths or model digests.

## Stable health-check IDs

The health-check ID contains the benchmark namespace and every key dimension:

```text
benchmark-regression:<applicationId>:<useCaseId>:<modelDigest>:<COLD|WARM>
```

This keeps IDs unique when the same use case is benchmarked with different models or load classifications. Consumers should treat the ID as opaque and should not display it as a user-facing model label.

## Persistence

The in-memory and Room telemetry implementations persist benchmark baselines behind the same `TelemetryRepository` contract.

Room schema version 3 adds the benchmark baseline table and a non-destructive migration from schema version 2. Existing run, log, health and resource telemetry remains available after migration.

A new capture replaces the previous baseline with the same stable key. The current design stores the active baseline, not an unbounded history of prior baselines.

## Threading and lifecycle

The benchmark engine performs synchronous repository reads and writes. It is intended for explicit developer or administrative actions, not the generation hot path.

Repository implementations retain ownership of their own threading guarantees. The Room adapter continues to serialize database access according to its existing lifecycle and shutdown contract.

## Privacy

The engine uses only structured telemetry already allowed by the observability contracts:

- identifiers required to scope the benchmark;
- timestamps;
- latency values;
- token counts and throughput;
- load classification.

It does not read or persist prompt text, generated text, arbitrary backend exceptions or model file paths.

## Testing

The slice includes deterministic tests for:

- median and nearest-rank p95 calculations;
- minimum baseline sample enforcement;
- cold/warm isolation;
- passing comparisons;
- multi-metric regressions;
- missing baseline and missing comparison windows;
- privacy-safe health details;
- Room persistence and schema migration behavior.

## Limitations

Host-side and simulated tests validate the calculation, persistence and orchestration behavior. They do not establish representative Android device performance.

Production baselines still require repeated measurements on physical `arm64-v8a` devices with supported external GGUF models. Device architecture, memory pressure, thermal throttling and OEM scheduling can materially change the observed metrics.

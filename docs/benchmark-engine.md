# Benchmark baselines, history and regression checks

## Purpose

`observability/benchmark-engine` turns completed generation telemetry into repeatable performance baselines, structured comparisons and privacy-safe health results.

The module does not execute inference or select models. It consumes metrics already recorded through `TelemetryRepository`, preserving the separation between the runtime data plane and the observability control plane.

## Benchmark identity

Every baseline is scoped by a `BenchmarkKey` containing:

- `applicationId`;
- `useCaseId`;
- immutable model SHA-256 digest;
- explicit `ModelLoadKind.COLD` or `ModelLoadKind.WARM`.

`ModelLoadKind.UNKNOWN` is rejected when the key is created. Cold and warm measurements remain independent, so a warm generation is never compared with a cold-load baseline.

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

Unavailable source metrics remain `null`; they are not replaced with zero.

## Active baseline and retained history

Each capture has two persistence effects:

1. it replaces the active baseline for the same stable benchmark key;
2. it appends an immutable point to bounded baseline history.

`TelemetryRepository.benchmarkBaselines()` returns one active baseline per key and remains the source used by regression checks.

`TelemetryRepository.benchmarkBaselineHistory(limit)` returns captures in reverse chronological order. History is bounded by `TelemetryRetentionPolicy.maxBenchmarkBaselines`, which defaults to 200 entries. The limit is global across benchmark keys and prevents unbounded telemetry growth.

The in-memory repository maintains the active-key map and history deque separately. Room maintains the existing `benchmark_baselines` table for active values and a separate `benchmark_baseline_history` table for retained captures.

Room schema version 4 introduces the history table through a non-destructive migration from version 3. Existing active baselines are copied into history as the first retained capture; existing run, log, health, resource and active-baseline data remains available.

## Structured comparison evaluator

`BenchmarkComparisonEvaluator` owns the comparison semantics used by both `BenchmarkRegressionHealthCheck` and the developer console. This prevents the UI from reimplementing policy thresholds or sample-window selection.

A `BenchmarkComparison` contains:

- the benchmark key;
- active baseline;
- current post-baseline aggregate when at least one matching sample exists;
- available and required comparison-sample counts;
- whether the window is ready for a policy decision;
- `PASS`, `WARN` or `FAIL` status and privacy-safe detail;
- one `BenchmarkMetricComparison` per policy metric.

Each metric comparison reports:

- metric identity and unit;
- baseline value;
- current value;
- current-to-baseline ratio when calculable;
- configured threshold ratio;
- threshold direction;
- whether the metric regressed.

The compared metrics are:

- median TTFT, with a maximum allowed ratio;
- p95 total latency, with a maximum allowed ratio;
- median decode throughput, with a minimum allowed ratio.

Only completed runs with the same application, use case, model digest and load class are included. Runs must also have completed after the active baseline capture timestamp.

## Readiness and partial previews

Comparison status is:

- `WARN` when no active baseline exists;
- `WARN` when fewer than the configured minimum post-baseline samples exist;
- `WARN` when no metric is comparable;
- `PASS` when the ready comparison remains within policy;
- `FAIL` when one or more ready metrics exceed policy.

When at least one post-baseline sample exists but the minimum window is not yet complete, the evaluator still returns a current aggregate and metric ratios. These values are explicitly a preview: `comparisonReady` remains false and no metric is presented as an actionable regression or pass.

This distinction lets the console show progress without converting statistically incomplete evidence into a decision.

## Regression health check

`BenchmarkRegressionHealthCheck` implements the common `HealthCheck` contract and delegates to `BenchmarkComparisonEvaluator`.

The health detail names only metric classes such as `median TTFT` or `p95 total latency`. It does not include prompts, generated output, model paths or model digests.

## Stable health-check IDs

The health-check ID contains the benchmark namespace and every key dimension:

```text
benchmark-regression:<applicationId>:<useCaseId>:<modelDigest>:<COLD|WARM>
```

This keeps IDs unique when the same use case is benchmarked with different models or load classifications. Consumers should treat the ID as opaque and should not display it as a user-facing model label.

## Threading and lifecycle

The benchmark engine performs synchronous repository reads and writes. Baseline capture and comparison are intended for explicit developer or administrative workflows, not the generation hot path.

Repository implementations retain ownership of their threading guarantees. The Room adapter serializes active-baseline and history writes in one DAO transaction and applies history retention in that same operation.

## Privacy

The engine uses only structured telemetry already allowed by the observability contracts:

- identifiers required to scope the benchmark;
- timestamps;
- latency values;
- token counts and throughput;
- load classification.

It does not read or persist prompt text, generated text, arbitrary backend exceptions or model file paths.

## Testing

Deterministic tests cover:

- median and nearest-rank p95 calculations;
- minimum baseline sample enforcement;
- cold/warm isolation;
- active-baseline replacement;
- bounded reverse-chronological history;
- Room active/history transactional persistence;
- passing comparisons;
- multi-metric regressions;
- ratio and threshold reporting;
- partial non-actionable previews;
- missing baseline and missing comparison windows;
- privacy-safe health details;
- non-destructive Room schema migration behavior.

## Limitations

Host-side and simulated tests validate calculation, persistence and orchestration behavior. They do not establish representative Android-device performance.

Production baselines still require repeated measurements on physical `arm64-v8a` devices with supported external GGUF models. Device architecture, memory pressure, thermal throttling and OEM scheduling can materially change the observed metrics.
# Harness benchmark composition

The connected phone-test application reuses the existing benchmark-engine contracts rather than introducing a UI-specific statistics implementation.

## Ownership

`HarnessBenchmarkSource` is application-owned and uses the same process-scoped `TelemetryRepository` as runtime generation telemetry, Health and Resources. It does not own a runtime, model store or separate run registry.

## Baseline keys

Every baseline remains isolated by:

- application ID;
- use-case ID;
- model digest;
- explicit `COLD` or `WARM` model-load classification.

Runs with `UNKNOWN` load classification are not eligible.

## Capture semantics

Baseline capture is explicit. Navigation and diagnostics refresh are observational and never create or replace a baseline.

The source discovers eligible keys from completed matching runs and delegates statistics and minimum-sample policy to `BenchmarkBaselineRecorder`. Capture runs on the diagnostics executor and is mutually exclusive with Health, Resource capture and active inference operations.

## Regression semantics

Each active baseline is evaluated through `BenchmarkRegressionHealthCheck`. Only completed post-baseline runs with the exact same benchmark key are considered.

The UI presents:

- baseline sample count;
- median time to first token;
- p95 total latency;
- median decode throughput;
- comparison readiness or regression status;
- the privacy-safe assessment detail produced by the benchmark engine.

A warning means that a baseline is absent, post-baseline samples are insufficient or comparable metrics are unavailable. It is not presented as pass or fail evidence.

## Privacy

Benchmark telemetry contains identifiers, timestamps, load classification, sample counts and aggregate performance metrics. The connected UI omits model file names and full model digests. Prompts and generated output are not included in benchmark baselines or regression details.

## Current limitation

The telemetry contract on this implementation branch exposes one active baseline per benchmark key. Retained multi-capture history and charts require the extended history contract and remain a follow-up. In-memory baselines are also lost on Android process death.

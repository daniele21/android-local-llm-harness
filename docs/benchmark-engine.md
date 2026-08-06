# Benchmark baselines, retained history and regression checks

Status: active
Document type: feature-specification
Owner: observability/benchmark-engine
Canonical scope: observability.benchmarks
Read when: changing benchmark baselines, retained history, regression evaluation or benchmark orchestration
Last reviewed: 2026-08-06

## Purpose

`observability/benchmark-engine` turns completed generation telemetry into repeatable performance baselines, retained capture history and privacy-safe regression health results.

The engine does not execute inference or select models. It consumes metrics already recorded through `TelemetryRepository`, preserving the separation between runtime execution and observability control.

## Benchmark identity

Every baseline is scoped by a `BenchmarkKey` containing:

- `applicationId`;
- `useCaseId`;
- immutable model SHA-256 digest;
- explicit `ModelLoadKind.COLD` or `ModelLoadKind.WARM`.

`ModelLoadKind.UNKNOWN` is rejected. Cold and warm measurements are intentionally independent.

## Capturing a baseline

`BenchmarkBaselineRecorder` reads recent completed runs matching one key, applies the configured window and persists a capture when the minimum sample count is available.

The capture records:

- median time to first token;
- nearest-rank p95 time to first token;
- median total latency;
- nearest-rank p95 total latency;
- median decode throughput;
- sample count and capture timestamp.

Unavailable source metrics remain `null`; they are never converted to zero.

## Active baseline and retained history

The repository exposes two distinct views:

- `benchmarkBaselines()` returns one active regression anchor for each benchmark key;
- `benchmarkBaselineHistory(limit)` returns immutable captures in newest-first order.

Capturing a new baseline:

1. appends an immutable retained capture;
2. replaces the active baseline for that exact key;
3. leaves older retained captures unchanged;
4. applies `TelemetryRetentionPolicy.maxBenchmarkBaselines` to retained history.

Viewing or filtering history never changes the active regression anchor.

The in-memory and Room implementations provide equivalent behavior. Room schema version 4 introduced `benchmark_baseline_history`; migration 3→4 copied each existing active baseline into retained history without deleting or changing the active row.

## Regression health check

`BenchmarkRegressionHealthCheck` implements the shared `HealthCheck` contract. It loads the active baseline for one key and compares only completed runs whose completion timestamp is later than the baseline capture timestamp.

The default policy reports:

- `WARN` when no active baseline exists;
- `WARN` when there are not enough matching post-baseline samples;
- `WARN` when no metric is comparable;
- `PASS` when all comparable metrics remain within policy;
- `FAIL` when one or more metrics exceed regression policy.

Supported comparisons include:

- current median TTFT divided by baseline median TTFT;
- current p95 total latency divided by baseline p95 total latency;
- current median decode throughput divided by baseline median decode throughput.

The health detail names metric classes only. It does not include prompts, generated output, model paths or full model digests.

## Stable health-check IDs

The check ID includes every key dimension:

```text
benchmark-regression:<applicationId>:<useCaseId>:<modelDigest>:<COLD|WARM>
```

Consumers treat the ID as opaque and must not display it as a model label.

## Connected presentation

The connected Benchmarks surface separates:

- active baseline and current regression readiness;
- post-baseline matching sample count;
- regression result and comparable metrics;
- retained historical captures.

Baseline capture is always explicit. Refresh, navigation and history browsing do not capture or replace a baseline.

A capture action is enabled only when the selected key has enough matching completed runs. Bulk capture must skip keys that are not ready and must not overwrite unrelated active baselines.

## Threading and lifecycle

The engine performs synchronous repository reads and writes and is intended for explicit developer or administrative actions outside the generation hot path.

The connected application runs capture and evaluation work through its diagnostics executor/effect boundary. UI composition, tab selection and ordinary refresh remain observational.

## Privacy

The engine uses only privacy-safe telemetry:

- application and use-case identifiers;
- model digest required for benchmark identity;
- timestamps;
- latency values;
- token counts and throughput;
- load classification.

It does not read or persist prompt text, generated output, arbitrary backend exceptions or model file paths. Connected UI may shorten digest presentation while retaining the exact digest inside the benchmark key.

## Testing

Deterministic coverage includes:

- median and nearest-rank p95 calculations;
- minimum sample enforcement;
- cold/warm and model-key isolation;
- active baseline replacement;
- immutable retained history ordering and retention;
- migration 3→4 seeding existing active baselines into history;
- comparison using only post-baseline samples;
- passing, warning and multi-metric regression results;
- missing metrics and no-comparable-metric behavior;
- privacy-safe health details;
- in-memory and Room parity;
- connected readiness and capture presentation.

## Evidence boundary

Host and simulated tests validate calculation, persistence and orchestration. They do not establish representative Android performance.

Production baselines require repeated measurements on representative physical `arm64-v8a` devices with the exact application/use-case/model/load key. Device architecture, memory pressure, thermal throttling and OEM scheduling must be recorded with the benchmark evidence.

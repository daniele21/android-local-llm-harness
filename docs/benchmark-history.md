# Retained benchmark history

The telemetry repository exposes two separate views:

- `benchmarkBaselines()` returns one active baseline for each application, use case, model digest and cold/warm load key. Regression checks use only this view.
- `benchmarkBaselineHistory(limit)` returns immutable captures in newest-first order. Replacing the active baseline never rewrites older captures.

Both in-memory and Room stores enforce `TelemetryRetentionPolicy.maxBenchmarkBaselines`. Room schema version 4 adds `benchmark_baseline_history`; migration 3→4 copies every existing active baseline into history without deleting or changing the active row.

The phone-test Benchmarks screen presents active regression results separately from retained captures. Browsing history never changes the active regression anchor.

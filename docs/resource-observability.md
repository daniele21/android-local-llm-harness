# Resource observability and model-load classification

Phase 2 exposes Android process-resource snapshots and explicit cold-versus-warm model-load classification without coupling the runtime to Android system services.

## Ownership

The stable DTOs and provider contract live in `observability/contracts`. Android API calls live in `observability/android-resource-probe`. Persistence remains behind `TelemetryRepository`, with bounded implementations in `in-memory-store` and `room-store`.

`core/runtime-core` owns the model lifecycle and is therefore the only layer that can classify a session as cold or warm correctly. It does not depend on the Android resource-probe implementation.

## Resource snapshots

`ResourceSnapshot` may contain:

- process proportional set size (PSS);
- allocated native heap;
- used Java heap;
- currently available device memory;
- the Android low-memory flag;
- the current platform thermal status.

Measurements that are unavailable or rejected by the platform are represented as `null`. Thermal status is `UNKNOWN` below Android 10 or when the platform value cannot be read or mapped. The harness never invents a zero measurement to stand in for missing data.

`AndroidResourceSnapshotProvider` uses public Android APIs and requires no additional permission. `ResourceSnapshotRecorder` captures one snapshot and writes it through `TelemetryRepository`.

```kotlin
val provider = AndroidResourceSnapshotProvider(context)
val recorder = ResourceSnapshotRecorder(provider, telemetryRepository)

val beforeGeneration = recorder.capture()
// Run a controlled workload.
val afterGeneration = recorder.capture()
```

Capture is explicit and caller-driven. The module does not create a hidden timer, background service or global executor. Applications may capture snapshots around lifecycle milestones, health suites or controlled benchmark runs on their own worker or control-plane executor.

## Persistence and retention

Room schema version 2 adds:

- `generation_runs.model_load_kind`, defaulting to `UNKNOWN` for existing rows;
- the `resource_snapshots` table and timestamp index.

The migration from schema 1 to 2 is explicit and non-destructive. Resource writes use the same serialized asynchronous executor as run and log telemetry. Queries are ordered barriers after earlier writes.

`TelemetryRetentionPolicy.maxResourceSnapshots` bounds retained samples independently from runs and logs. Dashboard queries include the retained resource snapshots.

## Cold and warm classification

`ModelLoadKind` has three values:

- `COLD`: session creation caused the resolved model/profile to be loaded;
- `WARM`: session creation reused the already loaded compatible model/profile;
- `UNKNOWN`: historical or external data cannot prove either state.

Classification is based on the actual runtime model lifecycle, not on a latency threshold. `modelLoadMs` is recorded only for a `COLD` session. A `WARM` run therefore has `modelLoadKind = WARM` and `modelLoadMs = null`.

This corrects the previous behavior where the load duration stored on the loaded model handle could be repeated on later warm requests.

A session retains its load classification for requests created through that session. Closing the session does not automatically unload an idle model; a later compatible session may still be warm. After an explicit unload, memory-pressure release, model switch or runtime restart, the next compatible session is cold.

## Privacy

Resource snapshots contain aggregate process/device measurements only. They contain no prompt, generated output, model bytes, model path, device serial, application document content or arbitrary exception message.

Generation telemetry continues to use stable application, use-case, request and model identifiers. Diagnostic export and cross-application transport remain separate boundaries with their own redaction policy.

## Threading and cost

Android resource collection is synchronous and should not run on the Android main thread when used repeatedly or around performance-sensitive workloads. PSS collection may be more expensive than reading heap counters, so benchmark and health callers should use an intentional cadence rather than sampling continuously.

Room persistence remains asynchronous and non-fatal to inference. A telemetry write failure does not fail, cancel or delay generation.

## Testing and evidence

Repository tests cover:

- every Android thermal-status mapping and unavailable-value fallback;
- snapshot recording and retention;
- Room entity mapping and schema-backed queries;
- first-session cold classification;
- compatible loaded-model reuse as warm;
- exclusion of model-load duration from warm runs;
- persistence of the classification in generation telemetry.

Host and simulated tests prove the contracts and lifecycle logic, not OEM memory reporting, real thermal throttling or PSS behavior under a real GGUF workload. Physical-device evidence remains mandatory before publishing memory, latency, throughput or thermal baselines.

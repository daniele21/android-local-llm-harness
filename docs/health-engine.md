# Health engine

Status: active
Document type: feature-specification
Owner: observability/health-engine
Canonical scope: observability.health
Read when: changing health checks, findings, sanity suites or integrity evaluation
Last reviewed: 2026-08-06

`observability/health-engine` is the Phase 2 control-plane boundary for independently testable health checks. It depends on stable runtime, observability and model-store contracts and does not depend on Android UI, transport implementations or `llama.cpp` internals.

## Responsibilities

The module:

- registers health checks by stable ID;
- runs all checks or a selected subset;
- measures check duration with an injectable monotonic clock;
- aggregates the suite using the worst result (`FAIL`, `WARN`, `NOT_RUN`, `PASS`);
- persists every result through `TelemetryRepository.saveHealth`;
- converts unexpected exceptions into a privacy-safe failure;
- exposes installed-model integrity through `ModelStore.verify`;
- executes configurable end-to-end generation sanity checks through `LocalLlmClient`;
- supports positive, negative, structural and non-empty output assertions;
- interprets neutral cache-health probes without coupling the runtime to the health engine.

It does not own Room, console rendering, cross-application transport, benchmark history, memory or thermal probes.

## Core API

The same `ModelIntegrityCache` instance must be supplied to the runtime and its health probe. This makes the probe observe the cache that inference actually uses rather than an empty diagnostic copy.

```kotlin
val integrityCache = ModelIntegrityCache()
val runtime = RuntimeOrchestrator(
    registry = registry,
    modelStore = modelStore,
    backend = backend,
    scheduler = scheduler,
    telemetry = telemetry,
    integrityCache = integrityCache,
)

val cacheHealthCheck = CacheHealthCheck(
    ModelIntegrityCacheHealthProbe(
        cache = integrityCache,
        modelStore = modelStore,
    ),
)

val sanityCheck = GenerationSanityHealthCheck(
    client = runtime,
    spec = GenerationSanitySpec(
        applicationId = ApplicationId("com.example.app"),
        useCaseId = UseCaseId("runtime-sanity"),
        prompt = "Reply with LOCAL_LLM_OK and nothing else.",
        expectedOutput = "LOCAL_LLM_OK",
        outputMatch = SanityOutputMatch.EXACT,
        maxOutputTokens = 8,
        temperature = 0f,
        seed = 0L,
        timeoutMs = 30_000L,
    ),
)

val engine = HealthEngine(
    checks = listOf(
        ModelIntegrityHealthCheck(modelStore),
        cacheHealthCheck,
        sanityCheck,
    ),
    telemetryRepository = telemetryRepository,
)

val fullReport = engine.runAll()
val selectedReport = engine.run(listOf(cacheHealthCheck.id, sanityCheck.id))
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

## Generation sanity check

`GenerationSanityHealthCheck` validates the functional runtime lifecycle behind the public `LocalLlmClient` contract. Each check is explicitly bound to one application and use case through `GenerationSanitySpec`.

The lifecycle is:

```text
prepare
create session
generate deterministic request
wait for Completed or Failed
assert configured output rule
close session
```

The check supports:

- `NON_EMPTY` to require any non-blank generated output;
- `EXACT` for an exact expected value;
- `CONTAINS` for a required marker;
- `NOT_CONTAINS` for a forbidden marker;
- `MATCHES_REGEX` for a complete regular-expression match;
- case-sensitive or case-insensitive text and regex comparison;
- explicit output-token limit;
- deterministic temperature and seed, defaulting to `0`;
- a bounded timeout;
- cooperative cancellation when the timeout expires.

`expectedOutput` may be empty only for `NON_EMPTY`. Regex patterns are validated when `GenerationSanitySpec` is created, so an invalid health configuration fails before the runtime lifecycle starts.

The stable check ID is:

```text
generation-sanity:<applicationId>:<useCaseId>
```

Outcomes:

- the model cannot be prepared: `FAIL`;
- the session cannot be created: `FAIL`;
- generation returns a typed failure: `FAIL` with the public error code only;
- generation times out: the handle is cancelled and the check returns `FAIL`;
- generation completes but does not satisfy the configured assertion: `FAIL`;
- generation and cleanup both succeed with a satisfied assertion: `PASS`;
- session cleanup fails: `FAIL`, even when the assertion passed.

This check is backend-agnostic. It does not depend on `RuntimeOrchestrator`, scheduler implementation details, JNI handles or `llama.cpp` types. It can therefore exercise the embedded runtime today and a future Binder-backed `LocalLlmClient` without changing the health contract.

## Model-integrity cache health

The cache-health boundary is split deliberately:

- `CacheHealthProbe` and `CacheHealthSnapshot` live in `observability/contracts`;
- `ModelIntegrityCacheHealthProbe` lives beside `ModelIntegrityCache` in `runtime-core`;
- `CacheHealthCheck` lives in `observability/health-engine` and maps the neutral snapshot to a health result.

This dependency direction keeps the runtime independent from the health-engine implementation.

`ModelIntegrityCacheHealthProbe` compares every cached verification stamp with the current `ModelStore.snapshot()` and the artifact's current file stamp.

An entry is:

- **healthy** when its digest is still installed and its path, file size and last-modified timestamp match the cached stamp;
- **stale** when the model is still installed but its current file stamp differs;
- **orphaned** when the digest is no longer present in the model store.

The snapshot is observational and non-mutating. Running the check does not clear, invalidate or re-verify entries. Cache repair remains owned by the runtime lifecycle and the next explicit verification.

The stable check ID is:

```text
cache-health:model-integrity
```

Outcomes:

- an empty or fully consistent cache: `PASS`;
- one or more stale or orphaned entries: `FAIL`.

Only aggregate counts are persisted. Paths, digests, model bytes and file timestamps are not included in the health detail.

A verified atomic import may seed the cache without a second hash while no prior cache stamp exists. Once cached, a changed file stamp can no longer reuse the import's `verified` flag: the next runtime verification calls `ModelStore.verify()` again. This prevents a modified artifact from remaining trusted indefinitely.

## Failure isolation and privacy

A check may fail independently without breaking inference or another health check. Unexpected exceptions are converted to:

```text
status = FAIL
detail = Health check failed unexpectedly
```

The original exception message is deliberately excluded because it may contain private paths, prompts or implementation details.

Generation sanity results never persist the configured prompt, generated output, expected marker, regex or backend error message. A typed generation failure exposes only its stable public error code. Assertion failures report only that the configured rule was not satisfied.

Cache-health results contain aggregate counts only. The internal cache stamp remains private to `runtime-core`.

Persistence uses the existing `TelemetryRepository` boundary. The Room-backed implementation therefore makes health results available to the owning embedded application without coupling this module to Room.

## Threading

`HealthEngine` executes checks synchronously on the caller's thread. `GenerationSanityHealthCheck` blocks that caller until a terminal event or the configured timeout. Callers must run health suites on a dedicated worker or control-plane executor, never the Android main thread or the inference callback thread.

The cache probe copies the concurrent cache entries before evaluating them and does not acquire an inference-wide lock. Its result is a bounded point-in-time observation and may become outdated immediately if the runtime changes the cache concurrently.

The health engine does not introduce a hidden global executor. Generation itself remains owned by the supplied `LocalLlmClient` and its scheduler.

## Testing

The module and runtime include deterministic tests for:

- suite aggregation;
- duration measurement;
- result persistence;
- unknown check IDs;
- privacy-safe exception handling;
- no-model, valid-model and invalid-model integrity outcomes;
- absence of private model paths from persisted details;
- successful generation and expected-output matching;
- non-empty output assertions;
- required and forbidden output markers;
- case-insensitive regular-expression assertions;
- invalid regex rejection before generation;
- deterministic generation overrides;
- output mismatch without generated-text or assertion-value disclosure;
- typed runtime failures without backend-message disclosure;
- timeout cancellation;
- preparation failure before session creation;
- mandatory session cleanup and cleanup failure;
- empty and fully consistent cache snapshots;
- stale file-stamp detection;
- orphaned entry detection after model-store removal;
- aggregate privacy-safe cache-health summaries;
- re-hashing a previously verified artifact after its file stamp changes.

The aggregate repository gate runs the tests, Android Lint, Android artifacts and native packaging verification.

## Current limitations

The generation sanity implementation is executable against a real `LocalLlmClient`, but repository CI uses deterministic contract fakes and does not prove behavior with a physical Android device and a real GGUF. That evidence remains part of the separate production-readiness gate.

The cache-health slice covers the runtime's model-integrity verification cache. No other runtime cache with an independent health contract is currently implemented.

The health engine does not yet provide:

- periodic scheduling;
- console controls or visualization;
- cross-application access;
- health contracts for future tokenizer, prompt, KV or downloaded-model caches.

Resource snapshots, cold-versus-warm classification and benchmark regression checks are implemented in separate Phase 2 modules; they are not owned by the health engine itself.

The separate console application still requires the planned signature-protected diagnostics bridge to access another application's private control-plane data.

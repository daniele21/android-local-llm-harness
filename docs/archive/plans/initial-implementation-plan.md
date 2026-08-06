# Android Local LLM Harness — Implementation Plan

Status: historical
Document type: historical-plan
Owner: repository
Last reviewed: 2026-08-06

This is the original phase-oriented implementation plan. It is preserved for historical context; current target behavior is owned by [`../../implementation-plan.md`](../../implementation-plan.md) and its focused specifications.

## 1. Purpose

This document defines the implementation plan for the Android Local LLM Harness.

The project provides a reusable Android runtime for executing explicit local LLM configurations, initially embedded inside each application and later available through a shared Android service. The first supported model format is GGUF and the first native backend is `llama.cpp`.

The harness is intended for:

- fully native Android applications;
- Capacitor-based Android applications through a native plugin;
- multiple applications that may use different models, quantizations and runtime profiles;
- developers who need local observability, health checks, sanity tests, benchmarks and cache inspection;
- a future centralized runtime that can deduplicate model files and coordinate memory across applications.

The plan is implementation-oriented. Each phase has explicit outputs and acceptance criteria. A phase is complete only when its functional, diagnostic and test requirements are satisfied.

---

## 2. Product principles

### 2.1 Explicit model binding

The runtime must not silently choose or substitute models.

Every inference is resolved through:

```text
applicationId + useCaseId
          -> AppModelBinding
          -> UseCaseProfile
          -> GgufModelProfile
          -> exact GGUF artifact digest
          -> exact llama.cpp load configuration
```

A different application may use a different model. The same application may also use multiple models for different use cases.

Fallbacks are allowed only when explicitly declared, ordered and visible in diagnostics.

### 2.2 GGUF as a first-class format

The initial implementation must support:

- GGUF metadata inspection;
- immutable artifact identity through SHA-256;
- quantization and architecture reporting;
- chat template inspection and explicit overrides;
- model import from file, URI and download source;
- memory-mapped loading when supported;
- model and context lifecycle metrics.

### 2.3 Embedded first, shared later

The initial runtime executes in the application process.

The embedded implementation must not expose assumptions that prevent a later Binder-based deployment. Public contracts therefore use stable identifiers and serializable data structures rather than native pointers or backend-specific objects.

### 2.4 Observability is part of the runtime

Every important runtime operation must produce structured events and metrics.

The Local LLM Console must eventually expose:

- runtime state;
- app and use-case bindings;
- installed models;
- active model and context state;
- generation timelines;
- structured logs;
- latency and throughput metrics;
- memory and thermal information;
- cache entries, hit rates and invalidation actions;
- health and sanity suites;
- benchmark history;
- privacy-safe diagnostic exports.

### 2.5 Privacy by default

Prompt and output persistence is disabled by default.

Normal telemetry stores identifiers, sizes, timings, token counts, statuses and error codes. Content collection requires an explicit diagnostic mode with a visible state and bounded retention.

### 2.6 Performance decisions require measurements

Model retention, context reuse, prefix caching, thread counts, batch sizes and GPU offloading must be selected using device benchmarks rather than assumptions.

---

## 3. Scope

### 3.1 Initial scope

The first production-capable embedded runtime includes:

- Android `arm64-v8a`;
- GGUF files;
- `llama.cpp` CPU backend;
- explicit model profiles;
- one loaded model by default;
- one active decode by default;
- streaming text generation;
- cancellation;
- deterministic and non-deterministic generation parameters;
- content-addressed model storage;
- model/context caching policies;
- structured local telemetry;
- developer console;
- native Android SDK;
- Capacitor plugin;
- sanity, health and benchmark suites.

### 3.2 Deferred scope

The following are deferred until the CPU embedded path is stable:

- Android GPU/Vulkan offloading as a production default;
- multiple simultaneous decodes;
- speculative decoding;
- multimodal models;
- embeddings and rerankers;
- LoRA hot swapping;
- remote inference fallback;
- cross-device model synchronization;
- third-party applications accessing the shared runtime;
- automatic model selection based on quality scoring.

These features may be added later without changing the core model-binding contract.

---

## 4. Target repository structure

```text
local-llm-android/
├── build-logic/
├── core/
│   ├── contracts/
│   ├── runtime-core/
│   ├── runtime-state-machine/
│   ├── scheduler/
│   ├── session-manager/
│   ├── device-profile/
│   └── app-registry/
├── models/
│   ├── model-profile/
│   ├── model-catalog/
│   ├── model-store/
│   ├── model-import/
│   ├── model-download/
│   └── gguf-inspector/
├── backends/
│   ├── backend-api/
│   ├── llama-cpp/
│   └── fake/
├── caching/
│   ├── contracts/
│   ├── model-memory-cache/
│   ├── context-cache/
│   ├── snapshot-cache/
│   └── result-cache/
├── observability/
│   ├── contracts/
│   ├── telemetry-collector/
│   ├── telemetry-store/
│   ├── structured-logging/
│   ├── health-engine/
│   ├── benchmark-engine/
│   └── diagnostic-export/
├── transports/
│   ├── api/
│   ├── in-process/
│   ├── diagnostics-bridge/
│   └── binder/
├── integrations/
│   ├── android-native/
│   └── capacitor-plugin/
├── apps/
│   ├── local-llm-console/
│   ├── sample-native/
│   ├── sample-capacitor/
│   └── benchmark-runner/
├── testing/
│   ├── fixtures/
│   ├── sanity/
│   ├── performance/
│   ├── stability/
│   └── fault-injection/
└── third_party/
    └── llama.cpp/
```

Modules should be introduced only when their responsibility is implemented. The repository must avoid empty architectural modules with no concrete behavior.

---

## 5. Runtime lifecycle

The runtime state machine must represent expensive and failure-prone operations explicitly.

```text
UNINITIALIZED
      |
      v
INITIALIZING
      |
      v
IDLE
      |
      +---- prepare ----> RESOLVING_PROFILE
                              |
                              v
                        VERIFYING_MODEL
                              |
                              v
                         LOADING_MODEL
                              |
                              v
                            READY
                              |
                     create/acquire context
                              |
                              v
                         GENERATING
                              |
                              v
                         IDLE_WARM
                              |
                  TTL / memory pressure / switch
                              |
                              v
                          UNLOADING
                              |
                              v
                            IDLE
```

Every transition must:

- be serialized by the runtime orchestrator;
- produce a structured event;
- record duration when relevant;
- expose a typed failure;
- leave the runtime in a recoverable state;
- support diagnostic inspection.

---

# 6. Implementation phases

## Phase 0 — Repository hardening

### Objective

Turn the initial scaffold into a reproducible, enforceable development foundation.

### Tasks

- Add the Gradle wrapper to the repository.
- Pin all build versions in `libs.versions.toml` or an equivalent central catalog.
- Add Android lint configuration.
- Add Kotlin formatting and static analysis.
- Add dependency verification or lock files.
- Add build variants for `debug`, `internal` and `release` where needed.
- Add unit-test and instrumentation-test source sets.
- Add code ownership or contribution rules for native runtime changes.
- Add architecture decision records under `docs/adr/`.
- Add a changelog policy and semantic versioning rules for SDK artifacts.
- Ensure CI builds all modules from a clean checkout.
- Produce build artifacts for the console and libraries in CI.

### Deliverables

- reproducible Gradle build;
- CI validation workflow;
- static analysis configuration;
- initial ADR set;
- versioning and release conventions.

### Acceptance criteria

- `./gradlew check lintDebug` succeeds from a clean checkout;
- the console debug APK is generated in CI;
- no local machine path is required;
- dependencies and toolchain versions are pinned;
- release builds cannot accidentally package `.gguf` files from developer directories.

---

## Phase 1 — Pin and integrate `llama.cpp`

### Objective

Replace the JNI stub with a reproducible native backend capable of inspecting and loading GGUF models.

### Tasks

#### Native source management

- Select and pin a specific `llama.cpp` commit.
- Record the commit in a machine-readable version file.
- Decide between Git submodule, subtree or controlled fetch script.
- Document the update procedure.
- Store local patches separately and keep them minimal.

#### Android native build

- Build only `arm64-v8a` for release.
- Add `x86_64` only for emulator development if useful.
- Enable appropriate ARM optimizations without producing binaries incompatible with older supported devices.
- Separate portable kernels from optional device-specific kernels.
- Expose the `llama.cpp` commit, build flags, NDK version and supported GGUF version at runtime.
- Configure native symbol generation for internal builds.

#### JNI API

Implement a coarse-grained JNI boundary for:

- runtime initialization;
- GGUF metadata inspection;
- model loading;
- model unloading;
- context creation;
- context reset;
- tokenization;
- detokenization;
- chat template application;
- generation start;
- aggregated streaming callbacks;
- cancellation;
- context destruction;
- native memory estimates;
- session state save and restore capability discovery.

Avoid a JNI call per token. The native decode loop should aggregate output before crossing JNI.

#### Native resource ownership

- Represent model and context handles through opaque IDs.
- Maintain native handle registries with explicit ownership.
- Reject use-after-close operations.
- Make close operations idempotent.
- Ensure partial failures release already allocated native resources.

### Deliverables

- linked `llama.cpp` backend;
- runtime/build metadata endpoint;
- GGUF inspector;
- model load/unload smoke test;
- native lifecycle tests.

### Acceptance criteria

- a valid GGUF file can be inspected without running generation;
- a supported model can be loaded and unloaded repeatedly;
- invalid/corrupted GGUF files fail with typed errors rather than native crashes;
- the backend reports its exact source commit and build configuration;
- repeated load/unload smoke tests do not show unbounded memory growth;
- no native handle escapes the backend module.

---

## Phase 2 — Model registry, import and content-addressed storage

### Objective

Implement reliable GGUF artifact management and explicit app/use-case/model resolution.

### Tasks

#### Model artifact identity

- Define `GgufArtifact` with SHA-256, size, format and parsed metadata.
- Use the SHA-256 digest as physical artifact identity.
- Keep logical model IDs separate from physical digests.
- Store model metadata next to the artifact.

#### Artifact store

Use an application-private, non-backed-up directory:

```text
noBackupFilesDir/local-llm/
├── models/sha256/<prefix>/<digest>/model.gguf
├── models/sha256/<prefix>/<digest>/metadata.json
├── staging/
├── profiles/
└── runtime-cache/
```

Implement:

- atomic install from staging;
- streaming SHA-256 verification;
- duplicate detection;
- free-space checks;
- import cancellation;
- interrupted-import cleanup;
- integrity revalidation;
- deletion protection for active models;
- last-access and reference metadata.

#### Import sources

Support:

- internal file path for development;
- Android `content://` URI;
- streamed HTTP download;
- future Play AI pack source through a common interface.

Large models must not be copied into memory.

#### Configuration registry

Implement repositories for:

- `GgufModelProfile`;
- `UseCaseProfile`;
- `AppModelBinding`;
- explicit fallback chains;
- profile schema versioning.

#### Resolution rules

- Resolve exactly one profile for `applicationId + useCaseId`.
- Validate that the referenced artifact digest is present.
- Reject ambiguous bindings.
- Reject incompatible profile/model combinations before loading.
- Emit a diagnostic resolution trace.

### Deliverables

- content-addressed artifact store;
- import APIs;
- profile registry;
- binding resolver;
- integrity scanner.

### Acceptance criteria

- importing the same GGUF twice stores one physical copy;
- import interruption never exposes a partial artifact as ready;
- corrupted models are quarantined or marked invalid;
- a request cannot run without an explicit valid binding;
- profile and binding errors are visible in the console diagnostics model;
- no model path is exposed as the primary public identity.

---

## Phase 3 — Functional embedded inference runtime

### Objective

Provide stable, cancellable and observable local generation inside an Android application process.

### Tasks

#### Runtime orchestrator

- Implement a serialized command processor.
- Own model, context and request lifecycle transitions.
- Prevent concurrent mutation of backend handles.
- Recover to `IDLE` or `READY` after request failures.
- Add shutdown and trim-memory commands.

#### Session manager

- Create stable session IDs.
- Associate sessions with application ID, use case and resolved model profile.
- Support stateless and conversational sessions.
- Reject cross-profile session reuse.
- Close orphaned sessions.
- Separate persisted conversation history from native KV state.

#### Generation

- Follow the focused [`generation configuration and prompting plan`](../../generation-configuration-and-prompting-plan.md)
  for the staged contracts, prompt/template trust boundary, exact token planning, dynamic
  context lifecycle, preset rollout, telemetry migration and validation sequence.
- Build the prompt using the configured chat template policy.
- Apply system prompt and use-case template versions.
- Tokenize and validate context limits before decode.
- Support temperature, top-k, top-p, min-p, repeat penalty, seed and stop sequences.
- Apply maximum output token limits.
- Stream aggregated text deltas.
- Return final metrics and stop reason.
- Detect invalid UTF-8 and repeated output pathologies.

#### Cancellation

- Allow cancellation while queued.
- Allow cooperative cancellation during prefill and decode.
- Ensure cancellation frees temporary buffers.
- Record cancellation latency.
- Distinguish user cancellation from runtime failure.

#### Scheduler

Start with:

```text
one loaded model
one active decode
N logical sessions
```

Implement:

- FIFO within the same priority;
- foreground interactive priority;
- background and maintenance priorities;
- queue limits;
- per-request deadline support;
- queue cancellation;
- queue wait metrics.

#### Android lifecycle

- Connect runtime lifetime to an application-scoped component.
- Handle activity recreation without losing runtime ownership.
- Respond to `ComponentCallbacks2` memory signals.
- Release resources on severe memory pressure.
- Avoid using an always-on foreground service during embedded operation.

### Deliverables

- working `LocalLlmClient` embedded implementation;
- session manager;
- single-decode scheduler;
- streaming generation;
- cancellation;
- memory-pressure integration.

### Acceptance criteria

- a configured use case generates text end to end;
- two sessions can queue requests without corrupting native state;
- cancellation works in queue, prefill and decode stages;
- context overflow returns a typed error before unsafe decode;
- activity recreation does not duplicate the runtime;
- runtime errors do not require application restart for the next valid request;
- every request has a complete diagnostic timeline.

---

## Phase 4 — Caching and performance lifecycle

### Objective

Reduce model preparation and repeated inference cost without compromising correctness or memory stability.

### 4.1 Artifact cache

The artifact store is persistent and content-addressed. It is not managed as a generic disposable cache.

Implement:

- artifact reference tracking;
- last-used timestamps;
- pinning;
- explicit delete;
- orphan cleanup;
- storage usage reporting;
- integrity status.

### 4.2 Operating-system page cache and `mmap`

- Enable `mmap` through the model load profile.
- Avoid a second user-space copy of model tensors.
- Distinguish cold model load, warm file load and hot model-handle reuse in metrics.
- Benchmark `mmap` enabled and disabled on representative devices.
- Keep `mlock` disabled by default and expose it only as an advanced profile option.

### 4.3 Loaded model cache

Implement a model handle cache with:

- one resident model by default;
- configurable memory budget;
- active context reference counts;
- explicit load and unload controls that change only runtime residency, never the installed artifact, binding or selected-model metadata;
- warm idle TTL;
- explicit pin/unpin;
- memory-pressure eviction;
- model-switch cost metrics;
- safe unload serialization.

The warm idle TTL must use an injected monotonic clock and a cancellable scheduler. It starts only
after the last context is released and no generation is active or queued, is cancelled or rearmed
when the model is used again, and unloads only after rechecking the same ownership conditions.
Manual unload follows the same safety checks. A pin may suppress normal TTL eviction but must not
override critical memory-pressure handling. The default TTL must be selected from representative
device measurements rather than assumed from desktop behavior.

Every unload records a structured reason such as manual action, idle TTL, memory pressure, model
switch or runtime shutdown. Reload remains an explicit `prepare` or session-creation operation and
must be observable as cold load, warm file load or hot handle reuse where the backend can
distinguish them.

Eviction must consider:

- active contexts;
- current generation;
- memory pressure;
- estimated resident bytes;
- reload cost;
- last use;
- expected reuse;
- thermal state.

### 4.4 Context and KV cache

- Reuse clean contexts for stateless use cases.
- Preserve context ownership for conversational sessions.
- Track estimated KV cache bytes.
- Enforce context-size-specific keys.
- Prevent reuse across incompatible load profiles.
- Add idle context eviction.

### 4.5 Prefix and session snapshot cache

Introduce only after correctness baselines exist.

Implement capability detection first. When supported:

- key snapshots by model digest, backend build, context configuration, chat template, prompt version, grammar and stable prefix hash;
- validate snapshot compatibility before restore;
- fall back to normal prefill on restore failure;
- track save, restore, hit, miss and invalidation metrics;
- apply bounded disk budgets;
- encrypt snapshots only if they may encode sensitive prompt material and the use case requires persistence.

### 4.6 Deterministic result cache

Result caching is opt-in per use case.

A cache key must include:

- application namespace;
- use-case ID;
- model digest;
- model/load profile version;
- prompt template version;
- normalized input hash;
- generation parameter hash;
- output schema hash;
- seed and deterministic settings.

Do not cache normal creative chat outputs by default.

### Deliverables

- cache contracts and policies;
- model/context retention implementation;
- cache metrics;
- console cache inspector model;
- performance comparison report.

### Acceptance criteria

- hot model reuse removes unnecessary model reloads;
- explicit unload releases the model/context runtime ownership without deleting or mutating the installed GGUF, binding or selected-model metadata;
- manual and TTL unload are rejected or deferred while a context, active generation or queued request still owns the model;
- reuse before TTL expiry cancels or rearms eviction, while the next explicit prepare after eviction reloads the same immutable model identity;
- memory-pressure events can evict inactive model/context resources;
- deterministic tests cover TTL expiry, reuse races, pinning, unload reasons and idempotent cleanup with an injected clock;
- representative-device evidence records PSS before/after unload and the latency cost of the following cold reload without assuming immediate operating-system page-cache reclamation;
- cache keys prevent incompatible state reuse;
- cache invalidation is deterministic and testable;
- cache effectiveness is visible through hit, miss, restore cost and avoided-work metrics;
- disabling every optional cache still produces correct output.

---

## Phase 5 — Telemetry, logs and Local LLM Console

### Objective

Provide a complete local developer control plane for runtime monitoring and diagnostics.

### 5.1 Telemetry model

Persist structured data for:

- applications;
- use cases;
- model artifacts;
- model profiles;
- runtime instances;
- generation runs;
- generation events;
- model lifecycle events;
- memory snapshots;
- cache events;
- health runs;
- benchmark runs;
- structured logs.

Use Room or an equivalent SQLite-backed implementation.

### 5.2 Required generation metrics

Capture:

- queue wait;
- profile resolution;
- model verification;
- model load or reuse;
- context acquisition;
- tokenization;
- input token count;
- prefix restore;
- prefill duration and throughput;
- time to first token;
- decode duration and throughput;
- output token count;
- validation duration;
- total duration;
- stop reason;
- cancellation latency;
- peak memory samples where available;
- initial and final thermal state;
- cache status;
- typed errors.

### 5.3 Structured logging

Every log event should include when available:

- timestamp;
- severity;
- component;
- event type;
- runtime instance ID;
- application ID;
- use-case ID;
- request ID;
- session ID;
- model digest;
- structured attributes;
- redaction level.

Do not use raw Logcat as the only diagnostic store.

### 5.4 Privacy modes

Implement:

```text
METADATA_ONLY       default
REDACTED_CONTENT    explicitly enabled
FULL_LOCAL_DEBUG    explicitly enabled and time-bounded
```

Requirements:

- no prompt/output storage in `METADATA_ONLY`;
- visible warning for content modes;
- configurable retention;
- immediate delete action;
- no network export unless explicitly implemented later;
- diagnostic export must state the active privacy mode.

### 5.5 Console sections

#### Overview

- runtime health;
- current model;
- active sessions;
- queue length;
- memory;
- thermal state;
- recent error;
- TTFT and token throughput summaries;
- cache hit summary.

#### Applications

- application ID and version;
- registered use cases;
- model binding;
- request counts;
- failures;
- latency and throughput distributions;
- cache usage.

#### Models

- GGUF identity and metadata;
- digest and size;
- architecture and quantization;
- chat template;
- integrity state;
- profiles and application references;
- cold/warm load history;
- storage and memory use.

#### Runs

- searchable run list;
- stage timeline;
- metrics;
- cache decisions;
- stop reason;
- errors;
- privacy-safe input/output metadata.

#### Logs

- filtering by component, level, application, use case, model, request and session;
- bounded pagination;
- export of selected events.

#### Cache

- artifact usage;
- loaded model status;
- context state;
- snapshot entries;
- result-cache entries;
- hit/miss/eviction metrics;
- safe clear and invalidate actions.

#### Health

- latest suite status;
- failing checks;
- model integrity;
- generation sanity;
- cache health;
- memory leak indicators;
- recommended remediation.

#### Benchmarks

- benchmark definitions;
- device and build metadata;
- historical runs;
- baseline comparison;
- regression flags.

#### Device

- Android version;
- ABI;
- SoC and supported CPU features;
- memory class and available memory;
- runtime/NDK/`llama.cpp` versions;
- thermal capability;
- storage availability.

### 5.6 Diagnostics access during embedded phase

Each integrating application will expose a diagnostics bridge:

- disabled in normal release builds by default;
- enabled in debug/internal builds;
- protected by signature permission;
- read-only for normal diagnostics;
- explicit authorization for destructive cache actions;
- versioned diagnostic protocol;
- connection and compatibility status shown in the console.

### Deliverables

- Room telemetry database;
- telemetry collector;
- structured log store;
- complete console navigation;
- live runtime dashboard;
- diagnostics bridge;
- redacted diagnostic export.

### Acceptance criteria

- developers can reconstruct every generation stage from the run timeline;
- the console works without prompt/output persistence;
- database retention prevents unbounded growth;
- console queries do not block generation;
- embedded apps can be inspected only when authorized;
- destructive console actions are explicit and logged;
- diagnostic export contains versions, configuration, health and metrics needed to reproduce an issue.

---

## Phase 6 — Health, sanity and benchmark framework

### Objective

Detect model incompatibility, runtime degradation and performance regressions before they reach product applications.

### 6.1 Static model health

Checks:

- file exists;
- expected size;
- SHA-256 integrity;
- valid GGUF header;
- supported architecture;
- recognized quantization;
- tokenizer metadata;
- chat template availability or valid override;
- declared context compatibility;
- storage availability;
- profile schema validity.

### 6.2 Load health

Checks:

- model load;
- context creation;
- tokenization;
- short warm-up;
- context reset;
- context close;
- model unload;
- memory returns within an accepted tolerance.

### 6.3 Use-case sanity suites

Each `UseCaseProfile` may provide fixtures with:

- input;
- deterministic generation settings;
- structural assertions;
- semantic label assertions where stable;
- latency and memory guardrails;
- optional expected failure cases.

Examples:

- JSON validity;
- required fields;
- allowed category values;
- no text outside structured output;
- non-empty summary;
- stop sequence respected;
- output token limit respected.

Avoid brittle exact-text assertions unless the use case is fully deterministic and intentionally version-locked.

### 6.4 Cache health

Checks:

- cache key stability;
- snapshot save/restore;
- incompatible snapshot rejection;
- cache clear;
- cache budget enforcement;
- result cache namespace isolation;
- correct fallback after cache corruption.

### 6.5 Stability tests

Scenarios:

- repeated generation;
- repeated load/unload;
- request cancellation at random stages;
- activity recreation;
- application background/foreground transitions;
- low-memory callback;
- model switch;
- near-context-limit requests;
- maximum output requests;
- malformed model files;
- interrupted model imports;
- forced native errors.

### 6.6 Benchmarks

Benchmark dimensions:

- cold model load;
- warm file load;
- hot model reuse;
- tokenization throughput;
- prefill throughput;
- TTFT;
- decode tokens per second;
- peak PSS;
- native heap;
- Java heap;
- context/KV estimate;
- cancellation latency;
- sustained thermal behavior;
- battery impact where measurable;
- Capacitor bridge overhead.

Record:

- device fingerprint;
- Android version;
- runtime build;
- `llama.cpp` commit;
- model digest;
- model profile;
- use-case profile;
- benchmark definition version.

### 6.7 Regression policy

A benchmark baseline must be explicitly promoted.

Regression rules should support:

- absolute thresholds;
- percentage change thresholds;
- device-specific baselines;
- warning and failure levels;
- manual justification for accepted regressions.

### Deliverables

- health engine;
- sanity suite DSL or configuration format;
- benchmark runner application;
- baseline store;
- console health and benchmark views;
- CI smoke benchmark support where practical.

### Acceptance criteria

- each supported model profile has a static and load health suite;
- each product use case has at least one sanity fixture;
- health failures identify the failing stage and remediation;
- benchmark results are comparable only when configuration identities match;
- regressions are visible in the console and machine-readable reports;
- fault injection verifies recovery from model, cache and native failures.

---

## Phase 7 — Native Android SDK integration

### Objective

Make the embedded harness easy to integrate into fully native Android applications.

### Tasks

- Provide a single application-scoped runtime factory.
- Provide dependency-injection integration examples.
- Expose suspending APIs and `Flow` for runtime state and generation events.
- Offer lifecycle-safe session ownership helpers.
- Support foreground UI and background user-initiated tasks appropriately.
- Provide sample ViewModel and UI integration.
- Expose model preparation progress.
- Provide typed error mapping suitable for product UI.
- Document application manifest and storage requirements.
- Publish internal Maven artifacts from CI.

### Sample application scenarios

- model import;
- prepare model;
- deterministic classification;
- streaming chat;
- cancellation;
- model switch;
- diagnostics enabled/disabled;
- memory-pressure behavior.

### Acceptance criteria

- a new native application can integrate the SDK without referencing `llama.cpp` types;
- runtime survives activity recreation;
- sessions close with their declared owner;
- model preparation and generation progress are observable;
- SDK errors are stable and documented;
- sample application exercises every public API.

---

## Phase 8 — Capacitor plugin integration

### Objective

Expose the same embedded runtime to Capacitor applications without running inference inside the WebView.

### Architecture

```text
TypeScript application
        |
Capacitor Local LLM plugin
        |
Kotlin adapter
        |
LocalLlmClient
        |
InProcessTransport
        |
RuntimeOrchestrator
        |
llama.cpp JNI
```

### Tasks

- Create the plugin package and Android implementation.
- Map TypeScript DTOs to stable Kotlin contracts.
- Expose capabilities, model preparation, sessions, generation, cancellation and close operations.
- Aggregate native token deltas before JavaScript events.
- Add configurable aggregation by token count and elapsed time.
- Avoid Base64 for large files.
- Accept Android content URIs or plugin-managed file references for model import.
- Restore plugin listeners after activity recreation where supported.
- Handle WebView destruction and reject orphaned callbacks.
- Add TypeScript types and generated documentation.
- Add a sample Capacitor application.
- Benchmark native versus Capacitor end-to-end overhead.

### Public plugin surface

```typescript
interface LocalLlmPlugin {
  getCapabilities(): Promise<RuntimeCapabilities>;
  prepareUseCase(options: PrepareUseCaseOptions): Promise<PrepareResult>;
  createSession(options: CreateSessionOptions): Promise<CreateSessionResult>;
  generate(options: GenerateOptions): Promise<{ requestId: string }>;
  cancel(options: { requestId: string }): Promise<void>;
  closeSession(options: { sessionId: string }): Promise<void>;
  getRuntimeSnapshot(): Promise<RuntimeSnapshot>;
}
```

Generation events should include:

- queued;
- started;
- text chunk;
- completed;
- cancelled;
- failed.

### Acceptance criteria

- the model and native engine never run in the WebView;
- streaming remains responsive without one JavaScript event per token;
- cancellation propagates to the native decode loop;
- large model files do not cross the bridge inline;
- plugin and native SDK produce equivalent generation results under deterministic settings;
- bridge overhead is measured and visible in benchmarks.

---

## Phase 9 — Security and production hardening

### Objective

Make the embedded SDK safe for production applications and prepare trust boundaries for the shared runtime.

### Tasks

- Threat-model model import, diagnostics, local storage and future IPC.
- Validate all file/URI inputs.
- Prevent path traversal and unsafe file replacement.
- Verify model integrity before load.
- Bound every queue, cache and telemetry store.
- Remove sensitive data from crashes and logs.
- Add native crash symbol and tombstone collection for internal builds.
- Add release build shrinking and native symbol strategy.
- Define dependency vulnerability review.
- Add SBOM generation if practical.
- Add signature permission for diagnostics bridge.
- Verify calling application identity.
- Add protocol version negotiation.
- Add fuzz or malformed-input tests for GGUF metadata parsing boundaries where feasible.

### Acceptance criteria

- unauthorized applications cannot access diagnostics;
- imported artifacts cannot escape the model store;
- release telemetry contains no prompt or output by default;
- corrupted or hostile files fail before generation;
- storage, queues and logs cannot grow without configured bounds;
- production builds expose no debug-only destructive controls.

---

## Phase 10 — Shared Android runtime

### Objective

Move the same data plane into a central Android host without changing product-level model binding semantics.

### Architecture

```text
Native app A ---------
                      \
Capacitor app B -------- Binder transport --> Local LLM Host
                      /                       |- central model store
Native app C ---------                        |- runtime orchestrator
                                               |- model/context cache
                                               |- global scheduler
                                               |- telemetry and health
                                               `- developer console
```

### Tasks

#### Versioned Binder protocol

- Define AIDL DTOs with protocol version fields.
- Implement capability negotiation.
- Keep payloads small.
- Use file descriptors or content URIs for large files.
- Add connection state and reconnection.
- Handle host process death and `DeadObjectException`.
- Make request IDs idempotent where appropriate.

#### Host security

- Protect the service with signature permission.
- Verify package and signing certificate.
- Maintain an allowlist of authorized client applications.
- Namespace sessions, telemetry and result caches by client.
- Prevent clients from accessing another application's content.

#### Central resource management

- Move artifact ownership to the host.
- Deduplicate identical model digests across applications.
- Implement global resident-memory budgets.
- Schedule requests across applications.
- Add per-client priorities, quotas and cancellation.
- Preserve explicit model bindings for every client/use case.

#### Host lifecycle

- Run as a bound service by default.
- Do not remain permanently alive only to retain a model.
- Promote to a foreground service only for eligible visible user work.
- Persist sufficient state to recover after process death.
- Allow clients to reconnect and recreate sessions.

#### Migration support

- Keep `LocalLlmClient` stable.
- Replace `InProcessTransport` with `BinderTransport`.
- Provide host availability and compatibility checks.
- Support an explicit embedded fallback policy during migration.
- Prevent duplicate model downloads when host mode is active.

### Acceptance criteria

- native and Capacitor clients can use the host through the same high-level API;
- different applications can use different models;
- identical digests are stored once by the host;
- the host serializes model/context operations safely;
- host death produces recoverable client errors;
- unauthorized applications cannot bind;
- telemetry remains attributable and isolated by client application;
- switching between embedded and shared transport does not change use-case configuration semantics.

---

# 7. Cross-cutting engineering workstreams

## 7.1 Error model

Define stable errors by domain:

```text
CONFIGURATION
MODEL_NOT_FOUND
MODEL_INTEGRITY
MODEL_INCOMPATIBLE
MODEL_IMPORT
MODEL_LOAD
CONTEXT_LIMIT
CONTEXT_ALLOCATION
TOKENIZATION
GENERATION
CANCELLED
TIMEOUT
MEMORY_PRESSURE
THERMAL_LIMIT
CACHE_CORRUPTION
NATIVE_RUNTIME
TRANSPORT
PERMISSION
PROTOCOL_INCOMPATIBLE
INTERNAL
```

Each error should contain:

- stable code;
- user-safe message;
- diagnostic detail;
- retryability;
- remediation hint;
- related request/session/model identifiers.

## 7.2 Configuration versioning

Version independently:

- artifact metadata schema;
- model load profile;
- use-case profile;
- app binding;
- health suite;
- benchmark definition;
- diagnostics protocol;
- shared runtime protocol.

Configuration migrations must be explicit and tested.

## 7.3 Backpressure

Bound:

- request queue length;
- generation event buffer;
- telemetry writer queue;
- log buffer;
- diagnostic stream;
- cache write queue.

Slow console or JavaScript consumers must not block native decode.

## 7.4 Threading

Document thread ownership for:

- runtime actor;
- JNI calls;
- native decode threads;
- telemetry writes;
- file hashing and import;
- console queries;
- Capacitor callbacks.

No blocking model or file operation should run on the Android main thread.

## 7.5 Test model strategy

Do not commit large GGUF files.

Use:

- tiny synthetic or permissively distributable fixtures where possible;
- CI-downloaded test models with pinned digest;
- local developer model configuration;
- fake backend for deterministic unit tests;
- instrumentation-only integration tests for real models.

## 7.6 API compatibility

Public SDK changes require:

- changelog entry;
- compatibility review;
- migration notes for breaking changes;
- protocol impact review;
- sample application update.

---

# 8. Testing strategy

## 8.1 Unit tests

Cover:

- profile resolution;
- cache key generation;
- state transitions;
- scheduler ordering;
- cancellation state;
- retention policies;
- error mapping;
- metric calculation;
- privacy redaction;
- configuration migration.

Use the fake backend to make tests deterministic.

## 8.2 Native tests

Cover:

- handle lifecycle;
- invalid-handle rejection;
- GGUF inspection;
- load/unload;
- context create/reset/destroy;
- cancellation;
- malformed inputs;
- native error propagation.

## 8.3 Android instrumentation tests

Cover:

- application-scoped runtime;
- activity recreation;
- content URI import;
- storage behavior;
- memory callbacks;
- diagnostics permission;
- console connection;
- Capacitor activity lifecycle.

## 8.4 End-to-end tests

Cover:

- import -> bind -> prepare -> generate -> inspect run;
- model switch;
- deterministic result cache;
- prefix snapshot restore;
- corrupted cache recovery;
- process restart;
- shared host reconnect when implemented.

## 8.5 Device matrix

Maintain at least three capability classes:

```text
LOW      constrained RAM and older supported ARM64 device
MEDIUM   representative current mid-range Android device
HIGH     recent high-memory Android flagship
```

For each class, record supported model size, recommended context, load profile, TTFT, throughput and memory ceiling.

---

# 9. CI/CD plan

## Pull-request validation

- formatting;
- static analysis;
- unit tests;
- Android lint;
- debug build;
- fake-backend integration tests;
- native compilation;
- API compatibility check;
- documentation link check.

## Main branch validation

- all pull-request checks;
- instrumentation tests where infrastructure is available;
- console APK artifact;
- SDK AAR artifacts;
- test report artifact;
- diagnostic schema validation.

## Native runtime upgrade validation

Any `llama.cpp` change requires:

- source commit update;
- runtime metadata update;
- supported model health suite;
- load/unload stability test;
- baseline benchmark comparison;
- cache/snapshot compatibility decision;
- release note describing behavior changes.

## Release outputs

- versioned Android SDK artifacts;
- versioned Capacitor plugin package;
- console APK for internal/developer use;
- checksums;
- changelog;
- compatibility matrix;
- benchmark summary;
- known limitations.

---

# 10. Risk register

| Risk | Impact | Mitigation |
|---|---|---|
| `llama.cpp` changes frequently | native API breakage and performance variance | pin exact commits, isolate JNI, benchmark every upgrade |
| GGUF model incompatibility | load failures or wrong generation behavior | inspect metadata, explicit compatibility checks, health suites |
| Android device fragmentation | unpredictable memory and throughput | device profiles, capability classes, measured defaults |
| Native memory pressure | application termination | one-model default, bounded contexts, trim-memory handling |
| JNI streaming overhead | poor responsiveness | aggregate native token output before JNI and Capacitor bridges |
| Model files consume large storage | failed downloads and user frustration | free-space checks, resumable import, deduplication, visible storage management |
| Cache corruption or incompatibility | crashes or incorrect output | versioned keys, integrity checks, safe fallback, clear actions |
| Telemetry growth | storage pressure | retention, aggregation, bounded tables, vacuum/cleanup policies |
| Sensitive content in diagnostics | privacy breach | metadata-only default, explicit content modes, redaction and retention |
| Shared host process death | interrupted requests | reconnectable clients, typed errors, session reconstruction |
| Background execution restrictions | interrupted long work | foreground-visible execution only when eligible; no permanent daemon assumption |
| GPU backend variability | device-specific crashes or regressions | CPU-first production path, GPU opt-in and compatibility testing |

---

# 11. Definition of done

A feature is complete only when:

- implementation is merged;
- public contracts are documented;
- errors are typed;
- telemetry is emitted;
- privacy behavior is reviewed;
- unit tests exist;
- integration tests exist where applicable;
- console visibility is added when the feature affects runtime state;
- cache key and invalidation are documented for any new cache;
- performance impact is measured for runtime-critical changes;
- sample applications are updated;
- architecture or ADR documentation is updated;
- CI passes from a clean checkout.

A runtime phase is complete only when its acceptance criteria are demonstrated on a real Android device, not only with the fake backend.

---

# 12. Initial implementation backlog

The following sequence should be used for the next implementation work:

1. Add and verify the Gradle wrapper.
2. Pin a `llama.cpp` source commit and document the update process.
3. Compile the native backend for `arm64-v8a`.
4. Replace the JNI stub with runtime/build metadata calls.
5. Implement GGUF header and metadata inspection.
6. Implement model artifact hashing and atomic import.
7. Implement the profile and app-binding repositories.
8. Implement explicit binding resolution and diagnostic traces.
9. Implement model load/unload through opaque handles.
10. Implement context create/reset/destroy.
11. Implement tokenization and prompt rendering.
12. Implement deterministic non-streaming generation.
13. Add streaming with native-side aggregation.
14. Add cooperative cancellation.
15. Implement the runtime state machine and single-decode scheduler.
16. Add memory-pressure handling and warm model TTL.
17. Add generation timelines and in-memory telemetry.
18. Replace in-memory telemetry with Room.
19. Implement the first complete console run-detail screen.
20. Add static model health and one deterministic sanity suite.
21. Add benchmark runner and baseline identity.
22. Implement native SDK sample application.
23. Implement Capacitor plugin and sample application.
24. Implement diagnostics bridge for embedded apps.
25. Implement context/prefix cache only after baseline correctness is stable.
26. Implement shared Binder transport after embedded APIs are proven.

---

# 13. First production readiness gate

The embedded runtime is considered ready for its first real application only when all of the following are true:

- one target GGUF model and quantization have passed health and stability suites;
- model import and integrity verification are reliable;
- explicit app/use-case binding is enforced;
- generation, streaming and cancellation work end to end;
- the runtime recovers from invalid requests and model failures;
- model and context memory are bounded;
- low-memory behavior is implemented;
- prompt/output logging is disabled by default;
- every generation has a complete diagnostic timeline;
- console views expose models, runs, logs, health and cache state;
- cold load, warm load, TTFT, decode throughput and peak memory have baselines;
- native and Capacitor integrations use the same public runtime contracts;
- CI produces reproducible SDK and console artifacts;
- known device and model limitations are documented.

The shared runtime must not begin by duplicating or bypassing this embedded data plane. It should reuse the same model registry, runtime orchestrator, scheduler, cache policies, telemetry and health framework, replacing only process ownership and transport boundaries.

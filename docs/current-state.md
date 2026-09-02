# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-09-02

This is the operational ledger for integrated state, blockers and immediate work. Capability history belongs in [`roadmap.md`](roadmap.md); milestone detail stays in its focused workstream/specification.

## Integration lines

- `dev` is the canonical base/target for ordinary work.
- `main` is the protected stable/release line.
- New work starts from the latest green `dev` unless explicitly hotfixed.

## Integrated baseline

### Runtime and models

The repository has pinned `llama.cpp`, reproducible Android `arm64-v8a` packaging, GGUF inspection/verified installation, explicit model lifecycle, generation/streaming/cancellation, single-decode scheduling, memory-pressure handling, model-aware context planning, output constraints and versioned presets. Product support remains curated Qwen3.5 dense 0.8B/2B; exact artifact/runtime choice is Harnex-owned. Q35-1..5 are complete; Q35-6 still needs representative-device tuning evidence. See [`qwen35/README.md`](qwen35/README.md).

### Android product and control plane

`apps/local-llm-phone-test` exposes Overview, Playground, Applications, Performance, Models, Diagnostics and Settings over real repository sources. Public identity is **Harnex** — **“Your local AI harness for Android.”** Historical `Harness*`, package/Binder IDs and compatibility filenames remain technical identifiers.

Applications control-plane work is complete through ACUX-80 and CPREC-10..70. Startup reconciles mandatory built-ins before UI/Binder readers, preserves valid custom/default/disabled state and stays off the main thread. CPREC-80/90 and broader phone UX/runtime claims still require representative-device evidence. See [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md).

### Shared runtime and Consumer boundary

SR-0..5 and repository-side SR-6 release-evidence tooling are integrated; representative physical SR-6 evidence remains. CRV repository implementation is complete through its automated candidate gate, while CRV-110 remains the frozen same-signer real-GGUF physical gate. See [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md) and [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md).

Background/process lifecycle hardening is integrated through durable logical jobs, detached execution ownership, explicit cancellation, exact prepared execution identity and started/foreground Host demand. PR #510 supplies the signature-protected emulator fault gate. PR #517 fixed the confirmed warm-retention Host forwarding defect for Consumer setup resolution and is merged on `dev` at `50ca8bd8621bb5019a4539b36548a1500bbf2af9`.

HBG-42 in PR #518 adds privacy-safe durable logical-job metadata and Host-restart reconciliation: persisted non-terminal work from a previous runtime becomes `INTERRUPTED` rather than impossible zombie `RUNNING`, while native handles/sessions and sensitive inference content remain process-local. Implementation head `9377db91389edd20d4f4e6ec0d856c54052172dc` passed repository-owned STRONG run `33684350491`, including repository guards, selected Android validation, native packaging and repository validation. The merge gate is a final exact-head STRONG run including the updated repository ledger.

RedactGuard's LAS candidate has integrated typed setup semantics, cause-specific accessible recovery and ProductViewModel setup ownership. Its LAS parent at `4764251ab2f6fe6ca6ab31a64d24717aad58479e` also contains the corrected Android Home/app-switch lifecycle probe. Binder disconnect/rebind instrumentation already proves same logical-job identity and no implicit cancellation, but the canonical Two-APK workflow still needs to execute that test against the official merged HBG-42 source. Active Host-process-loss instrumentation is being added downstream to require typed `HOST_PROCESS_LOST` recovery after Harnex restart.

### Consumer API, OMBRA and evaluation

CA-0..4 are integrated; RedactGuard remains a pure Consumer SDK client and concrete model/runtime/residency authority stays in Harnex. OMBRA repository work includes document ingestion, deterministic analysis planning/validation, host-owned PII use-case policy, redaction/export, product UI, synthetic corpus v2 and pre-registered quality policy v1. OMB-6B identity approval, OMB-8 measured quality execution and physical two-APK evidence remain open. Canonical state: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md) and [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md).

Model-evaluation contracts/evaluators and core dataset pipeline are integrated through EVAL-D-09; Android import D-10 and runner/persistence/comparison/Performance work continue. See [`model-evaluation/README.md`](model-evaluation/README.md).

## Open blockers

### 1. Background/process lifecycle convergence

The old generic-`INCOMPATIBLE` setup blocker is resolved. RedactGuard now preserves typed setup/runtime identity, Harnex's confirmed concrete setup-resolution forwarding defect is fixed on `dev`, and downstream setup stage/problem/recovery semantics no longer encode missing configuration/model/transient failures as incompatibility.

HBG-42 closes the Host-restart state-reconciliation gap at the Harnex owner: stale non-terminal durable metadata cannot remain `RUNNING` across process restart and no native work is claimed to survive. Remaining automated work is cross-repo lifecycle convergence rather than another speculative setup/model fix:

- execute Binder disconnect/rebind in the canonical Two-APK workflow;
- execute active-job Host process loss against the merged HBG-42 Host and require structured downstream `HOST_PROCESS_LOST`;
- retain ViewModel recreation and Home/app-switch same-job evidence;
- add any missing explicit-cancel Two-APK evidence;
- prove RedactGuard process-loss behavior remains source/privacy aware rather than reconstructing sensitive process-local context;
- integrate durable-job state with the existing Harnex `LOW_MEMORY -> CANCEL_AND_RELEASE_ALL` runtime owner and prove structured cleanup/interruption;
- prove multiple jobs/consumers remain deterministically serialized by the existing single-decode scheduler and bounded Host admission.

Automatic retry after Host process loss remains a separate HBG-43 decision and must never reconstruct sensitive input that the owning process no longer has.

### 2. OMBRA completion

OMB-6B remains review-gated; green automation does not imply visual approval. OMB-8 must execute reviewed Qwen3.5 artifact/configuration identities against policy v1 without lowering thresholds to fit results. Physical/release claims remain separate from CI/emulator evidence.

### 3. Representative Android evidence

CRV-110, CPREC-80/90, SR-6, Q35-6, HBG-64 and remaining phone UX/resource claims require representative physical evidence. Sessions may be combined where practical, but each acceptance gate remains independently recorded; emulator evidence is never promoted into ARM64/native/model/OEM claims.

### 4. Follow-on hardening

Remaining parallel work includes RAM warm-idle/device restoration evidence, Q35-7 lifecycle/memory/thermal validation, model evaluation and the [`LLUP`](workstreams/llama-cpp-v0-3-residency-qualification.md) upgrade stream where ownership does not conflict.

## Immediate next block

1. run final exact-head STRONG validation for HBG-42 including the updated lifecycle/current-state documentation, then merge PR #518 only on exact green evidence;
2. pin RedactGuard's canonical Two-APK workflow to the official HBG-42 merge SHA and execute Home/app-switch, ViewModel recreation, Binder disconnect/rebind and active Host-process-loss on exact RedactGuard/Harnex source identities;
3. close the remaining LAS-08C explicit-cancel, RedactGuard-process-loss, critical-memory-pressure and multiple-job/consumer journeys without duplicating canonical Harnex owners;
4. advance HBG-43 retry semantics only where required input is still safely available, then HBG-50 residency composition and HBG-62/63 final automated evidence;
5. execute remaining ARM64/JNI/GGUF/model-memory/OEM evidence separately as REAL_ENVIRONMENT evidence;
6. continue OMB-6B, OMB-8, evaluation and LLUP independently where ownership does not conflict.

## Source links

- Background lifecycle: [`workstreams/background-process-lifecycle-hardening.md`](workstreams/background-process-lifecycle-hardening.md), [`adr/0016-detached-shared-runtime-jobs.md`](adr/0016-detached-shared-runtime-jobs.md)
- Consumer SDK: [`shared-runtime/consumer-android-sdk.md`](shared-runtime/consumer-android-sdk.md)
- Shared runtime: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md)
- Control-plane reconciliation: [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md)
- Consumer API / OMBRA: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md)
- Model evaluation: [`model-evaluation/README.md`](model-evaluation/README.md)
- Qwen3.5: [`qwen35/README.md`](qwen35/README.md)
- Harnex 0.5: [`releases/harness-0.5.md`](releases/harness-0.5.md)

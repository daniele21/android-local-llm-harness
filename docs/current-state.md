# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-09-03

This is the operational ledger for integrated state, blockers and immediate work. Capability history belongs in [`roadmap.md`](roadmap.md); milestone detail stays in focused workstreams/specifications.

## Integration lines

- `dev` is the canonical base/target for ordinary work.
- `main` is the protected stable/release line.
- New work starts from the latest green `dev` unless explicitly hotfixed.

## Integrated baseline

### Runtime and models

Harnex has pinned `llama.cpp`, reproducible Android `arm64-v8a` packaging, GGUF inspection/verified installation, explicit model lifecycle, generation/streaming/cancellation, single-decode scheduling, memory-pressure handling, model-aware context planning, output constraints and versioned presets. Product support remains curated Qwen3.5 dense 0.8B/2B. Q35-1..5 are complete; Q35-6 still needs representative-device tuning evidence.

### Android product and control plane

`apps/local-llm-phone-test` exposes Overview, Playground, Applications, Performance, Models, Diagnostics and Settings over repository sources. Public identity is **Harnex** — **“Your local AI harness for Android.”**

Applications control-plane work is complete through ACUX-80 and CPREC-10..70. CPREC-80/90 and broader phone UX/runtime claims still require representative-device evidence.

### Shared runtime and Consumer boundary

SR-0..5 and repository-side SR-6 release-evidence tooling are integrated; representative physical SR-6 evidence remains. CRV repository implementation is complete through its automated candidate gate; CRV-110 remains the same-signer real-GGUF physical gate.

Background/process lifecycle hardening is integrated through durable logical jobs, detached execution ownership, explicit cancellation, exact prepared execution identity and started/foreground Host demand. PR #510 supplies deterministic emulator generation fault control. PR #517 fixed the warm-retention setup-resolution forwarding defect.

PR #518 / HBG-42 is merged on `dev` at `fc525a301a208f7f243ddbf87c0d523c39097627`. Persisted non-terminal work from a previous Host runtime is reconciled to `INTERRUPTED`; native handles/sessions and sensitive inference content remain process-local.

RedactGuard's LAS candidate has integrated typed setup semantics, cause-specific accessible recovery, ProductViewModel setup ownership, authoritative Home/app-switch probing, Host-process-loss, RedactGuard-process-loss and explicit-cancel instrumentation. Canonical Two-APK execution still needs to converge those tests on one exact cross-repository identity.

PR #525 is the active Harnex-side LAS-08C pressure slice. It maps Android running-critical trim to the existing `LOW_MEMORY` policy, terminalizes active durable logical jobs as runtime failures before runtime cancellation, preserves explicit `CANCELLED` and Host-loss `INTERRUPTED` meanings, and adds explicit bounded multi-consumer admission evidence. Physical memory-pressure/reclamation claims remain outside emulator evidence.

### Consumer API, OMBRA and evaluation

CA-0..4 are integrated; RedactGuard remains a pure Consumer SDK client and concrete model/runtime/residency authority stays in Harnex. OMBRA still has review/quality/physical evidence work. Model-evaluation contracts/evaluators and core dataset pipeline are integrated through EVAL-D-09; Android import and runner/persistence/comparison/Performance work continue.

## Open blockers

### 1. Background/process lifecycle convergence

The old generic `INCOMPATIBLE` setup blocker is resolved. Remaining automated work is lifecycle convergence:

- run Binder disconnect/rebind in the canonical Two-APK workflow;
- run explicit cancel and prove terminal cleanup;
- run active-job Host process loss and require downstream `HOST_PROCESS_LOST`;
- run RedactGuard process loss and prove no sensitive process-local state is reconstructed;
- run critical trim through the real Host Service callback and require runtime-failure cleanup;
- retain Home/app-switch and ViewModel recreation evidence;
- combine bounded multi-consumer admission with existing single-decode no-overlap evidence;
- complete exact-head automated preflight on the final cross-repository candidate.

Automatic retry after Host process loss remains HBG-43 and must never recreate sensitive input unavailable after process death.

### 2. OMBRA completion

OMB-6B remains review-gated. OMB-8 must execute reviewed Qwen3.5 identities against policy v1 without lowering thresholds to fit results. Physical/release claims remain separate from CI/emulator evidence.

### 3. Representative Android evidence

CRV-110, CPREC-80/90, SR-6, Q35-6, HBG-64 and remaining phone resource claims require representative physical evidence. Emulator evidence is never promoted into ARM64/JNI/GGUF/model-memory/thermal/OEM claims.

### 4. Follow-on hardening

Parallel work includes HBG-43 retry semantics, HBG-50 residency composition, RAM warm-idle/device restoration, Q35-7 lifecycle/memory/thermal validation, model evaluation and LLUP where ownership does not conflict.

## Immediate next block

1. validate and merge PR #525 only on exact-head selector-driven evidence;
2. pin RedactGuard Two-APK to that official Harnex merge identity;
3. execute Binder rebind, explicit cancel, Host process loss, RedactGuard process loss and critical-pressure journeys together with existing Home/ViewModel evidence;
4. reconcile the LAS candidate with current RedactGuard `dev`, then run final automated integration validation;
5. keep ARM64/JNI/GGUF/model-memory/thermal/OEM evidence as separate `REAL_ENVIRONMENT` work;
6. advance HBG-43/HBG-50/HBG-62/63 only after the lifecycle matrix is stable.

## Source links

- Background lifecycle: [`workstreams/background-process-lifecycle-hardening.md`](workstreams/background-process-lifecycle-hardening.md), [`adr/0016-detached-shared-runtime-jobs.md`](adr/0016-detached-shared-runtime-jobs.md)
- Consumer SDK: [`shared-runtime/consumer-android-sdk.md`](shared-runtime/consumer-android-sdk.md)
- Shared runtime: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md)
- Control-plane reconciliation: [`workstreams/control-plane-state-reconciliation.md`](workstreams/control-plane-state-reconciliation.md)
- Consumer API / OMBRA: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md)
- Model evaluation: [`model-evaluation/README.md`](model-evaluation/README.md)
- Qwen3.5: [`qwen35/README.md`](qwen35/README.md)

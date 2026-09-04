# Harness Android UX/UI progress

Status: active
Document type: workstream-state
Owner: apps/local-llm-phone-test
Canonical scope: state.phone-ux
Read when: determining the remaining connected-phone UX/UI migration and evidence work
Last reviewed: 2026-08-26

Canonical target specification: [`harness-ux-ui-implementation-plan.md`](harness-ux-ui-implementation-plan.md)

This tracker records concise workstream status only. Detailed repository state and the immediate implementation block belong in [`current-state.md`](current-state.md).

## Status legend

- `DONE`: implementation and automated acceptance criteria are integrated.
- `PARTIAL`: meaningful connected behavior exists, but implementation or automated coverage remains.
- `DEVICE`: implementation is integrated; representative physical-device evidence remains.
- `PENDING`: implementation has not started.

## Workstreams

| Workstream | Status | Integrated boundary | Remaining gate |
| --- | --- | --- | --- |
| Repository-side product-experience realignment | DONE | Task-first/source-backed Overview, progressive Playground, evidence-map Diagnostics, task-backed Settings, adaptive/accessibility policy, fail-closed Performance decisions and Diagnostics state/effect convergence validated on the reconciled repository composition | — |
| Launcher identity and shared design system | DONE | Reproducible launcher assets, light/dark/system tokens, shared components, contrast and touch-target checks | Screenshot regression expansion |
| Responsive application shell | DEVICE | Navigation Compose, compact bottom navigation, expanded rail, detail-aware top bars and adaptive content policy | Process recreation plus representative responsive/large-font evidence |
| Overview | DEVICE | Evidence-backed selected-model/runtime/resource/latest-run presentation with one state-dependent next action | Representative large-font/TalkBack/device evidence |
| Playground | DEVICE | Real inference, streaming, cancellation, cleanup, ViewModel/UDF, Basic -> Advanced -> Expert disclosure and effective configuration | Physical GGUF and representative accessibility evidence |
| Performance | DEVICE | Run/Datasets/History/Compare journey and evidence-gated decision presentation that refuses unsupported rankings | Connect compatible aggregated decision evidence as evaluation backend matures; physical evaluation evidence |
| Models | DEVICE | Unified inventory, typed effects, download/install/import/select/verify/remove, details and deterministic recovery | Restart/reconciliation UI coverage, RAM actions and physical evidence |
| Applications control plane | DEVICE | Apps primary navigation; source-backed Application -> Assigned use case -> Preset drill-down; Suggested/Custom/default semantics; custom preset creation; revision-safe supported mutations with canonical re-read; medium/expanded master-detail with compact/large-font single-pane fallback | ACUX-90 representative physical two-APK proof that persisted default/effective configuration is honored by a real consumer and invalid/stale paths fail closed |
| Diagnostics container | DEVICE | Evidence overview -> Health/Runs/Resources/Benchmarks/Logs/Validation drill-down; resource render state in ViewModel; generation-guarded async actions | Process recreation and representative TalkBack/device evidence |
| Settings and developer tools | DEVICE | Appearance, Privacy, Storage, About/Build, Developer tools and Physical validation; durable theme preference; non-task brand-palette UI removed | Representative lifecycle/accessibility evidence and future source-backed cleanup controls where required |
| Request timeline and detail navigation | PARTIAL | Typed Settings, request-timeline, model-detail and application/use-case/preset detail routes with opaque arguments | Back-stack restoration and process recreation matrix |
| Durable model-identity state | PARTIAL | Catalog/import/selection/runtime projection and explicit degraded states | Qwen3.5-only migration completion, `lastUsedAt`, restart tests and physical reconciliation evidence |
| RAM residency controls | PENDING | Runtime supports opaque load/unload and safe idle release | Product load/unload actions and monotonic warm-idle TTL |
| Compose state and screenshot tests | PARTIAL | Shell/destination instrumentation, progressive-disclosure/Diagnostics/Performance semantics, pure presentation coverage and Applications adaptive policy/master-detail coverage | Connected execution of full semantics/golden/large-font/landscape/expanded matrix |
| Accessibility | DEVICE | Canonical 48 dp interaction targets, adaptive dense-content stacking, contrast foundations, non-color-only status semantics and Applications large-font adaptive fallback | Representative TalkBack/focus-order and large-font device evidence |
| Physical-device validation | DEVICE | Production contracts and Play/ADB validation paths exist | Representative download/install/inference/cancellation/memory and UX accessibility evidence; ACUX-90 Applications effective two-APK configuration proof |

## Current architecture debt

- Navigation restoration and process recreation are not fully demonstrated.
- Performance intentionally fails closed until compatible aggregated latency/throughput/memory/quality evidence is available to support a ranking.
- Resource and benchmark presentation can become richer only where source data supports it.
- RAM residency remains implicit through prepare/release rather than explicit product controls with a defined warm-idle TTL.
- Complete screenshot, TalkBack, representative large-font/landscape and physical-GGUF evidence remains open.
- Applications control-plane repository implementation is complete through ACUX-80; only representative physical two-APK effective-consumer evidence remains before ACUX end-to-end closeout.

The earlier Diagnostics state/effect debt is closed repo-side: renderable resource history is `HarnessUiState`, asynchronous Health/resource/benchmark operations are generation-guarded by `HarnessViewModel`, and `MainActivity` remains the Android lifecycle/result/effect root rather than a second render-state owner.

## Next UX block

The Applications repository-side implementation is complete. Do not reopen its gateway, navigation, read surfaces, mutation contracts or adaptive layout without a concrete regression or product change. The feature-specific next step is ACUX-90 physical evidence only.

ACUX-90 must demonstrate on representative hardware with two real APKs:

- the authorized consumer is visible with the intended app/use-case identity;
- a selected/created default preset persists across Harness restart;
- the real consumer discovers/activates and uses the expected effective assignment/configuration identity;
- privacy-safe evidence correlates app/use-case/binding/preset revisions;
- stale/invalid configuration fails closed rather than silently falling back or overwriting.

The broader existing evidence block remains independently open:

- exercise representative TalkBack/focus order and large-font/landscape/expanded layouts on physical hardware;
- demonstrate process recreation and back-stack restoration without persisting sensitive prompt/output state;
- execute representative real-GGUF download/install/inference/cancellation/memory flows on the exact build;
- retain Performance's fail-closed decision behavior until compatible aggregated evaluation evidence exists;
- keep RAM warm-idle TTL/load-unload product controls as a separate capability slice with source-backed state and deterministic lifecycle tests.

## Release boundary

Applications is now repository-side implemented and may be described as such. It must not yet be described as physically proven/effective for external consumers until ACUX-90 succeeds on representative hardware with the exact two-APK configuration identity and fail-closed path.

The connected phone product must not claim representative-device UX, thermal/performance or real-GGUF readiness until those exact device gates are recorded. Current Harness 0.5 release gates are maintained in [`releases/harness-0.5.md`](releases/harness-0.5.md).

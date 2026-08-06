# Harness Android UX/UI progress

Status: active
Document type: current-state
Owner: apps/local-llm-phone-test
Last reviewed: 2026-08-06

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
| Launcher identity and shared design system | DONE | Reproducible launcher assets, light/dark/system tokens, shared components, contrast and touch-target checks | Screenshot regression expansion |
| Responsive application shell | PARTIAL | Navigation Compose, compact bottom navigation, expanded rail and detail-aware top bars | Restoration, process recreation and responsive evidence |
| Overview | PARTIAL | Real selected model, runtime and latest Playground state | Move remaining state/effects from Activity; resource/recent-run states |
| Playground | DEVICE | Real inference, streaming, cancellation, cleanup, ViewModel/UDF and effective configuration | Compose semantics polish, responsive/accessibility and physical GGUF evidence |
| Models | DEVICE | Unified inventory, typed effects, download/install/import/select/verify/remove, details and deterministic recovery | Restart/reconciliation UI coverage, RAM actions and physical evidence |
| Diagnostics container | PARTIAL | Runs, Health, Resources, Benchmarks, Logs and Validation sections with real sources | Move state/actions behind ViewModel/effects and complete state matrix |
| Settings and developer tools | PARTIAL | Privacy, Storage, Build, Developer tools and Physical validation details | ViewModel/effect migration, theme persistence and cleanup controls |
| Request timeline and detail navigation | PARTIAL | Typed Settings, request-timeline and model-detail routes with opaque arguments | Back-stack restoration, process recreation and emulator matrix |
| Durable multi-model state | PARTIAL | Catalog/import/selection/runtime projection and explicit degraded states | `lastUsedAt`, restart tests and physical reconciliation evidence |
| RAM residency controls | PENDING | Runtime supports opaque load/unload and safe idle release | Product load/unload actions and monotonic warm-idle TTL |
| Compose state and screenshot tests | PARTIAL | Initial shell/destination instrumentation and pure presentation coverage | Full semantics, golden, large-font, landscape and expanded matrix |
| Accessibility | PARTIAL | Design-system contrast and 48 dp foundations | TalkBack, focus order and complete connected-state evidence |
| Physical-device validation | DEVICE | Production contracts and Play/ADB validation paths exist | Representative download/install/inference/cancellation/memory evidence |

## Current architecture debt

- `MainActivity` still owns Overview, Diagnostics and Settings renderable state or effects.
- Diagnostics controllers still rely on callbacks and executors without a typed ViewModel/effect boundary.
- Navigation restoration and process recreation are not fully demonstrated.
- Resource and benchmark presentation can be richer, but only where source data supports it.
- RAM residency remains implicit through prepare/release rather than explicit product controls.
- Complete accessibility, screenshot and representative physical-device evidence remains open.

## Next UX block

Migrate Diagnostics state and user intents behind immutable ViewModel state and an Activity-scoped effect implementation. Preserve explicit execution semantics: refresh and navigation remain observational, while health, resource, benchmark and validation work starts only from a user action.

Acceptance for that block:

- section, loading, result and error state is reducer-owned;
- callbacks dispatch typed events rather than mutating Compose state;
- executors and repository/native resources remain effect-owned and Activity-scoped;
- leaving a detail route clears or restores bounded state deterministically;
- JVM coverage includes concurrent actions, failure, stale callback and detach behavior;
- focused phone-test Spotless, tests, Lint and Kotlin compilation pass.

## Release boundary

The workstream is not complete until the automated compact/expanded/accessibility matrix and representative physical-device GGUF evidence are recorded. Current Harness 0.5 release gates are maintained in [`releases/harness-0.5.md`](releases/harness-0.5.md).

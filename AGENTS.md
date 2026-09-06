# Harnex — Coding Agent Guide

Harnex is the Android local-AI harness: it owns runtime lifecycle, policy, Binder sharing and inference orchestration. Keep inference local; never add silent cloud fallback or content logging.

## Durable invariants

- Model/runtime/Binder state has one canonical owner; UI and adapters translate, they do not duplicate policy.
- JNI handles, jobs, models, processes and temporary evidence are bounded, cancellable and cleaned on every exit path.
- Public Binder/Consumer changes require direct-consumer compatibility evidence.
- Emulator proof never implies ARM64 JNI/llama.cpp, real GGUF, physical memory/thermal or OEM behavior.
- Build/package identity and immutable successful artifact semantics remain truthful.
- Product UI follows the user task, hierarchy, progressive disclosure, accessibility/adaptive behavior and canonical design tokens/components.

## Ownership

| Change | Owner | Direct consumers / proof |
| --- | --- | --- |
| Public/runtime contracts | `core/contracts`, `core/backend-spi`, `core/runtime-core` | adapters, fakes, owner tests |
| Model/lifecycle truth | `models/model-store`, control-plane stores | runtime/control-plane tests |
| Binder protocol/client | `transports/android-binder-*` | `apps/shared-runtime-client-consumer-fixture`, service host, consumer tests |
| Host/process boundary | `integrations/android-service-host` | two-APK journeys |
| Native execution | `backends/llama-cpp`, `third_party/llama.cpp` | JNI/native/package gates |
| Product UI | `apps/local-llm-console`, phone surfaces, `design/ux-contract.json` | design-system tests + journeys |

Follow applicable scoped `AGENTS.md`. Extend the owner before adding parallel state; inspect material consumers when a shared boundary changes.

## Read by task

| Task | Read now |
| --- | --- |
| Pure docs/copy | affected source/links; `docs/README.md` only if ownership is unclear |
| Behavior/bug/contract | `skills/structured-change/SKILL.md`, `skills/validate-change/SKILL.md`, relevant `.engineering/commands.json` |
| Material product UI | above + `skills/design-product-experience/SKILL.md` and relevant `design/*` |
| Integration/release | `skills/preflight-change/SKILL.md`, commands and affected `.engineering/e2e.json` |
| Missing deterministic remote gate | `skills/remote-preflight/SKILL.md` |
| Persistent multi-session work | `skills/plan-workstream/SKILL.md` + active plan; `docs/current-state.md` carries repository-level integrated/blocker/next truth; finalize with `skills/finalize-workstream/SKILL.md` |

Read architecture/features/ADRs only for concrete questions. Upstream adoption/update guidance applies only to explicit standard migrations.

## Delivery boundaries

- **ITERATION**: owner-local falsification; no exact-head/full-diff/docs/publication ceremony after each edit.
- **INTEGRATION**: coherent outcome ready for `dev`; affected docs, exact candidate/base, required automated gates and affected critical E2E. Material UI/UX integration journeys require `FULL_MEDIA`. Residual physical confirmation is `DEFERRED_TO_RELEASE`.
- **RELEASE**: `FULL` release evidence plus every applicable blocking real-environment confirmation.

Stage and validation depth are independent. Resolve risk dimensions into required gates; unknown executable scope fails safe stronger. Missing local tooling does not make the user the Gradle/native runner. Reuse only provably equivalent trusted evidence. Prefer early convergence; stacked publication is exception-only.

## Context, diagnosis and completion

Use bounded search/output and reuse unchanged reads. `.engineering/documentation-policy.json` owns representative context routes; `python3 scripts/verify_agent_context.py --route bug --format json` reports character-based estimates. Add `--path <affected-path>` or `--workstream <plan>` when useful; routes never authorize omitting relevant source or instructions.

For meaningful work state the observable outcome, owner, preserved invariants and proof in the task/PR. On failure classify before patching. Each failed repair needs a falsifiable hypothesis; after two failed repairs with the same signature, change diagnostic strategy and gather discriminating evidence before a third.

On resume refresh source/base identity and use checkpoint evidence as pointers, not current-source proof. Before integration update affected canonical docs. Transfer durable truth and deferred release obligations before deleting completed plans.

Surface unresolved material ambiguity, privacy/security conflicts, duplicate ownership, unbounded resources, stale integration docs, unavailable required automation or evidence/claim mismatch. Never suppress legitimate tests, hide failed/pending gates or downgrade evidence merely to obtain PASS.

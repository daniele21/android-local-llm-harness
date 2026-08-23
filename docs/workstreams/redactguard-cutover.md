# RedactGuard ownership cutover

Status: active
Document type: implementation-workstream
Owner: shared-runtime / Consumer API
Canonical scope: redactguard.cutover
Read when: changing the RedactGuard/Harness ownership boundary or removing legacy OMBRA code from Harness
Last reviewed: 2026-08-23

## Goal

Complete the cross-repository RedactGuard cutover without weakening the Harness security, runtime or evidence boundaries: external consumers resolve through explicit Host Control Plane activation state, the independent RedactGuard APK proves the boundary on physical Android hardware, and only then the legacy in-repository OMBRA product surface is removed from Harness.

## Non-goals

- moving model, GGUF, llama.cpp, scheduling, residency, telemetry or use-case/preset administration into RedactGuard;
- merging the stale stacked HCP implementation branches directly into current `dev`;
- removing the generic packaged Consumer fixture or Harness-internal Playground/device-validation surfaces;
- claiming physical/release readiness from CI or emulator evidence;
- deleting legacy OMBRA compatibility before the exact two-APK gate is green.

## Invariants

- Harness owns runtime/model execution and Host Control Plane policy.
- RedactGuard consumes only public Consumer SDK/Binder contracts and app-owned product/domain contracts.
- External Consumer execution must not fall back to a phone-global selected model.
- Activation/deactivation owns residency acquisition/release and Binder-death cleanup.
- Package + UID + signer authorization remains fail-closed.
- `document-pii-detection` remains an explicit Host-owned use-case binding.
- Physical evidence must record exact Harness APK, RedactGuard APK, signer, SDK/source revision, device and model/preset identities before legacy cleanup.

## Execution DAG

| Slice | State | Depends on | Owns / writes | Acceptance |
| --- | --- | --- | --- | --- |
| RC-1 Clean Control Plane replay | ACTIVE | — | `core/contracts`, `core/runtime-core`, Binder contract/client, Android service host, phone Host composition, generic Consumer fixture | Replay only the still-required HCP 1.2 discovery/activation/residency/cutover semantics from current `dev`; no global-model fallback for external consumers; focused JVM/wire/lifecycle tests pass. |
| RC-2 Exact-head software gate | BLOCKED | RC-1 | CI/evidence only; Consumer SDK ABI/version metadata when contract changes require it | Repository health, docs, Consumer SDK validation, scoped Android validation and packaging are green on one exact head. |
| RC-3 Cross-repo physical proof | BLOCKED | RC-2 | physical evidence/runbooks only | Independently built same-signer Harness Host + RedactGuard APK pass bind/discovery/activation/generation/cancellation/deactivation/Host-death-recovery plus representative RedactGuard import -> analysis -> review -> export/failure scenarios on real arm64 hardware. |
| RC-4 HCUT-1 legacy removal | BLOCKED | RC-3 | `apps/local-llm-console`, legacy OMBRA auth aliases, duplicated PDF/PII/product code/data/docs, build/settings/CI references | Harness builds/tests with no RedactGuard product source; RedactGuard builds/tests with no Harness source checkout; generic Consumer fixture remains green; Host-owned use-case policy remains. |
| RC-5 Final reconciliation | BLOCKED | RC-4 | durable architecture/current-state/roadmap docs and GitHub issue/PR cleanup | Durable docs describe the final ownership boundary; HCUT-1 closes; superseded HCP PRs are closed only after unique-commit audit; this temporary workstream is deleted. |

## Parallelism

While RC-1 is active, RedactGuard product hardening and unrelated LLRT/evaluation work may continue in parallel when write ownership is disjoint. RC-3 is a serialized physical evidence gate. RC-4 must not start in parallel with RC-3 because the legacy compatibility surface is intentionally retained until the physical proof succeeds.

## Current executable slice

**RC-1** — replay the Consumer Control Plane/cutover semantics from the stale HCP branch family onto the latest green `dev`. Treat PRs #343/#348 only as implementation evidence to audit, not as mergeable integration lines. The previous #343 exact-head failure was a Spotless violation in `HarnessCatalogResolution.kt`; the clean replay must satisfy the current repository formatter and 0.5 governance baseline rather than inherit that stack.

## Durable destinations

On completion, retain only current behavior/decisions in the shared-runtime/Consumer API architecture and roadmap, repository current-state, public ABI/version evidence and the physical/release evidence owner. Delete this workstream after RC-5.
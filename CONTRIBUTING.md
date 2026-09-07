# Contributing

## Development prerequisites

- JDK 17
- Android SDK API 36
- Android Build Tools 36.0.0
- Android NDK 28.2.13676358

Use the committed Gradle wrapper for every local and CI build.

## Product-change discipline

Product impact is independent from engineering effort. Before substantial work that changes a supported Harnex capability, consumer workflow/boundary, trust model, target user or product promise, read `.engineering/product.json` + `docs/product.md` and use `skills/shape-product-change/SKILL.md`.

- `PRODUCT_NONE` — implementation-only work; continue directly with normal engineering.
- `PRODUCT_LOCAL` — settled local behavior; state affected consumer, desired outcome and acceptance only.
- `PRODUCT_FEATURE` — establish consumer/problem/outcome, material value/usability/feasibility/viability risks/assumptions, non-goals, quality constraints and success before substantial implementation.
- `PRODUCT_STRATEGIC` — use stronger evidence/alternatives/rollout reasoning for broad product-boundary/value/trust/platform decisions.

Discovery may conclude `BUILD`, `NARROW_SCOPE`, `CHOOSE_ALTERNATIVE` or `DO_NOT_BUILD`. Do not turn this into a parallel PRD system: when persistent coordination is needed, keep the compact product intent in the existing workstream.

Shipping establishes delivery, not product impact. Add a post-release product question only when real use must resolve material uncertainty; do not add telemetry by default.

## Local validation

```bash
./gradlew qualityCheck check lintDebug assembleDebug
```

## Formatting and static analysis

```bash
./gradlew spotlessApply
./gradlew detekt
```

Detekt is intentionally executed through its CLI from the root build. The stable Detekt Gradle plugin is not coupled to Android Gradle Plugin 9.

## Dependency locking

Dependency locking is enabled for all configurations. Whenever a dependency is added or intentionally updated, regenerate and review lock state with:

```bash
./gradlew dependencies --write-locks
```

Do not introduce dynamic versions such as `latest.release`, `+` or unbounded ranges.

## Branches and commits

- Branches: `feature/<scope>`, `fix/<scope>`, `chore/<scope>`
- Commits: imperative and scoped when useful
- Do not commit GGUF, GGML or diagnostic export files

## Architectural rules

- Consumer apps own their product workflow; Harnex owns shared local-AI model/runtime/control-plane policy.
- Keep product model selection explicit in `AppModelBinding`.
- Do not expose native pointers or `llama.cpp` types outside `backends/llama-cpp`.
- Do not persist prompts or outputs in normal telemetry by default.
- Add a cache only with a documented key, invalidation policy, size budget and metrics.
- Any native runtime upgrade requires benchmark and sanity-suite comparison.
- Add an ADR for choices that materially constrain public contracts, native source ownership, storage or process boundaries.
- Translate material product quality promises from `docs/product.md` into measurable invariants/budgets/evidence at their technical owner rather than duplicating implementation detail in the product source.

## Material ambiguity and failure diagnosis

Resolve requirements from canonical product/code/contracts/docs/ADRs/consumers/tests before implementation. If two reasonable interpretations still materially change product intent, behavior, public contracts, persistence, privacy/security, resource/lifecycle semantics, compatibility, acceptance criteria or meaningful UX, surface the conflict instead of silently choosing.

When validation fails, classify it as current-change regression, baseline failure, environment/toolchain issue, flaky behavior, stale-base effect or incorrect assumption/contract before editing production code. Fix the owning invariant; do not weaken legitimate gates or repeat symptom patches without a new falsifiable hypothesis.

## Validation depth and execution

Use `scripts/detect_ci_scope.py` through the repository workflow with `auto` as the normal selector:

- `LEAN` — docs/governance/metadata and cheap repository guards;
- `SCOPED` — contained module implementation plus direct consumers/compile/unit/lint;
- `STRONG` — public/shared contracts, Binder/control-plane, persistence, native/JNI, manifest, dependency, R8/ProGuard, packaging/variant or other release-sensitive changes;
- `FULL` — promotion/release, selector/CI/global Gradle/module inventory/toolchain changes, unknown executable paths or explicit full request.

Product depth does not select validation depth mechanically. A `PRODUCT_FEATURE` may still have a narrow engineering risk cone; a `PRODUCT_NONE` selector/toolchain change may require `FULL`.

Execution capability is separate from depth. Required gates are `AGENT_LOCAL`, `REMOTE_AUTOMATED` or `REAL_ENVIRONMENT`. An automatable deterministic gate must not be delegated to the user solely because the coding agent lacks Android tooling.

## Pre-publication readiness

Use `skills/preflight-change/SKILL.md` before integration/release publication. Refresh the intended `dev` revision, review the complete diff, make affected durable owners current, record exact head/base identity, select required gates/profile and classify execution capability.

If selected deterministic gates cannot run agent-local, use `skills/remote-preflight/SKILL.md` and `/preflight` rather than asking the user to run Gradle/R8/Lint/build commands.

At integration, required automatable evidence must pass while residual physical evidence may be explicitly `DEFERRED_TO_RELEASE`. Promotion to `main` requires `FULL` release validation plus every applicable blocking real-environment confirmation.

## Pull-request checks

PRs state the observable outcome, applicable product depth (`PRODUCT_NONE|LOCAL|FEATURE|STRATEGIC`), affected owners/invariants, exact head/base, selected profile/gates, agent-local/remote evidence, affected E2E, durable docs and residual release evidence using truthful `PASS`, `FAIL`, `PENDING` and `N/A` states.

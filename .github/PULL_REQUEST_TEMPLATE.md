## Target branch

- [ ] Ordinary feature, fix, documentation, dependency, UX/UI or infrastructure work targets `dev`.
- [ ] A pull request targeting `main` is either a validated `dev -> main` promotion or an explicit emergency hotfix.

## What changed

Describe one coherent deliverable.

## Why

Explain the problem/outcome and important tradeoffs.

## Invariants / risk

Describe public-contract, data, resource, failure, security, migration or operating-lifecycle implications. Write `N/A` when truly not applicable.

Harness invariants to call out when relevant:

- backend-neutral public/domain contracts;
- explicit model/use-case resolution and activation;
- no native/backend handles outside their owner;
- privacy-safe telemetry/evidence;
- bounded model/context/decode/resource ownership;
- no production/device claim without representative evidence.

## Product experience

If user-facing behavior changes, classify it as structural UX, interaction or visual-only and describe the affected user outcome/task, information/action hierarchy, progressive disclosure/defaults, critical states/feedback/recovery, accessibility/adaptive behavior, design-system ownership and any purposeful motion/graphics semantics. Otherwise `N/A`.

## Build / runtime / artifact lifecycle

State affected `.engineering/commands.json` intents and any build identity, manifest/checksum/build-delta/retention or cleanup implications. Otherwise `N/A`.

## Documentation impact

Classify each owner as `UPDATED` or `N/A`; give a short reason when impact was plausible but `N/A`. README identity means purpose/audience/outcome/mission/positioning. README usage means prerequisites/setup/run/configuration/public API/UI/examples.

- README_IDENTITY: `UPDATED|N/A`
- README_USAGE: `UPDATED|N/A`
- FEATURE_DOCS: `UPDATED|N/A`
- ARCHITECTURE: `UPDATED|N/A`
- ADR: `UPDATED|N/A`
- SECURITY_DATA: `UPDATED|N/A`
- OPERATIONS: `UPDATED|N/A`
- PRODUCT_EXPERIENCE: `UPDATED|N/A`
- CURRENT_STATE: `UPDATED|N/A`
- DOCS_CURRENT_WITH_IMPLEMENTATION: `PASS|FAIL`

A usage-only change must not trigger an opportunistic mission rewrite. Existing feature docs update in the same change as the durable behavior they describe.

## Pre-publication readiness

Record exact publication state:

- HEAD: `<revision>`
- TARGET: `dev@<revision>` (or explicit promotion/hotfix target)
- AMBIGUITY: `PASS|FAIL`
- BASE_FRESHNESS: `PASS|FAIL`
- FULL_DIFF_REVIEW: `PASS|FAIL`
- READINESS: `READY_FOR_CI|READY_FOR_REMOTE_PREFLIGHT|AUTOMATED_PREFLIGHT_CONFIRMED|NOT_READY_FOR_AUTOMATED_PREFLIGHT`

A known-red draft must be explicit and may not claim automated readiness.

## Validation profile

- AUTO resolution: `LEAN|SCOPED|STRONG|FULL`
- Reason: `<selector reason>`
- Affected modules/jobs: `<scope>`
- Override: `N/A|strong|full` and why

`FULL` is not the default. Stronger explicit validation is allowed; weaker-than-auto requires an explicit exception and justification.

## Agent-local validation

List deterministic selected-profile gates the current coding agent executed directly as `PASS|FAIL|N/A`.

## Remote automated validation

List deterministic selected-profile gates unavailable agent-local as `PASS|FAIL|PENDING|N/A`, including `/preflight` trigger/run identity. Do not delegate Gradle/R8/Lint/build/emulator work to the user merely because the agent lacks Android tooling.

## E2E environment / fidelity evidence

For every affected complete journey, use `.engineering/e2e.json` and report:

- critical journey ID;
- execution environment ID;
- fidelity class;
- artifact surface;
- `PASS|FAIL|PENDING|N/A`;
- residual target/physical gaps.

The API35 x86_64 Binder emulator is `simulated_or_emulated`; it is not ARM64 `llama.cpp`, real-model, memory-reclamation, thermal or OEM evidence. A built APK on an emulator can prove package/install behavior without upgrading hardware fidelity.

## Real-environment evidence

Declare physical-device, native ARM64/model, memory, hardware, thermal/performance, packaged cross-app or representative usability evidence as `PASS|PENDING|N/A`. Pending evidence still blocks any stronger claim that depends on it.

## E2E / experience evidence

For a complete critical workflow or stable high-risk UI surface, describe journey, environment/artifact identity, accessibility/visual/usability evidence, cleanup verification and bounded evidence retention. A screenshot alone does not prove interaction, recovery, accessibility, adaptive behavior or usability. Otherwise `N/A`.

## Documentation / design lifecycle

- [ ] Documentation impact was assessed from observable behavior and every affected canonical owner is current.
- [ ] New active docs have supported metadata, bounded reading cost and one navigation owner.
- [ ] Active multi-PR coordination lives in `docs/workstreams/`, not separate plan/progress/status files.
- [ ] Completed workstreams are deleted by default after durable knowledge is transferred; archive is exception-only.
- [ ] Generated screenshots/reports/logs are evidence artifacts, not default durable design/document truth.

## Safety and privacy

- [ ] No GGUF/GGML model binary, credential, signing material, private path/URI, prompt or generated output is committed or exposed.
- [ ] Public contracts remain backend-neutral and implementation details do not leak across module boundaries.

## Promotion-only checklist

Complete only for `dev -> main`.

- [ ] Validation profile is `FULL` on the exact promotion candidate.
- [ ] Repository validation and repository health passed on the exact candidate.
- [ ] Android packaging and native host tests passed on the exact candidate.
- [ ] Required physical/device/release evidence for the promoted claims is attached.
- [ ] All conversations are resolved and the branch is up to date.
- [ ] The merge method preserves validated `dev` history with a merge commit.

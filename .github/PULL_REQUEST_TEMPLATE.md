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

If user-facing behavior changes, classify it as structural UX, interaction or visual-only and describe the affected user outcome/task, information/action hierarchy, progressive disclosure/defaults, critical states/feedback/recovery, accessibility/adaptive behavior, design-system ownership and any purposeful motion/graphics semantics. Confirm that structure/interaction was resolved before motion or polish. Otherwise `N/A`.

## Build / runtime / artifact lifecycle

State affected `.engineering/commands.json` intents and any build identity, manifest/checksum/build-delta/retention or cleanup implications. Otherwise `N/A`.

## Pre-publication readiness

Record the final preflight for the exact branch state being published.

- HEAD: `<revision>`
- TARGET: `dev@<revision>` (or explicit promotion/hotfix target)
- AMBIGUITY: `PASS|FAIL`
- BASE_FRESHNESS: `PASS|FAIL`
- FULL_DIFF_REVIEW: `PASS|FAIL`
- READINESS: `READY_FOR_CI|NOT_READY_FOR_CI`

List locally reproducible gates as `PASS|FAIL|PENDING|N/A`. A known-red draft must be explicitly identified and may not claim `READY_FOR_CI`.

## Validation

List exact checks and evidence executed; never claim evidence not run.

- [ ] `LOCAL PREFLIGHT COMPLETE` for the recorded HEAD/TARGET pair.
- [ ] Relevant targeted tests cover the changed behavior.
- [ ] Required local deterministic preflight gates passed on the recorded HEAD.
- [ ] `Repository validation` is green on the current head.
- [ ] `Repository health` is green on the current head.
- [ ] Relevant Android packaging/native checks are green.

## CI-only / real-environment evidence

Declare physical-device, hardware, thermal/performance, packaged cross-app or other evidence unavailable locally as `PENDING` or `N/A`. Pending evidence still blocks any stronger production/device claim that depends on it.

## E2E / experience evidence

For a complete critical workflow or stable high-risk UI surface, describe journey, environment/artifact identity, accessibility/visual/usability evidence, cleanup verification and bounded evidence retention. A screenshot alone does not prove interaction, recovery, accessibility, adaptive behavior or usability. Otherwise `N/A`.

## Documentation / design lifecycle

- [ ] I updated the canonical durable owner rather than creating a duplicate source.
- [ ] New active docs have supported metadata, bounded reading cost and one navigation owner.
- [ ] Active multi-PR coordination lives in `docs/workstreams/`, not separate plan/progress/status files.
- [ ] Completed workstreams are deleted by default after durable knowledge is transferred; archive is exception-only.
- [ ] Generated screenshots/reports/logs are evidence artifacts, not default durable design/document truth.

## Safety and privacy

- [ ] No GGUF/GGML model binary, credential, signing material, private path/URI, prompt or generated output is committed or exposed.
- [ ] Public contracts remain backend-neutral and implementation details do not leak across module boundaries.

## Promotion-only checklist

Complete only for `dev -> main`.

- [ ] The head is the current protected `dev` candidate.
- [ ] Full non-scoped repository validation passed on the exact candidate.
- [ ] Repository health passed on the exact candidate.
- [ ] Android packaging and native host tests passed on the exact candidate.
- [ ] Required physical/device/release evidence for the promoted claims is attached.
- [ ] All conversations are resolved and the branch is up to date.
- [ ] The merge method preserves validated `dev` history with a merge commit.

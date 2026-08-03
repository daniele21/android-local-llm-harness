# Branch and pull-request workflow

This repository uses one canonical implementation line per active phase. Historical branches may remain temporarily for traceability, but they must not receive new implementation commits after their work is superseded or merged.

## Current canonical line

As of August 2026:

- `main` is the canonical integrated baseline;
- pull request #13 merged the consolidated Phase 1 implementation into `main`;
- pull requests #21, #23, #24, #25, #26 and #27 merged the current Phase 2 telemetry, health, resource and benchmark slices into `main`;
- pull request #28 merged the useful sanity-assertion recovery and ARM64 emulator preflight;
- pull request #29 merged the Google Play-installable physical-device validation app;
- the physical-device GGUF gate remains open and blocks production readiness, releases to application consumers and device-performance claims.

New work must start from the latest `main` unless an explicit, documented stacked dependency requires otherwise.

## Archived historical branches

The following branches are historical references and must not receive new commits.

### Phase 0 and Phase 1

- `agent/phase-0-ci-validation`
- `agent/phase-0-final-validation`
- `agent/phase-1-native-runtime`
- `agent/phase-1-native-runtime-validation`
- `agent/phase-1-content-addressed-import`
- `agent/phase-1-functional-runtime`
- `agent/phase-1-consolidation`
- `agent/post-phase-1-cleanup`

Pull requests #8 and #12 were closed as superseded by #13. Pull request #13 is merged and its effective implementation is now part of `main`.

### Phase 2 merged implementation branches

- `agent/phase-2-room-telemetry`
- `agent/phase-2-health-engine`
- `agent/phase-2-generation-sanity`
- `agent/phase-2-cache-health`
- `agent/phase-2-resource-observability`
- `agent/phase-2-benchmark-regressions`

Their accepted implementation is part of `main`; these branches are retained only for audit until remote cleanup.

### Phase 2 superseded branch

- `agent/phase-2-health-control-plane`

PR #22 is closed as superseded. Its alternative `HealthControlPlane`, multi-fixture DTO hierarchy and granular finding model are not canonical. The compatible assertion behavior is being recovered on PR #28 from current `main`. Do not merge, rebase or continue PR #22's branch.

Delete historical remote branches only after their replacement is safely integrated, unique commits are audited and recovery notes are recorded. Do not rewrite historical refs to point at newer commits because that would destroy their audit value.

## Dependabot and infrastructure branches

Dependabot and infrastructure branches are not product implementation lines. Keep them isolated from runtime and feature changes.

After a major integration into `main`:

- close or refresh dependency pull requests whose base predates the integration;
- require the dependency branch to be based on the current `main` before review;
- review major GitHub Actions upgrades individually, especially changes with runner, caching, security or licensing implications;
- do not mix dependency-only updates with runtime, documentation or observability work.

PR #20 remains the isolated review line for the `gradle/actions` v6 licensing and caching change.

## Rules for new work

1. Start from the latest intended target branch, normally `main`.
2. Use one branch for one coherent deliverable and one pull request.
3. Avoid stacked branches unless the dependency is explicit, short-lived and documented in both pull requests.
4. Rebase or retarget a stacked child branch immediately after its parent merges.
5. Do not continue committing to a branch whose pull request has merged or been closed as superseded.
6. Before opening a new implementation branch, compare the proposed scope with open pull requests and `docs/roadmap.md`.
7. Keep dependency upgrades separate from feature and runtime changes.
8. Sync the active implementation branch with its base before final validation, then run the cumulative clean CI gate.
9. Close superseded pull requests with a note identifying the canonical replacement and any selectively recovered behavior.
10. Delete merged or superseded remote branches after the replacement is safely integrated and audited.

## Required protection for `main`

Repository settings for `main` should require:

- changes through pull requests;
- the stable aggregate status check `Repository validation`;
- the branch to be current with `main` before merge;
- resolved review conversations;
- force pushes and branch deletion to be disabled;
- repository administrators to follow the same protection rules, except for documented emergency recovery.

Physical-device evidence is not required for every repository pull request. It is required before a production-ready release, before distributing the runtime to application consumers and before making device compatibility or performance claims. A pull request that itself introduces such a claim must include or reference the relevant evidence.

## Merge discipline

A pull request may be merged only when:

- it is based on the intended target branch and is conflict-free;
- required CI is green on the current head commit;
- documentation and implementation status agree;
- there is no open pull request carrying a competing implementation of the same responsibility;
- required physical-device evidence is attached when the pull request or release candidate makes a production, compatibility or performance claim.

The open physical-device gate does not prevent unrelated repository development, documentation cleanup or Phase 2 observability work. It does prevent tagging or presenting the runtime as production-ready.

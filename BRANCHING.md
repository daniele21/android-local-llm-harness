# Branch and pull-request workflow

This repository uses one canonical implementation line per active phase. Historical branches may remain temporarily for traceability, but they must not receive new implementation commits after their work is superseded or merged.

## Current canonical line

As of August 2026:

- `main` is the canonical integrated baseline;
- pull request #13 merged the consolidated Phase 1 implementation into `main`;
- there is no active Phase 1 implementation branch;
- the physical-device GGUF gate remains open and blocks production readiness, releases to application consumers and device-performance claims;
- Phase 2 repository work may start from the latest `main` while the physical-device gate is completed independently.

Do not reopen or continue a historical Phase 1 branch. New work must start from the current `main` unless an explicit, documented stacked dependency requires otherwise.

## Historical Phase 0 and Phase 1 branches

The following branches are historical references and must not receive new commits:

- `agent/phase-0-ci-validation`
- `agent/phase-0-final-validation`
- `agent/phase-1-native-runtime`
- `agent/phase-1-native-runtime-validation`
- `agent/phase-1-content-addressed-import`
- `agent/phase-1-functional-runtime`
- `agent/phase-1-consolidation`

Pull requests #8 and #12 were closed as superseded by #13. Pull request #13 is merged and its effective implementation is now part of `main`.

Delete the historical remote branches after the post-merge cleanup has been reviewed and their remaining audit value has been confirmed. Do not rewrite them to point at newer commits because that would destroy their value as historical references.

## Dependabot branches

Dependabot branches are infrastructure updates, not product implementation lines. Keep them isolated from runtime and feature changes.

After a major integration into `main`:

- close or refresh dependency pull requests whose base predates the integration;
- require the dependency branch to be based on the current `main` before review;
- review major GitHub Actions upgrades individually, especially changes with runner, caching, security or licensing implications;
- do not mix dependency-only updates with runtime, documentation or observability work.

## Rules for new work

1. Start from the latest intended target branch, normally `main`.
2. Use one branch for one coherent deliverable and one pull request.
3. Avoid stacked branches unless the dependency is explicit, short-lived and documented in both pull requests.
4. Rebase or retarget a stacked child branch immediately after its parent merges.
5. Do not continue committing to a branch whose pull request has merged or been closed as superseded.
6. Before opening a new implementation branch, compare the proposed scope with open pull requests and `docs/roadmap.md`.
7. Keep dependency upgrades separate from feature and runtime changes.
8. Sync the active implementation branch with its base before final validation, then run the cumulative clean CI gate.
9. Close superseded pull requests with a comment identifying the canonical replacement and confirming whether any commit must be recovered.
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

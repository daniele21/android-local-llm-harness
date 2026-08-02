# Branch and pull-request workflow

This repository uses one canonical implementation line per active phase. Historical branches may remain temporarily for traceability, but they must not receive new implementation commits after their work is superseded.

## Current canonical line

As of August 2026:

- `main` is the last integrated baseline;
- `agent/phase-1-consolidation` is the only active Phase 1 implementation branch;
- pull request #13 is the only active functional Phase 1 pull request;
- all remaining Phase 1 code, documentation and device-evidence changes must target `agent/phase-1-consolidation` until #13 is merged.

Do not create a parallel Phase 1 feature branch merely to continue work already represented in #13.

## Historical Phase 0 and Phase 1 branches

The following branches are retained only as historical references and must not receive new commits:

- `agent/phase-0-ci-validation`
- `agent/phase-0-final-validation`
- `agent/phase-1-native-runtime`
- `agent/phase-1-native-runtime-validation`
- `agent/phase-1-content-addressed-import`
- `agent/phase-1-functional-runtime`

Their implementation work is either already in `main` or represented by the cumulative Phase 1 consolidation branch. Pull requests #8 and #12 were closed as superseded by #13.

The `agent/phase-1-native-runtime` branch contains a historical commit enabling shared Android CPU backend variants. The same effective configuration is present in the consolidation branch and is covered by the clean Android build, so the historical commit must not be cherry-picked into the active line.

After #13 is merged, delete these historical remote branches once their audit value is no longer needed. Do not rewrite them to point at newer commits because that would destroy their value as historical references.

## Dependabot branches

Dependabot branches are infrastructure updates, not product implementation lines. Keep them isolated from the active Phase 1 branch.

Until #13 is merged:

- do not merge Dependabot pull requests into `main`;
- do not cherry-pick dependency-only commits into `agent/phase-1-consolidation` unless a blocking security or CI issue requires it;
- allow Dependabot to rebase or recreate its branches after the Phase 1 merge;
- review major GitHub Actions upgrades individually, especially changes with runner, licensing or caching implications.

## Rules for new work

1. Start from the current target branch, normally the latest `main` after Phase 1 is merged.
2. Use one branch for one coherent deliverable and one pull request.
3. Avoid stacked branches unless the dependency is explicit, short-lived and documented in both pull requests.
4. A stacked child branch must be rebased or retargeted immediately after its parent merges.
5. Do not continue committing to a branch whose pull request has been closed as superseded.
6. Before opening a new implementation branch, compare the proposed scope with open pull requests and `docs/roadmap.md`.
7. Keep dependency upgrades separate from feature and runtime changes.
8. Sync the active implementation branch with its base before final validation, then run the cumulative clean CI gate.
9. Close superseded pull requests with a comment identifying the canonical replacement and confirming whether any commit must be recovered.
10. Delete merged or superseded remote branches after the replacement is safely integrated and audited.

## Merge discipline

A pull request may be merged only when:

- it is based on the intended target branch and is conflict-free;
- required CI is green on the current head commit;
- required device evidence is attached or explicitly deferred by the roadmap and Definition of Done;
- documentation and implementation status agree;
- there is no open pull request carrying a competing implementation of the same responsibility.

For the Phase 1 consolidation, keep #13 open until the physical-device gate and feature-level API documentation are complete. After merge, recreate or rebase infrastructure-only dependency pull requests against the new `main` rather than merging stale branch heads.

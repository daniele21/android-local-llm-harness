# ADR 0008: `dev` integration line and protected promotion to `main`

- Status: Accepted
- Date: 2026-08-05

## Context

The repository previously integrated ordinary feature work directly into `main`. That made the stable branch serve simultaneously as a development queue, cumulative test line and release source. It also allowed long-running recovery branches and product work to compete for the same target while release evidence was still incomplete.

Harness 0.5.0 needs a stable integration line where Android, native, model-management and UX/UI changes can accumulate and be tested together without making every accepted feature immediately release-promotable.

## Decision

The repository uses two protected long-lived branches.

### `dev`

`dev` is the canonical base and target for ordinary development:

- features, non-urgent fixes, dependency updates, documentation and UX/UI work start from the latest green `dev`;
- every change reaches `dev` through a pull request;
- `Repository validation` is required before merge;
- every merge push to `dev` triggers cumulative full Android and native validation;
- packaging runs for relevant changes and can also be requested explicitly for an exact candidate ref;
- a red `dev` freezes new integrations until a fix-forward pull request restores the branch.

Feature pull requests normally use squash merge so each accepted deliverable is represented by one integration commit.

### `main`

`main` remains the stable, protected and release-oriented branch:

- ordinary feature branches cannot target `main`;
- normal changes enter through a promotion pull request from `dev`;
- emergency hotfix branches are the only exception;
- a promotion receives full non-scoped Android validation, native host tests and packaging for the exact candidate;
- a promotion is merged with a merge commit, preserving the identity of the validated `dev` line;
- tags and release artifacts are created only from validated `main` commits.

The repository default branch remains `main` because it represents the latest stable and promotable state, not the daily development base.

## Pull-request policy

Automation rejects pull requests to `main` unless the head is:

- `dev`; or
- an explicit branch with an approved hotfix prefix.

The pull-request template makes `dev` the expected target for ordinary work and adds a separate promotion checklist.

## Hotfix and forward-port

An urgent correction starts from the latest `main`, is validated and merged into `main`, then returns to `dev` through a `main -> dev` pull request. The same patch is not applied independently to both branches.

After a normal `dev -> main` promotion, the resulting merge commit is synchronized back into `dev` before the next promotion cycle so branch ancestry remains explicit.

## CI responsibilities

- Documentation-only pull requests to `dev` may use the fast repository-guard path.
- Implementation pull requests to `dev` use verified scope detection and fail safe to complete Android validation when public contracts or validation infrastructure change.
- Pushes to `dev` use cumulative full Android and native validation regardless of the individual merge diff.
- Pull requests targeting `main` use full non-scoped validation.
- Packaging runs on relevant `dev` and `main` pushes, on promotion or hotfix pull requests targeting `main`, and through explicit manual candidate selection.
- `main` and `dev` both require the stable aggregate check named `Repository validation`.

## Failure, rollback and release handling

If `dev` becomes red, integrations stop and the preferred recovery is a small fix-forward pull request. Reverting the offending integration commit is acceptable when a safe fix-forward is not immediately available.

If a promotion candidate fails, `main` is unchanged and the correction is made on `dev` before opening or refreshing the promotion pull request.

If a released `main` commit is defective, an emergency hotfix is applied through the protected hotfix path, tagged only after validation, and forward-ported to `dev`.

## Consequences

Benefits:

- `main` regains a clear stability and release meaning;
- cumulative interactions are detected on `dev` before promotion;
- recovery, UX/UI and infrastructure work can proceed through one canonical integration queue;
- release evidence is tied to an exact candidate commit.

Costs:

- accepted work may wait on `dev` before appearing in `main`;
- hotfixes require an explicit forward-port;
- branch protection must be maintained consistently for both long-lived branches;
- promotion introduces an additional review and validation step.

## Operational status

The code-owned policy, validation workflows, packaging candidate selection, pull-request template and canonical documentation are delivered together by the governance pull request. Repository-level protection for `dev` remains an administrative action: require an up-to-date pull request, resolved conversations and `Repository validation`, and disable force-push and deletion before treating Phase 1 as fully complete.

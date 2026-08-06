# Documentation map

Status: active
Document type: documentation-governance
Owner: repository
Last reviewed: 2026-08-06

This directory separates current state, target behavior, durable architecture, operational procedures, release gates and historical records. A fact should have one canonical owner; other documents link to it instead of restating it.

## Canonical sources

| Question | Canonical source |
| --- | --- |
| What is integrated, blocked or next? | [`current-state.md`](current-state.md) |
| Which capabilities and milestones remain? | [`roadmap.md`](roadmap.md) |
| What is the target behavior and acceptance criteria? | [`implementation-plan.md`](implementation-plan.md) and focused feature specifications |
| What architecture exists today? | [`architecture.md`](architecture.md) and accepted ADRs under [`adr/`](adr/) |
| What is required before merge or release? | [`definition-of-done.md`](definition-of-done.md) |
| What remains for Harness 0.5.0? | [`releases/harness-0.5.md`](releases/harness-0.5.md) |
| How is a feature intended to behave? | Focused feature documentation under [`features/`](features/) or an existing domain document |
| How is a procedure executed? | The applicable build, signing, device or evidence runbook |
| What happened in a completed plan or audit? | [`archive/`](archive/) |

## Document types

- `current-state`: short operational ledger. It may contain the integration baseline, open blockers and one ordered next sequence.
- `roadmap`: capability-level milestones. It must not repeat active branch names, pull-request narratives or commit-by-commit history.
- `target-specification`: intended behavior and acceptance criteria, independent of a temporary branch or pull request.
- `architecture`: current dependency and ownership boundaries.
- `feature-specification`: durable lifecycle, contracts, failure behavior, privacy and testing for one domain.
- `runbook`: executable operational procedure.
- `release-checklist`: gates for one named release.
- `evidence`: immutable validation result or matrix.
- `historical-plan` or `historical-audit`: read-only context that is never an active source of truth.

## Precedence

When sources disagree, use this order:

1. executable contracts and tests;
2. accepted ADRs;
3. current architecture documentation;
4. focused feature specifications;
5. target implementation plan;
6. current-state ledger;
7. roadmap;
8. README and coding-agent guides;
9. archived material.

Do not silently reconcile a contradiction that can change behavior. Correct the owning source or surface the conflict in the pull request.

## Maintenance rules

- Keep volatile branch, PR, workflow-run and local-candidate state only in pull requests or the short current-state ledger when operationally necessary.
- Do not add completion ledgers to `AGENTS.md`, README or feature specifications.
- Update `current-state.md` when a merged change alters the next operational block.
- Update `roadmap.md` only when capability or milestone status changes.
- Update a feature specification when public behavior, ownership, failure handling, privacy or validation requirements change.
- Archive a plan or audit when its branch, PR or decision checkpoint is closed and the remaining durable behavior has been transferred to an active source.
- Historical documents must begin with an explicit archival banner and must not be referenced as canonical sources.
- Prefer a redirect stub at an old well-known path during migrations so existing links do not break.

## Required metadata

New active planning and governance documents should include:

```text
Status: active
Document type: <type>
Owner: <repository or domain>
Last reviewed: YYYY-MM-DD
```

Archived documents use `Status: historical` and identify the active replacement.

## Validation

Run after adding, removing, renaming or reclassifying documentation:

```bash
python3 scripts/verify-docs.py
python3 scripts/verify-agent-navigation.py
python3 -m py_compile scripts/*.py
git diff --check
```

The agent-navigation guard verifies that every configured Gradle module remains discoverable through the concise repository overview or an applicable scoped guide, without requiring the root guide to duplicate the complete module map.

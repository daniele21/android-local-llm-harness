---
name: preflight-change
description: Establish exact-head READY_FOR_CI before publishing by resolving material ambiguity, verifying target-base freshness, reviewing the complete diff, diagnosing failures at their owner and running every required locally reproducible deterministic gate selected by blast radius.
---

# Preflight Change

Use this Skill immediately before pushing, opening/updating a PR, or otherwise intentionally triggering CI as readiness confirmation. `validate-change` owns the iterative test loop; this Skill owns the final publication decision.

The governing rule is:

> CI should confirm, not discover, deterministic repository failures that the supported local environment can reproduce.

## 1. Resolve material ambiguity

Before claiming readiness, confirm that implementation is not resting on an unresolved material assumption.

First inspect canonical evidence:

- owning contract/state/config/design source;
- architecture/feature docs and accepted ADRs;
- direct consumers, fakes/adapters and nearby tests;
- active workstream acceptance criteria when applicable.

Ask the user only when two reasonable interpretations remain and they would materially change product behavior, public/API/protocol contracts, persisted data/migration semantics, security/trust/privacy boundaries, failure/resource/concurrency/lifecycle behavior, backward compatibility, acceptance criteria or meaningful UX.

Do not ask about local naming/style/implementation choices that preserve observable semantics. If material ambiguity remains unresolved, status is `NOT_READY_FOR_CI`.

## 2. Verify the intended base

Read the intended target branch/ref again before final validation.

- Record exact target/base revision and feature head revision.
- Verify the feature is based on, reconciled with, or proven merge-compatible with the current target according to repository policy.
- Treat stacked work as conditional while parent PRs/dependencies are not integrated.
- After a base/dependency/head change, invalidate prior affected evidence and rerun it.

Do not reuse green evidence from an obsolete head/base relationship.

## 3. Review the complete diff

Inspect the whole diff against the intended base, not only the last edited files.

Look for:

- accidental/generated/private files or debug/logging residue;
- unrelated edits or hidden scope expansion;
- duplicated ownership/policy or a second source of truth;
- weakened/deleted/suppressed tests or validation;
- stale docs/contracts after behavior changed;
- missed direct consumers/fakes/adapters;
- unbounded resources, missing cleanup or changed failure semantics;
- accidental compatibility/migration/security/UX drift.

A diff review is a semantic review, not only a formatting pass.

## 4. Run the final local deterministic matrix

Use `validate-change` and `.engineering/commands.json` to select the narrowest sufficient final matrix for the actual blast radius.

Every required gate that is reproducible in the supported local environment must pass on the exact current head. Depending on scope this can include:

- formatting/formatter check;
- lint/static analysis/typecheck;
- touched module/package compilation;
- focused unit/component tests;
- direct-consumer/contract/integration tests;
- canonical repository `check`/`test`;
- build/package/smoke/E2E where the claim requires them.

Do not run unrelated expensive suites by default. Do not publish a cross-boundary change after only a narrow unit test.

Evidence that genuinely requires CI, a physical device, specialized hardware, an external service or representative users may remain pending for `READY_FOR_CI`, but it must be explicitly labelled and still blocks any stronger claim that depends on it.

## 5. Diagnose failures before editing

For every failure, classify it before changing production code:

- `CHANGE_REGRESSION` — introduced by this change;
- `BASELINE_FAILURE` — reproducible on the intended target base;
- `ENVIRONMENT` — toolchain/dependency/environment mismatch;
- `FLAKY` — non-deterministic and reproduced as such;
- `BASE_DRIFT` — stale/stacked integration effect;
- `ASSUMPTION` — requirement/design/contract assumption is wrong or unresolved.

Then identify the violated invariant and its owner. Fix the owner and add/strengthen regression evidence at the lowest useful level.

Never delete, suppress, weaken or rewrite a legitimate gate simply to make the branch green unless the owning contract itself is intentionally changed.

If the same gate fails again after an attempted fix, stop symptom patching. Re-examine the cause, owner and assumptions and form a new falsifiable hypothesis before editing again. If that exposes material ambiguity, return to section 1 and ask the user.

## 6. Check local/CI parity

When deterministic CI logic exists, confirm that local validation uses the same project-owned commands/scripts where practical. Workflow YAML may orchestrate environment setup, caching and artifacts, but should not secretly own a different formatter/test/build policy.

If a prior CI run found a deterministic failure that local preflight missed, treat that class of failure as a preflight/parity defect and close the gap before declaring readiness again.

## 7. Output readiness

Report:

```text
HEAD: <revision>
TARGET: <branch>@<revision>
AMBIGUITY: PASS|FAIL
BASE_FRESHNESS: PASS|FAIL
FULL_DIFF_REVIEW: PASS|FAIL
LOCAL_GATES:
  <gate>: PASS|FAIL|PENDING|N/A
CI_ONLY / REAL_ENVIRONMENT:
  <gate>: PENDING|N/A
READINESS: READY_FOR_CI|NOT_READY_FOR_CI
```

`READY_FOR_CI` requires all of:

- no unresolved material ambiguity;
- current target/base relationship verified;
- complete diff reviewed;
- every required locally reproducible deterministic gate is `PASS`;
- exact head recorded;
- non-local evidence explicitly declared.

Any later edit, rebase/merge/replay, dependency change or material target-base movement invalidates the affected readiness evidence.

A known-red draft may be published only when the user explicitly wants a collaboration/investigation artifact. State the known-red condition clearly; do not label it `READY_FOR_CI`.

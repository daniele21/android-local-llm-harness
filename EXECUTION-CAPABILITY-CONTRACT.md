# Validation Execution Capability Contract

Version: 0.2.0

This contract defines **who executes required validation** and **how much automated validation is justified** when a repository is maintained by different kinds of coding agents. It complements the project engineering standard without weakening required evidence.

The governing rules are:

> Automation should execute automatable work. A human must not become the fallback test runner merely because a coding agent lacks a local shell, checkout, SDK or build environment.

> Validation depth follows blast radius. Do not run a full repository/release matrix when a narrower automated profile can prove the changed invariants.

## Execution classes

Every required gate is assigned, for the current agent/session, to exactly one class:

- `AGENT_LOCAL` — the agent can execute it directly on the exact current head.
- `REMOTE_AUTOMATED` — deterministic and automatable, but unavailable in the current agent environment; repository-owned automation executes it.
- `REAL_ENVIRONMENT` — genuinely requires representative hardware, protected authority, external environment or manual evidence.

Ordinary Gradle, Kotlin compilation, Lint, R8/minification, unit tests and unsigned build/package work are not `REAL_ENVIRONMENT` merely because the current ChatGPT session lacks Android tooling.

## Validation depth profiles

Execution location and validation depth are separate decisions. The repository exposes an `auto` selector mapping the exact change blast radius to the narrowest sufficient profile:

- `LEAN` — docs/governance/metadata-only or cheap universal guards with no executable/product blast radius.
- `SCOPED` — contained implementation owner/module plus direct consumers, focused compile/tests/lint.
- `STRONG` — shared contracts, persistence/security, native/JNI, manifest, dependency, R8/ProGuard, packaging/variant or other cross-boundary/release-sensitive changes.
- `FULL` — promotion/release, selector/global-build/dependency-inventory/toolchain changes, unknown executable paths, explicit full validation or cases where narrowing cannot be trusted.

`FULL` is exceptional on ordinary feature PRs. Automatic escalation is allowed; silent downgrade below `auto` is forbidden.

## Automatic profile selection

The project-owned selector must:

- compare exact intended base/head paths;
- keep docs-only/metadata-only changes cheap when safe;
- map implementation changes to owners/modules and direct consumers;
- escalate shared contracts, persistence/security, native/build/package/R8/dependency/variant changes;
- fail safe stronger on unknown executable paths;
- force `FULL` when the selector/build inventory itself changes;
- report selected profile, reason and affected modules/jobs.

A stronger explicit request such as `/preflight strong` or `/preflight full` is allowed. A weaker-than-auto override is exceptional and requires explicit justification.

## No-human-runner principle

An automatable deterministic gate MUST NOT be delegated to the user solely because the coding agent lacks local execution capability.

Correct flow:

```text
agent lacks Android SDK
-> classify Gradle/R8 gate as REMOTE_AUTOMATED
-> select profile from blast radius
-> trigger repository-owned remote preflight with profile=auto
-> inspect result/logs
-> fix owning cause
-> re-evaluate profile
-> retrigger automation
```

## Agent-triggerable remote preflight

The default remote request is equivalent to `/preflight auto`.

Remote preflight must:

- resolve and validate the exact PR/head revision;
- select the profile from blast radius unless a stronger profile is explicitly requested;
- execute the same project-owned validation semantics used by normal CI rather than inventing a second test policy;
- report profile, reason, affected modules/jobs and PASS/FAIL evidence;
- be safely retriggerable after a fix;
- keep evidence bounded/privacy-safe;
- avoid production/signing/deployment secrets when executing change-branch code.

## Security model

For PR-triggered remote execution:

- accept commands only from trusted repository associations/actors;
- resolve and pin the exact PR head SHA;
- default to same-repository PR heads;
- execute change-branch code with read-only/no write repository credentials;
- expose no production/signing/deployment secrets;
- separate any write-capable reporting step from code execution;
- preserve bounded timeout, artifact retention and concurrency.

## Readiness states

- `READY_FOR_CI` — every deterministic gate required by the selected profile could run agent-local and passed.
- `READY_FOR_REMOTE_PREFLIGHT` — semantic/base/diff checks and available local gates passed; one or more selected deterministic gates are `REMOTE_AUTOMATED`.
- `AUTOMATED_PREFLIGHT_CONFIRMED` — every deterministic automated gate required by the selected profile passed for the exact head/base.
- `NOT_READY_FOR_AUTOMATED_PREFLIGHT` — a required gate failed, profile selection is unsafe, automation routing is missing, or another blocker prevents truthful validation.

Real-environment evidence is tracked separately and may remain `PENDING`; it still blocks any stronger claim that depends on it.

## Failure loop

```text
remote failure
-> inspect logs/evidence
-> classify failure
-> identify violated invariant + owner
-> patch owning cause
-> re-evaluate blast radius/profile
-> review diff/base impact
-> retrigger remote preflight
```

Do not ask the user to rerun the same automatable command between iterations. If remote automation or trustworthy scope detection is missing, classify that as `AUTOMATION_CAPABILITY_GAP` or `VALIDATION_SCOPE_GAP` and repair the repository automation rather than permanently assigning work to a human.

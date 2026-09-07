---
name: shape-product-change
description: Shape a material product change before substantial implementation. Classify product impact, establish user/problem/outcome, expose value/usability/feasibility/viability risks and assumptions, choose the smallest sufficient solution and define success evidence without forcing PRD ceremony onto implementation-only work.
---

# Shape Product Change

## Goal

Turn a product-affecting request into a bounded, evidence-aware product decision that can flow into UX and engineering without confusing a requested solution with the actual problem.

The governing order is:

```text
PRODUCT IMPACT
-> USER / CONSUMER
-> PROBLEM / JOB
-> DESIRED OUTCOME
-> PRODUCT RISKS / ASSUMPTIONS
-> EVIDENCE
-> SMALLEST SUFFICIENT SOLUTION
-> SUCCESS
-> UX / ENGINEERING HANDOFF
-> POST-RELEASE QUESTION (when material)
```

## 1. Classify product depth first

Read `.engineering/product.json` and the owning product/feature sources.

Choose the smallest justified depth:

### `PRODUCT_NONE`

No material supported behavior/product promise changes. Stop the product workflow and continue with normal engineering.

### `PRODUCT_LOCAL`

Problem and solution direction are already settled. State only:

- affected user/consumer;
- expected outcome;
- observable acceptance.

Do not create a product brief.

### `PRODUCT_FEATURE`

New capability or meaningful supported-behavior/workflow change. Continue through the full compact workflow below.

### `PRODUCT_STRATEGIC`

Material change to target user, product boundary, value proposition, trust model, platform, distribution/business model or another decision with broad downstream impact. Use stronger evidence and alternatives before substantial implementation.

Do not classify by number of files or estimated coding effort. Product depth follows impact, uncertainty and reversibility.

## 2. Establish product intent

For feature/strategic changes answer concisely:

```text
User/consumer:
Problem/job:
Desired outcome:
Why it matters / existing evidence:
Non-goals:
```

Prefer evidence already present in current product docs, issues, support signals, usage evidence or explicit owner direction. Do not invent user research.

If a requested component/feature conflicts with durable product intent, surface the conflict instead of optimizing the requested implementation blindly.

## 3. Assess product risks

For each risk classify `LOW`, `MEDIUM`, `HIGH` or `N/A`, with one short reason:

- `VALUE` — will the intended user/consumer care enough for the outcome to matter?
- `USABILITY` — can they understand/use it successfully?
- `FEASIBILITY` — can it meet technical/operational/security/performance/lifecycle constraints?
- `VIABILITY` — can the product/ecosystem sustainably support it, including compatibility/cost/policy/business constraints that materially apply?

Do not manufacture uncertainty. If product intent is directly established and a risk is already resolved by strong current evidence, mark it low and move on.

## 4. Expose only material assumptions

For each high-consequence uncertain belief record:

```text
A1 <assumption>
If wrong: <consequence>
Evidence: <known evidence or NONE>
Next evidence: <cheapest useful falsification/confirmation or NOT_NEEDED>
```

Treat assumptions as assumptions. Do not phrase a plausible belief as established user behavior.

## 5. Choose evidence proportionately

Use the least expensive and least invasive evidence that can materially change the decision.

Possible evidence includes:

- existing usage/support data;
- representative-user observation/feedback;
- prototype/usability evidence;
- dogfooding;
- benchmark or technical spike;
- compatibility experiment;
- alternative/market evidence;
- privacy-safe telemetry;
- bounded rollout experiment.

A discovery step may validly conclude:

- `BUILD`;
- `NARROW_SCOPE`;
- `CHOOSE_ALTERNATIVE`;
- `DO_NOT_BUILD`.

Do not build a complete production feature only to answer a question a smaller experiment can resolve.

## 6. Shape the smallest sufficient solution

If implementation remains justified:

1. state the candidate solution in user/system terms;
2. consider a materially simpler credible alternative when solution direction is not already fixed;
3. preserve explicit non-goals;
4. identify product-quality attributes that constrain the solution;
5. reject speculative configuration/abstraction whose value is not established.

The goal is the smallest coherent solution that can create the desired outcome, not the largest interpretation of the request.

## 7. Define success at the right layers

For `PRODUCT_FEATURE`/`PRODUCT_STRATEGIC` distinguish:

```text
ACCEPTANCE
What observable behavior proves we built the intended contract?

OUTCOME
What becomes easier/possible/better for the user/consumer?

PRODUCT IMPACT
What real-use signal would indicate meaningful value when such evidence is material?
```

Product-impact evidence need not be analytics. It can be usability evidence, support friction, task success, operational quality, adoption or another proportionate signal.

Never report `shipped` as equivalent to confirmed product impact.

## 8. Hand off without duplicating sources

If meaningful UI/UX semantics are affected, pass user/problem/outcome/constraints to `skills/design-product-experience/SKILL.md`.

If persistent implementation coordination is justified, place only the compact Product Intent/Risks/Success information in the existing workstream before its engineering DAG. Do not create parallel PRD/progress/plan files.

Engineering should translate product quality attributes into explicit technical invariants/budgets/evidence at the correct owners.

## 9. Define post-release learning only when needed

For feature/strategic changes ask:

> What useful product question can only real use answer after release?

If none, mark `N/A`.

If material, record one or a few bounded questions and the least invasive useful evidence. Examples include discovery/adoption, task success, support friction, experienced performance, compatibility cost or unexpected workflow behavior.

Do not add telemetry merely because the product workflow mentions observation.

## Output discipline

For a material change, produce an implementation-ready summary no larger than needed:

```text
PRODUCT_DEPTH:
USER / PROBLEM / OUTCOME:
NON_GOALS:
RISKS: value / usability / feasibility / viability
ASSUMPTIONS: material only
DECISION: BUILD / NARROW_SCOPE / CHOOSE_ALTERNATIVE / DO_NOT_BUILD
SOLUTION: if building
QUALITY_CONSTRAINTS:
SUCCESS: acceptance / outcome / product impact
UX_HANDOFF: required/N/A
POST_RELEASE_QUESTION: <question or N/A>
```

Persist durable product truth only when it changes `docs/product.md` or the owning feature/contract. Temporary discovery reasoning belongs in the active workstream/issue/change context, not a permanent research archive.

## Stop conditions

Surface the conflict instead of improvising when:

- the requested solution contradicts durable product mission/non-goals;
- a material high-risk assumption has no evidence and implementing first would be expensive or hard to reverse;
- product quality requirements and the candidate solution are incompatible;
- usability intent is unresolved but implementation is trying to lock the UI structure;
- feasibility evidence contradicts the intended product claim;
- compatibility/deprecation impact would silently break supported consumers.

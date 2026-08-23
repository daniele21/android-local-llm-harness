---
name: review-reference-quality
description: Audit a repository or milestone against the Agent-Native Reference Engineering Standard and produce a prioritized evidence-based L0/L1/L2 gap assessment across engineering, operations and applicable product experience without adding speculative machinery.
---

# Review Reference Quality

Use before important releases, after major architecture/product-experience change, during standard adoption, or when the user asks whether the repository is solid/reference-grade.

## Review dimensions

Assess evidence for:

1. architecture and ownership;
2. complexity/dependency discipline;
3. resource/memory lifecycle;
4. concurrency/backpressure;
5. failure/cancellation/shutdown/recovery;
6. data lifecycle, privacy and security;
7. observability and error taxonomy;
8. layered testing and architecture/contract invariants;
9. E2E coverage of critical workflows when the complete outcome cannot be proven below that level;
10. performance budgets/evidence;
11. reproducibility/build/package behavior;
12. project operating contract and canonical command routing;
13. build identity, artifact lineage, retention, manifests/checksums and build deltas;
14. local runtime/port/process ownership and zero-residue cleanup;
15. repository hygiene;
16. CI/branch/release enforcement;
17. documentation lifecycle/canonical ownership;
18. agent operability and context/token cost;
19. when `product-ui` is adopted: product-experience contract, information architecture, progressive disclosure, critical states/feedback/recovery, accessibility, adaptive behavior, design-system/brand ownership and UX evidence.

## Product experience evidence

For `product-ui`, inspect the actual product and owning contracts rather than grading from attractive screenshots.

Assess:

- whether UI concepts follow user tasks rather than internal architecture;
- whether primary/secondary/destructive actions have a clear hierarchy;
- whether advanced/debug complexity is progressively disclosed;
- whether normal workflows have sensible defaults;
- critical loading/empty/error/disabled/offline/permission/partial states;
- feedback/progress and actionable recovery;
- keyboard/focus/assistive semantics/text scaling/contrast/reduced motion as applicable;
- responsive/adaptive layouts across supported contexts;
- canonical design source, semantic tokens and component reuse;
- bounded key reference views rather than screenshot/mockup sprawl;
- critical journeys linked to appropriate E2E;
- visual/accessibility/usability regression evidence at a level justified by risk.

Do not label a UI "modern" or "intuitive" without explaining the observable hierarchy, interaction and evidence behind that assessment.

## Evidence rules

- Inspect code, tests, CI/config and current canonical docs/design contracts; do not grade from README claims alone.
- Read `.engineering/commands.json` and verify that declared commands map to real project behavior.
- When `product-ui` is adopted, read `design/ux-contract.json` and `design/brand-kit.json` and verify they point to real design/component ownership rather than placeholder documentation.
- Distinguish unit/integration/E2E/smoke/accessibility/visual/usability evidence instead of treating all green checks as equivalent.
- E2E should be a small set of critical workflows, not a requirement to automate every UI path.
- If lower-level tests fully prove a workflow invariant, do not recommend E2E merely for compliance aesthetics.
- Browser/web projects should prefer Playwright when adding new browser E2E unless an equally strong established solution already exists; do not replace a good incumbent tool without cause.
- Do not recommend a new design system/UI framework when strong established components/tokens already exist.
- Distinguish documented policy from machine-enforced reality.
- For runtime/build/E2E lifecycle claims, inspect cleanup paths for success, failure, timeout, cancellation and interrupt.
- Verify E2E/visual failure evidence is privacy-safe, identity-bearing and bounded-retention rather than repository clutter.
- Verify successful artifacts are identifiable/immutable and local retention is bounded.
- Verify build deltas compare against the previous successful comparable build.
- Distinguish host/emulator/synthetic evidence from representative device/hardware/user evidence.
- Mark unavailable evidence as unknown/pending, not passing.
- Recommend the simplest fix that closes a real invariant or experience gap.

## Output

Produce:

- current maturity: L0/L1/L2 or `below L0`;
- strongest existing practices worth preserving;
- blocking gaps for the next level;
- important non-blocking risks/debt;
- test-layer observations (unit/integration/E2E/smoke and evidence strength);
- operating-contract observations (commands, build/artifact identity, runtime/cleanup, retention and build delta);
- product-experience observations when applicable (task model, hierarchy/disclosure, states, accessibility/adaptive behavior, design ownership, critical journeys/evidence);
- agent-context observations;
- a prioritized remediation DAG with dependencies and parallelizable lanes when meaningful;
- evidence required before claiming the next maturity level.

Prefer a short list of high-leverage gaps over a large generic checklist.

# Core project-local Skills

These Skills are copied into the Harness and versioned with the project. They encode recurring procedures that should not inflate the root `AGENTS.md`.

Core set:

- `plan-workstream` — create a bounded dependency-aware active plan only when coordination is justified;
- `structured-change` — preserve ownership, simplicity, resource/failure/data invariants and resolve material ambiguity during meaningful changes;
- `design-product-experience` — reason through meaningful UX/UI work in the correct order, with proportional depth, before implementation/polish;
- `validate-change` — choose the narrowest sufficient validation while iterating and diagnose failures at the owning invariant;
- `preflight-change` — establish exact-head `READY_FOR_CI` only after target-base freshness, complete-diff review and required local deterministic gates;
- `finalize-workstream` — transfer durable knowledge and delete completed plans by default;
- `review-reference-quality` — perform an L0/L1/L2 gap review before important milestones.

Harness may specialize local copies. Record customization in `.engineering/baseline.json` so future baseline migrations merge rather than overwrite local procedure.

`design-product-experience` is active because Harness adopts `product-ui`; use it for meaningful structural UX, interaction or motion/visual-system changes. Local visual-only token/style edits should stay proportional rather than expanding into unnecessary design process.

Before publication, `preflight-change` owns the readiness decision; `validate-change` remains the iterative validation procedure.

Do not create a Skill for one-off instructions. A Skill is justified when a procedure recurs, is conditional, has non-obvious ordering/hazards, or saves substantial repeated agent context.

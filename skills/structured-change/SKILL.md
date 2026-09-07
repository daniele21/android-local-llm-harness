---
name: structured-change
description: Shape a meaningful Harnex behavior, contract, resource or UI change around its owner, observable outcome and regression evidence before editing and completion.
---

# Structured Change

Use for meaningful code/product changes; copy-only edits do not need this procedure. State the **observable outcome, canonical owner, invariants to preserve and proof of success** in the task/PR. Inspect the owner, material consumers/fakes and nearby tests; establish failing evidence for reproducible bugs when practical.

Resolve material ambiguity from current code/contracts/ADRs first. Ask only when alternatives change product behavior, compatibility, public contracts, persistence, privacy/security, lifecycle or meaningful UX.

Search before adding state/configuration/policy. Extend the existing owner and deliver the smallest coherent outcome; technical layers are subtasks unless independently valuable. Preserve bounded lifecycle/backpressure/cancellation/cleanup, failure/recovery, local-first data handling, native/build semantics and public Binder/Consumer compatibility. For material UI use `../design-product-experience/SKILL.md` and canonical design owners.

Use `../validate-change/SKILL.md` for focused feedback. Implementation is complete when behavior, consumers, relevant failure/resource semantics and focused proof agree. `../preflight-change/SKILL.md` owns full-diff/docs/final evidence at integration; do not repeat publication ceremony on every edit.

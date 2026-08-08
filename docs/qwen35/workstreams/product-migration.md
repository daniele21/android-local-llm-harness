# Qwen3.5-only product migration

Status: active
Document type: feature-specification
Owner: qwen35
Canonical scope: qwen35.product-migration
Read when: retiring multi-family catalog eligibility, migrating bindings or presenting installed models outside the Qwen3.5 support envelope
Last reviewed: 2026-08-08

## Goal

Apply [ADR 0011](../../adr/0011-qwen35-only-product-support.md) to catalog, binding, import and inventory behavior without deleting user-managed model bytes or making the UI an alternate policy owner.

## Eligibility boundary

After migration, catalog eligibility is limited to manifest-declared dense Qwen3.5 0.8B and 2B releases. A stale catalog document, retained profile or user-entered import label cannot bypass this boundary. Manual imports remain stored but ineligible for selection or preparation until Q35-2 can prove their supported class structurally.

Catalog filtering is not sufficient by itself. Installation and runtime preparation enforce the same support policy through owning domain contracts so older last-good catalogs and local imports fail closed.

## Legacy installed artifacts

An installed artifact outside the support envelope remains content-addressed user data. Migration must:

- retain its bytes and integrity metadata;
- expose a stable `LEGACY_UNSUPPORTED`-equivalent inventory state;
- prevent new selection, binding, load and session creation;
- allow explicit verification, details and user-confirmed removal;
- invalidate a selected or bound legacy identity visibly rather than substituting another model;
- never remove it during runtime release, reconciliation or application upgrade.

Exact enum names may differ, but unsupported status must not be represented as missing, corrupt or automatically replaceable.

## Catalog transition

The next curated revision offers only supported-tier Qwen3.5 releases. Existing non-Qwen3.5, Qwen3 and Qwen3.5 4B entries become unavailable for new eligibility. Catalog availability remains an administrator lifecycle axis; it does not represent runtime compatibility or certification evidence.

The initial certification candidates are the 0.8B Q4_K_M and 2B Q4_K_M releases. Other supported-tier quantizations may remain `CANDIDATE` with experimental or unverified evidence, but they cannot inherit certification.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| Q35-MIG-01 | PLANNED | Publish a curated revision containing only Qwen3.5 0.8B/2B eligible releases and remove unsupported profile mappings. |
| Q35-MIG-02 | PLANNED | Add a shared coarse support-envelope decision used by catalog, binding and runtime preparation; defer structural import admission to Q35-2. |
| Q35-MIG-03 | PLANNED | Invalidate unsupported bindings with typed, observable failures and no fallback. |
| Q35-MIG-04 | PLANNED | Represent retained installed artifacts outside the envelope as legacy/unsupported. |
| Q35-MIG-05 | PLANNED | Block selection and load while preserving details, verification and explicit removal. |
| Q35-MIG-06 | PLANNED | Update connected inventory and recovery presentation through owning controllers. |
| Q35-MIG-07 | PLANNED | Replace product-eligibility fixtures for Qwen2/Qwen3/LFM/SmolLM while retaining family-neutral contract tests. |
| Q35-MIG-08 | PLANNED | Add upgrade/reconciliation tests proving no installed bytes are deleted or silently rebound. |

## Acceptance criteria

Q35-1 is complete when:

- no unsupported catalog release is eligible for new download, installation, selection or binding;
- manual imports remain ineligible until structural Qwen3.5 admission is implemented in Q35-2;
- unsupported bindings fail with a typed reason and never select a replacement;
- retained legacy artifacts remain visible, verifiable and explicitly removable;
- reconciliation, runtime release and application upgrade do not delete legacy bytes;
- UI state derives from domain decisions and contains no duplicate family-admission logic;
- deterministic catalog, installer, runtime, inventory and migration tests pass.

Structural artifact admission and backend proof begin in [`model-compatibility.md`](model-compatibility.md) after this product boundary is established.

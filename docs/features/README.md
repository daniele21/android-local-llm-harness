# Feature documentation

Status: active
Document type: feature-index
Owner: repository
Canonical scope: documentation.features
Read when: locating or creating durable cross-module feature behavior documentation
Last reviewed: 2026-09-04

Feature documents describe durable current or accepted target behavior, constraints, ownership and verification when those facts are not sufficiently discoverable from public contracts, tests and architecture documentation.

Do not create one file per small feature. Prefer code/tests for obvious behavior and a bounded document for cross-module or operationally important capabilities. Feature documents must not contain implementation progress, PR history or completed task diaries.

When a change alters durable behavior already described by a feature document, update that canonical owner in the same change. Create a new feature document only when durable non-obvious behavior is not sufficiently discoverable from public contracts, tests, code, architecture or an existing focused owner. Delete or consolidate a feature document when it no longer has an independent durable purpose.

## Current feature owners

- [`phone-app-architecture.md`](phone-app-architecture.md) — connected Android phone application state/effect/navigation/lifecycle composition boundaries.
- [`application-control-plane-ux.md`](application-control-plane-ux.md) — task-first Applications -> assigned use case -> preset UX, states, adaptive/accessibility behavior and acceptance contract for shared-runtime consumer configuration.
- [`local-inference-activity-audit.md`](local-inference-activity-audit.md) — durable local inference attribution/content history, strict audit lifecycle, retention/privacy boundaries and Activity presentation contract.

A feature document should normally capture the user/system outcome, canonical owner and important consumers, relevant public/domain contracts, persistence/data and resource/failure semantics when applicable, durable constraints, and verification/evidence expectations.

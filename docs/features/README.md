# Feature documentation

Status: active
Document type: feature-index
Owner: repository
Canonical scope: documentation.features
Read when: locating or creating durable cross-module feature behavior documentation
Last reviewed: 2026-08-23

Feature documents describe durable current behavior, constraints, ownership and verification when those facts are not sufficiently discoverable from public contracts, tests and architecture documentation.

Do not create one file per small feature. Prefer code/tests for obvious behavior and a bounded document for cross-module or operationally important capabilities. Feature documents must not contain implementation progress, PR history or completed task diaries.

## Current feature owners

- [`phone-app-architecture.md`](phone-app-architecture.md) — connected Android phone application state/effect/navigation/lifecycle composition boundaries.

A feature document should normally capture the user/system outcome, canonical owner and important consumers, relevant public/domain contracts, persistence/data and resource/failure semantics when applicable, durable constraints, and verification/evidence expectations.

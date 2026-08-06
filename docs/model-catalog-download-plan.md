# Model distribution lifecycle

Status: active
Document type: feature-index
Owner: models
Canonical scope: models.distribution-lifecycle
Read when: work spans catalog selection, secure transfer, installation and phone model management
Last reviewed: 2026-08-06

This document is the progressive-disclosure entry point for administrator-managed GGUF distribution. Focused specifications own the detailed behavior; implementation progress belongs in [`current-state.md`](current-state.md), not here.

## Product contract

Administrators publish reviewed releases and immutable artifact metadata. Users may inspect compatibility and explicitly request download, installation, selection, loading or removal. None of those operations implies the next one.

```text
catalog release
  -> strict validation and compatibility evaluation
  -> explicit user download request
  -> allowlisted HTTPS transfer into private temporary storage
  -> digest and size verification
  -> opaque VerifiedDownloadHandle
  -> explicit GGUF inspection and ModelStore publication
  -> installed metadata reconciliation
  -> explicit selection/binding
  -> runtime preparation
```

## Ownership map

| Boundary | Canonical source | Owner |
| --- | --- | --- |
| Catalog documents, releases, targets and compatibility | [`curated-model-catalog.md`](curated-model-catalog.md) | `models/model-catalog` |
| Network policy, partial files, retries and verified holding | [`secure-model-download.md`](secure-model-download.md) | `models/model-download` |
| GGUF inspection and explicit publication | [`model-installation.md`](model-installation.md) | `models/model-install` |
| Content-addressed installed artifacts | [`architecture.md`](architecture.md) and model-store contracts | `models/model-store` |
| Phone orchestration and presentation | [`phone-model-distribution.md`](phone-model-distribution.md) | `apps/local-llm-phone-test` |
| User-visible model actions | [`model-management-phone.md`](model-management-phone.md) | `apps/local-llm-phone-test` |
| Unified installed/catalog/runtime state | [`harness-model-inventory-state.md`](harness-model-inventory-state.md) | `apps/local-llm-phone-test` |

Accepted architectural constraints are recorded in ADRs [`0005`](adr/0005-admin-model-catalog-boundaries.md), [`0006`](adr/0006-secure-model-download-core.md) and [`0007`](adr/0007-explicit-verified-download-installation.md).

## Invariants

- Catalog entries identify artifacts by exact SHA-256 and expected byte size.
- Remote data selects only application-reviewed profile keys; it cannot inject arbitrary prompts, paths, code or unrestricted backend settings.
- Application and use-case targeting is exact and fails closed.
- Download locations are not model identities.
- The verified-download backing path is never exposed to UI, catalog or runtime code.
- Only `ModelStore` publishes installed artifacts.
- A verified download is not installed; an installed artifact is not selected; selection does not imply RAM residency.
- Cancellation and failure clean private partial files and preserve previously installed state.
- Removal respects active runtime ownership and does not silently switch to another model.

## Failure and recovery

Each boundary returns typed failures owned by that boundary. UI code maps those failures to presentation state without reimplementing policy.

- Catalog validation rejects malformed, oversized, unsupported or unauthorized data before persistence.
- Compatibility distinguishes hard blockers from user-visible warnings.
- Transfer revalidates redirects, host policy, public address resolution, declared size, storage headroom and final digest.
- Installation rechecks catalog, reviewed profile and inspected GGUF metadata before publication.
- Partial publication rolls back any newly written state and does not delete a prior valid installation.
- Startup reconciliation may recover catalog and installed state, but selection remains explicit.

## Privacy and observability

Safe telemetry may include release identifiers, immutable digests, byte counts, duration, retry count and typed outcomes. It must not include signed URLs, query strings, document URIs, private paths, prompts or generated content.

## Acceptance criteria

- Catalog, transfer, installation and selection are independently testable with fakes.
- Invalid input, cancellation, retry, cleanup and reconciliation have deterministic coverage.
- Network and filesystem policy remains outside Compose and runtime orchestration.
- Installed bytes are addressable only by verified digest.
- Phone UI exposes explicit actions and accurate intermediate states without manufacturing domain decisions.
- Real-device download, installation, load and generation evidence is required before compatibility or production-readiness claims.

The original phase and pull-request plan is preserved in [`archive/plans/2026-08-model-catalog-download-plan.md`](archive/plans/2026-08-model-catalog-download-plan.md).

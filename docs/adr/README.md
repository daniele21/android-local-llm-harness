# Architecture Decision Records

Status: active
Document type: adr-index
Owner: repository
Canonical scope: architecture.decisions
Read when: a change may alter a durable architectural constraint or supersede an accepted decision
Last reviewed: 2026-08-31

Architecture Decision Records capture decisions that materially constrain the runtime, public contracts, native integration, storage, security or deployment model.

## Records

- [`0001-room-backed-telemetry.md`](0001-room-backed-telemetry.md) — persistent telemetry ownership, threading, retention and privacy defaults
- [`0002-build-and-quality-toolchain.md`](0002-build-and-quality-toolchain.md) — pinned Android, Gradle and quality-tool configuration
- [`0003-no-model-binaries-in-source.md`](0003-no-model-binaries-in-source.md) — external GGUF/GGML artifact policy
- [`0004-phone-test-upload-key-custody.md`](0004-phone-test-upload-key-custody.md) — external Play upload key, Keychain workflow and recovery policy
- [`0005-admin-model-catalog-boundaries.md`](0005-admin-model-catalog-boundaries.md) — separation of remote model distribution, verified installation and local inference
- [`0006-secure-model-download-core.md`](0006-secure-model-download-core.md) — allowlisted HTTPS transfer, partial-file recovery and pre-installation integrity verification
- [`0007-explicit-verified-download-installation.md`](0007-explicit-verified-download-installation.md) — opaque verified-download access, GGUF inspection, ModelStore publication and post-import rollback
- [`0008-dev-integration-and-protected-promotion.md`](0008-dev-integration-and-protected-promotion.md) — protected `dev` integration and validated promotion to `main`
- [`0009-model-aware-generation-planning.md`](0009-model-aware-generation-planning.md) — versioned generation planning, trusted prompt templates and lazy context materialization
- [`0010-model-aware-embedded-first.md`](0010-model-aware-embedded-first.md) — explicit application/use-case model binding and embedded-first deployment
- [`0011-qwen35-only-product-support.md`](0011-qwen35-only-product-support.md) — Qwen3.5-only product support over family-neutral lifecycle contracts
- [`0012-shared-runtime-same-signer-binder-boundary.md`](0012-shared-runtime-same-signer-binder-boundary.md) — same-signer trust/protocol/model-authority boundary and original bound-only v1 lifecycle; lifecycle/background portions are superseded by ADR 0016
- [`0013-public-consumer-capability-boundary.md`](0013-public-consumer-capability-boundary.md) — host-owned model authority, capability discovery, policy-scoped selection and deterministic prepared execution for public consumers
- [`0014-backend-spi-boundary.md`](0014-backend-spi-boundary.md) — backend-neutral SPI ownership, runtime dependency inversion and store-neutral model-loading boundary
- [`0015-harness-managed-control-plane.md`](0015-harness-managed-control-plane.md) — Harness-owned persistent applications/use cases/presets/bindings, activation-residency leases, unified cross-app telemetry and decision/notification ownership
- [`0016-detached-shared-runtime-jobs.md`](0016-detached-shared-runtime-jobs.md) — logical inference jobs independent from transient Binder/UI lifetime, idempotent reconnect/recovery semantics and explicit Android background execution

## Status values

- Proposed
- Accepted
- Superseded
- Deprecated

## Naming

Use sequential files:

```text
NNNN-short-decision-title.md
```

Each ADR includes context, decision, consequences and alternatives considered. A later ADR supersedes an earlier decision rather than rewriting its history.

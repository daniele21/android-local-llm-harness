# Architecture Decision Records

Architecture Decision Records capture decisions that materially constrain the runtime, public contracts, native integration, storage, security or deployment model.

## Records

- [`0001-room-backed-telemetry.md`](0001-room-backed-telemetry.md) — persistent telemetry ownership, threading, retention and privacy defaults
- [`0002-build-and-quality-toolchain.md`](0002-build-and-quality-toolchain.md) — pinned Android, Gradle and quality-tool configuration
- [`0003-no-model-binaries-in-source.md`](0003-no-model-binaries-in-source.md) — external GGUF/GGML artifact policy
- [`0004-phone-test-upload-key-custody.md`](0004-phone-test-upload-key-custody.md) — external Play upload key, Keychain workflow and recovery policy
- [`0005-admin-model-catalog-boundaries.md`](0005-admin-model-catalog-boundaries.md) — separation of remote model distribution, verified installation and local inference
- [`0006-secure-model-download-core.md`](0006-secure-model-download-core.md) — allowlisted HTTPS transfer, partial-file recovery and pre-installation integrity verification

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

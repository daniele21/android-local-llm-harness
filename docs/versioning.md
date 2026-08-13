# Versioning and release policy

Status: active
Document type: release-policy
Owner: repository
Canonical scope: release.versioning
Read when: changing versions, compatibility promises, promotion or release artifacts
Last reviewed: 2026-08-13

## SDK versions

Published Android and Capacitor artifacts follow Semantic Versioning.

Before `1.0.0`, minor releases may contain deliberate API changes, but every breaking change must include migration notes. Patch releases must remain backward compatible within the same minor line.

The shared-runtime Android client artifact has its own SDK identity. Development builds use snapshot semantics; a distributable client version is not inferred from the host application version or Binder protocol version.

## Independent identities

The following identities are versioned or recorded separately from the SDK release:

- Harness repository/version identity;
- shared-runtime host application version/build identity;
- shared-runtime Android client SDK version;
- shared-runtime Binder protocol major/minor and negotiated feature set;
- GGUF artifact digest;
- model load profile schema;
- use-case profile schema;
- app binding schema;
- health suite definition;
- benchmark definition;
- diagnostics protocol;
- pinned `llama.cpp` commit.

Changing an SDK version must never implicitly change an application's configured model identity. Changing the host version must not silently redefine the Binder compatibility contract. Changing a compatible Binder minor does not, by itself, require the host and client package versions to match.

## Shared-runtime compatibility identity

A shared-runtime release or physical evidence record must identify at least:

```text
harness git commit
host package + version/build
client SDK version
Binder protocol major/minor
negotiated protocol minor/features when execution evidence exists
host/client signing-certificate digest identity
selected curated model digest
pinned llama.cpp revision
Android device/version/ABI for physical evidence
```

The Binder protocol remains independently versioned from host/client packaging. Major incompatibility fails before registration; compatible minor differences negotiate the common feature set. The protocol fixture policy is owned by the shared-runtime contract documentation rather than Semantic Versioning of either APK.

Signing certificate digests are evidence/security identities, not product versions. Full certificates, private keys and passwords are never release metadata.

## Integration and release lines

- `dev` carries snapshot development and is the only normal base and target for feature work.
- `main` carries stable promotable history and receives ordinary changes only through a complete `dev -> main` promotion.
- Feature pull requests normally squash into `dev`; promotions use a merge commit to preserve the exact validated candidate.
- Tags, changelog release entries and distributed Android artifacts are created only from validated `main` commits.
- Emergency hotfixes are applied to `main` and then forward-ported to `dev`.

## Release gate

A release requires:

- an exact `dev` candidate promoted to `main` through a protected pull request;
- complete non-scoped Android, native and packaging validation on the candidate;
- passing CI from a clean checkout;
- changelog entry;
- public API review;
- updated sample applications when relevant;
- model and device compatibility notes;
- benchmark comparison for runtime-critical changes;
- explicit cache/snapshot compatibility decision;
- checksums for distributed artifacts.

For a shared-runtime client/host distribution, the release gate additionally requires:

- same-signer physical two-APK evidence for the exact candidate identity;
- independently signed client denial;
- packaged release client-AAR consumer execution;
- cancellation, host-death/reconnect, memory and thermal evidence;
- protocol compatibility fixtures and applicable package replacement/upgrade evidence;
- release notes binding host version, client SDK version, protocol identity, runtime/backend identity and selected model evidence;
- a security review of exported service, signature permission, caller identity policy and privacy boundary.

## Development versions

Development builds on `dev` use snapshot semantics and are not releases. Harness `0.5.0` is the current internal-integration target; it may be promoted to `main` and distributed through Google Play Internal Testing only after its promotion gates pass. The shared-runtime client currently carries snapshot identity until its physical/release gates close.

Harness 0.5.0 and the shared runtime must not be described as production-ready until representative physical-device Qwen3.5 lifecycle, cancellation, memory, JNI-loading, thermal and cross-process release evidence is complete.

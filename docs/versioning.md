# Versioning and release policy

## SDK versions

Published Android and Capacitor artifacts follow Semantic Versioning.

Before `1.0.0`, minor releases may contain deliberate API changes, but every breaking change must include migration notes. Patch releases must remain backward compatible within the same minor line.

## Independent identities

The following identities are versioned separately from the SDK release:

- GGUF artifact digest;
- model load profile schema;
- use-case profile schema;
- app binding schema;
- health suite definition;
- benchmark definition;
- diagnostics protocol;
- future shared-runtime Binder protocol;
- pinned `llama.cpp` commit.

Changing an SDK version must never implicitly change an application's configured model identity.

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

## Development versions

Development builds on `dev` use snapshot semantics and are not releases. Harness `0.5.0` is the current internal-integration target; it may be promoted to `main` and distributed through Google Play Internal Testing only after its promotion gates pass. It must not be described as production-ready until representative physical-device GGUF lifecycle, cancellation, memory, JNI-loading and thermal evidence is complete.

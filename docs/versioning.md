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

## Release gate

A release requires:

- passing CI from a clean checkout;
- changelog entry;
- public API review;
- updated sample applications when relevant;
- model and device compatibility notes;
- benchmark comparison for runtime-critical changes;
- explicit cache/snapshot compatibility decision;
- checksums for distributed artifacts.

## Development versions

The repository starts at `0.1.0-SNAPSHOT`. The first tagged `0.1.0` release is reserved for a functional embedded GGUF inference path and does not occur during repository hardening alone.

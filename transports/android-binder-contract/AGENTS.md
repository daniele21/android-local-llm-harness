# Android Binder Contract — Coding Agent Guide

Scope: `transports/android-binder-contract`

Read the repository root `AGENTS.md`, then `docs/shared-runtime/README.md`, `docs/shared-runtime/architecture.md` and `docs/shared-runtime/workstreams/protocol-v1.md` when changing this module.

## Ownership

This module owns only the Android Binder protocol boundary:

- AIDL interfaces shared by host and client;
- explicit Parcelable wire DTOs;
- protocol version/capability negotiation;
- structural and payload-size validation;
- supported `core/contracts` projections;
- stable wire error/event codes;
- ordered delta chunking and terminal reconstruction helpers.

It must not own host caller authorization, model selection, runtime orchestration, storage, telemetry persistence, service lifecycle policy, client binding state or UI.

`core/contracts` must never depend on this module. Wire-to-core mapping is allowed here because the dependency direction is transport -> semantic core.

## Protocol invariants

- Fail closed on incompatible majors, non-overlapping minor ranges, required missing features and unknown semantic tags.
- Keep minor evolution append-only; do not repurpose existing tags or field meanings.
- Do not serialize core data classes, Java/Kotlin object graphs, arbitrary `Bundle` or `Map` values.
- Do not send application identity or model selection as trusted client input.
- Do not expose backend messages, paths, signing material or native details in `WireErrorParcel`.
- Do not duplicate completed output in a terminal parcel; clients reconstruct bounded output from ordered deltas.
- Keep every delta at or below `BinderProtocolV1.MAX_DELTA_CHARACTERS` without splitting Unicode surrogate pairs.
- Keep prompts, schemas and generated content out of fixtures except small synthetic privacy-safe strings.

## Focused validation

```bash
./gradlew spotlessCheck
./gradlew :transports:android-binder-contract:testDebugUnitTest
./gradlew :transports:android-binder-contract:compileDebugKotlin
./gradlew :transports:android-binder-contract:compileDebugAidl
./gradlew :transports:android-binder-contract:compileDebugAndroidTestKotlin
./gradlew :transports:android-binder-contract:lintDebug
./gradlew :transports:android-binder-contract:assembleDebug
python3 scripts/verify-agent-navigation.py
```

Run the repository-wide Android gate for protocol, Gradle, AIDL or public-boundary changes.

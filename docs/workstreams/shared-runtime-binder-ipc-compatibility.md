# Shared Runtime Binder IPC Compatibility

Status: active
Document type: implementation-workstream
Owner: shared-runtime-protocol
Canonical scope: workstream.shared-runtime-binder-ipc-compatibility
Last reviewed: 2026-08-26

## Goal

Make the published Harness Consumer Android SDK safe for a separately built Android consumer APK that enables R8/minification, so the packaged cross-process journey can execute:

`connect -> assignedUseCases -> publishedPresets -> activate -> inference -> deactivate`

without Binder/Parcel framing failures and without consumer-owned Harness keep rules.

## Triggering evidence

A physical release pair using the same signer and granted signature permission remained bound and kept both processes alive, but `assignedUseCases()` failed before inference while the Harness process logged repeated:

`libbinder.Parcel: Attempt to read or write from protected data in Parcel`

at the offset where the following Binder callback object starts. The installed RedactGuard release is R8-minified; its mapping shows Consumer control-plane Parcelable classes transformed, while the Harness host build is not minified. Source comparison shows the control-plane request DTO and AIDL method are unchanged between the consumed alpha.5 source revision and current Harness `dev`.

R8/packaging is therefore the leading hypothesis, not yet an accepted root cause. The first slices must reproduce or falsify it before a compatibility fix is selected.

## Non-goals

- Do not change host control-plane policy, persisted state, model selection or CPREC semantics.
- Do not change protocol major/minor or DTO field meaning unless evidence proves a wire-schema defect.
- Do not disable R8 globally in RedactGuard or consumer applications.
- Do not add Harness-specific keep rules to RedactGuard as the durable fix.
- Do not use prompt/document/model output in committed diagnostics or fixtures.

## Invariants

- `transports/android-binder-contract` owns Binder/AIDL/Parcelable wire compatibility.
- The externally published SDK must carry any optimizer rules required for its own wire contract.
- Host and consumer remain independently built APKs; repository/project coupling is not an acceptable proof.
- Physical packaged two-APK behavior is a required exit gate and cannot be replaced by JVM or emulator evidence.
- A failure is fixed at its owning boundary; legitimate shrinker/contract checks are not weakened to make CI green.

## Execution DAG

| ID | State | Owns / writes | Acceptance | Depends on |
| --- | --- | --- | --- | --- |
| SR-BIPC-00 | ACTIVE | this workstream; repository current-state routing | Evidence, invariants, DAG and executable slices are recorded. | - |
| SR-BIPC-10 | ACTIVE | `apps/shared-runtime-client-consumer-fixture` | Release fixture uses production-like R8/minification and the existing real Binder control-plane test is capable of reproducing/falsifying the release-only failure. | 00 |
| SR-BIPC-20 | ACTIVE | Binder packaging/diagnostic test surface | Exact packaged wire classes/AIDL/Parcelable behavior can be compared across minified consumer and non-minified host; evidence identifies the first divergent owner rather than only the product symptom. | 00 |
| SR-BIPC-30 | BLOCKED | root-cause decision in this workstream | Classify root cause as optimizer/packaging, Parcel/AIDL framing, or another owner. No keep-rule fix lands before this gate. | 10, 20 |
| SR-BIPC-40 | BLOCKED | `transports/android-binder-contract` or evidence-selected owner | Minimal owner-level compatibility fix; if R8 is confirmed, required consumer rules ship from the Binder contract AAR. | 30 |
| SR-BIPC-50 | BLOCKED | `apps/shared-runtime-client-consumer-fixture` | Minified packaged client passes discovery, presets, activation, duplicate rejection, deactivation and a Consumer API call against the release host. | 40 |
| SR-BIPC-60 | BLOCKED | `samples/external-consumer-android`; publication verification | Standalone Maven-only consumer validates release/minified packaging and proves SDK-owned rules are actually propagated. | 40 |
| SR-BIPC-70 | BLOCKED | Consumer SDK publication | Next available prerelease is published with source identity, manifest/checksum and external-consumer verification. | 50, 60 |
| SR-BIPC-80 | BLOCKED | `redactguard-android` dependency/docs/tests | RedactGuard consumes the fixed published SDK with R8 still enabled and no Harness-specific local keep rule. | 70 |
| SR-BIPC-85 | ACTIVE | `redactguard-android` connected-device diagnostic script/docs | Service declaration and UID checks are robust on current Android output; diagnostic false negatives are separated from product failures. | 00 |
| SR-BIPC-90 | BLOCKED | physical two-APK evidence | Exact built artifacts pass upgrade-preserving and clean two-APK paths; no protected-Parcel log appears and Local AI reaches real inference with synthetic input. | 80, 85 |
| SR-BIPC-100 | BLOCKED | durable docs/current state | Durable protocol/publication/runbook truth updated and temporary workstream deleted by default. | 90 |

## Current executable slices

Run in parallel where write ownership does not conflict:

- `SR-BIPC-10` — production-like minified packaged consumer fixture.
- `SR-BIPC-20` — wire/package divergence evidence.
- `SR-BIPC-85` — RedactGuard diagnostic correctness.

`SR-BIPC-30` is the integration gate. Do not infer the corrective mechanism from the current symptom alone.

## Validation

### Harness focused iteration

```bash
./gradlew spotlessCheck
./gradlew :transports:android-binder-contract:testDebugUnitTest
./gradlew :transports:android-binder-contract:compileDebugKotlin
./gradlew :transports:android-binder-contract:compileDebugAidl
./gradlew :transports:android-binder-contract:compileDebugAndroidTestKotlin
./gradlew :transports:android-binder-contract:lintDebug
./gradlew :transports:android-binder-contract:assembleDebug
./gradlew :apps:shared-runtime-client-consumer-fixture:assembleRelease
./gradlew :apps:shared-runtime-client-consumer-fixture:assembleReleaseAndroidTest
bash scripts/verify-consumer-sdk-publication.sh
python3 scripts/verify-agent-navigation.py
```

Because this work changes a public Android transport/build boundary, final readiness also requires the repository `check`, `test` and `build` commands from `.engineering/commands.json` on the exact head.

### RedactGuard integration

Use the canonical RedactGuard commands for formatting/lint, unit tests, artifact packaging and the two-APK E2E. A release/minified artifact is required for the compatibility claim.

### Physical evidence

Final physical evidence must record exact host/app source revisions and artifact identities. Device evidence is `PENDING` until executed; emulator or host-only tests cannot upgrade that status.

## Durable destinations

When complete, transfer only current truth to:

- `docs/shared-runtime/workstreams/protocol-v1.md` for Binder compatibility requirements;
- `docs/shared-runtime/consumer-android-sdk.md` for publication/minified-consumer guarantees;
- relevant release/validation runbooks for packaged two-APK evidence;
- RedactGuard `docs/features/local-ai-consumer.md` / runtime adapter docs for the consumed SDK identity and boundary.

Git history owns implementation history; delete this temporary workstream after durable transfer.

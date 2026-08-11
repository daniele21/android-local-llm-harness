# Shared runtime validation and rollout

Status: active
Document type: feature-specification
Owner: shared-runtime-validation
Canonical scope: shared-runtime.validation-rollout
Read when: adding shared-runtime tests, two-APK device execution, evidence, compatibility matrices or release gates
Last reviewed: 2026-08-11

## Goal

Prove that the Binder boundary preserves functional behavior, authorization, isolation, cleanup, privacy and acceptable device overhead before any client SDK or host capability is distributed.

This workstream does not certify model quality. It consumes exact Qwen3.5 runtime evidence and adds cross-process transport/deployment evidence.

## Dependencies

- SR-1 protocol fixtures.
- SR-2 host and SR-3 client deterministic tests.
- SR-4 two-APK vertical slice.
- Applicable Q35 tuning/validation evidence before consumer release.

## Inspect before editing

- [`../../device-e2e-testing.md`](../../device-e2e-testing.md)
- [`../../device-e2e-evidence.md`](../../device-e2e-evidence.md)
- [`../../definition-of-done.md`](../../definition-of-done.md)
- [`../../versioning.md`](../../versioning.md)
- app scoped guides, manifests, build variants and packaging scripts
- existing device runner and evidence redaction scripts

Read backend/native evidence sources only for cases that exercise JNI/model lifecycle. Do not duplicate Q35 semantic or tuning matrices here.

## Validation layers

```text
wire unit/parcel tests
  -> host/client deterministic tests with fakes
  -> same-APK separate-process Binder instrumentation where useful
  -> two separately installed APK emulator preflight
  -> two separately installed APK physical-device evidence
  -> packaged AAR consumer and release compatibility review
```

Passing a lower layer never substitutes for a higher required layer. Emulator results are preflight only.

## Test applications

Primary positive path:

- host: `apps/local-llm-phone-test`;
- client: `apps/local-llm-console`;
- exact build variants signed by an accepted common signing lineage;
- exact host component and permission names supplied to the client build.

Negative authorization path:

- produce a minimal client fixture signed with an ephemeral independent key generated outside source control;
- attempt explicit binding and verify denial before protocol/runtime information;
- delete or retain the key only in the temporary evidence workspace according to the runner policy;
- never commit keystores, passwords or exported signing material.

Do not use `sharedUserId`. Do not weaken the service permission to simplify instrumentation.

## Two-APK runner

Add one focused script after SR-4, proposed as:

```text
scripts/run-shared-runtime-device-e2e.sh
```

Responsibilities:

1. require an explicit device or exactly one connected device;
2. verify physical/emulator classification and label evidence accurately;
3. build or accept exact host/client APK paths;
4. record package, version, certificate digest, protocol/SDK identity and git commit;
5. install/upgrade host and client in deterministic order;
6. verify the host has an exact curated model installed/selected without copying it into source;
7. drive bind, prepare, session, streaming, cancellation and cleanup scenarios;
8. capture privacy-safe results, relevant process/resource snapshots and filtered logs;
9. run negative signature and version cases when requested;
10. leave installed apps/model state according to explicit cleanup flags.

The runner does not download arbitrary models, expose host-private paths or record prompts/outputs in shared reports.

## Functional matrix

| Case | Expected result |
| --- | --- |
| Host absent | Typed host-not-installed state; no retry loop. |
| Host present, model absent | Bind succeeds; prepare fails explicitly without download. |
| Valid same-signer client | Register, prepare, stream, complete and close. |
| Invalid signer | Bind/authorization denied before runtime information. |
| Unauthorized use case | Typed denial; no model load. |
| Protocol major mismatch | Incompatible before registration/prepare. |
| Compatible minor mismatch | Negotiated common feature set. |
| Cancel while queued | One cancelled terminal event and released mapping. |
| Cancel during decode | Cooperative backend cancellation and reusable runtime. |
| Client killed | Host cancels/closes only that client's resources. |
| Host killed | Client receives one disconnect outcome; reconnect starts clean. |
| Two clients | Existing scheduler serializes decode; ownership remains isolated. |
| Same external IDs | Host internal IDs remain collision-free across clients. |
| Slow callback | Bounded backpressure cancellation; no unbounded growth. |
| Unbind/rebind | Old sessions invalid; new registration works. |

## Lifecycle and resource matrix

Measure or assert:

- service create/bind/unbind/destroy counts;
- active client/session/request ledger size before and after each terminal path;
- Binder death-recipient registration/removal;
- runtime loaded-model and scheduler state after client death;
- model remains installed and selected after service/client cleanup;
- host UI recreation does not duplicate runtime/service graph;
- process PSS before connection, after load, after generation and after cleanup;
- cancellation latency and time from core delta to client callback;
- no sustained callback/executor/thread growth over repeated runs.

Transport overhead is compared with an equivalent in-process run only when execution identity, model state and device conditions are comparable. Do not attribute model performance changes to Binder without matching evidence.

## Privacy and security checks

Use unique non-sensitive sentinel strings in test prompt/output and assert absence from:

- normal host/client structured telemetry;
- Room databases or exported reports;
- logcat captured by the runner;
- saved instance state and app-private preference/database projections;
- failure messages and evidence JSON/CSV.

The interactive client may display bounded output in memory. Test artifacts shared from the run omit prompt, reasoning and answer text.

Security review covers:

- exported service and permission ownership;
- explicit component binding;
- calling UID/package/signing verification;
- token entropy and cross-UID rejection;
- use-case allowlist and absence of client model control;
- request/session quota enforcement;
- denial behavior without information leakage;
- no path, Binder token or certificate disclosure;
- no unintended diagnostics/control-plane access.

## Compatibility matrix

Maintain fixtures and device/package coverage for:

| Client | Host | Expected |
| --- | --- | --- |
| v1.0 | v1.0 | Full v1 baseline. |
| older compatible minor | newer minor | Common negotiated features. |
| newer compatible minor | older minor | Client avoids unavailable optional features. |
| v1 | future incompatible major | Fail before registration. |
| current SDK | host missing required feature | Typed incompatibility. |

Upgrade tests cover host replacement while the client is installed and client replacement while the host/model state remains. No upgrade test assumes live sessions survive process/package replacement.

## Evidence identity

Every physical evidence record includes:

```text
harness git commit
host package/version/build type/signing certificate digest
client package/version/build type/signing certificate digest
client SDK version
Binder protocol major/minor and negotiated features
Android device model/version/ABI
exact model artifact digest and runtime/generation profile identity
llama.cpp revision
scenario and cold/warm classification
terminal code/stop reason
timing, token, memory and thermal fields applicable to the scenario
```

It excludes GGUF bytes, prompt/output/schema text, app-private paths, Binder tokens, signing keys/passwords and full certificate data.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| SR-VAL-01 | PLANNED | Define shared-runtime test fixtures and evidence schema. |
| SR-VAL-02 | PLANNED | Add host/client fake and Binder instrumentation coverage. |
| SR-VAL-03 | PLANNED | Add repeatable two-APK emulator preflight runner. |
| SR-VAL-04 | PLANNED | Add ephemeral independently signed denial fixture. |
| SR-VAL-05 | PLANNED | Add multi-client, ID collision, death and backpressure matrix. |
| SR-VAL-06 | PLANNED | Add prompt/output sentinel privacy assertions. |
| SR-VAL-07 | PLANNED | Add protocol/SDK/host upgrade compatibility matrix. |
| SR-VAL-08 | PLANNED | Run physical-device two-APK functional/lifecycle evidence. |
| SR-VAL-09 | PLANNED | Compare Binder overhead against matching in-process evidence. |
| SR-VAL-10 | PLANNED | Complete security, public API, packaging and consumer-release review. |

## Merge gate

Each implementation PR runs the narrowest deterministic checks for its owner. SR-4 and later additionally require:

- both application unit/lint/assembly checks;
- binder contract/client/host checks;
- repository formatting, Detekt and model-artifact guard;
- repository-wide Android checks for shared contracts/Gradle/manifests;
- packaging verification;
- emulator/device runner preflight when the changed behavior requires it;
- documentation and agent-navigation guards.

Exact commands are added when the planned modules/tasks exist so this document does not advertise non-existent Gradle targets as executable today.

## Consumer-release gate

Do not publish the client AAR or describe the host as application-consumable until:

- SR-1 through SR-5 are integrated and their deterministic gates pass;
- physical two-APK evidence passes for supported release-like variants;
- applicable Q35 artifact/runtime physical evidence is complete;
- cancellation, death, memory and thermal behavior is reviewable;
- invalid-signer and incompatible-version cases pass;
- public API and protocol compatibility policies are accepted;
- consumer sample uses the packaged AAR;
- release artifacts are built from an exact validated `main` commit.

## Completion criteria

SR-5 completes when isolation, death, bounded transport and privacy matrices pass deterministically. SR-6 completes only when physical evidence and release review support the exact distribution claim. Host/client emulator success alone cannot close SR-6.

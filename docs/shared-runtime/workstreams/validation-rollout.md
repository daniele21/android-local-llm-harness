# Shared runtime validation and rollout

Status: active
Document type: feature-specification
Owner: shared-runtime-validation
Canonical scope: shared-runtime.validation-rollout
Read when: adding shared-runtime tests, two-APK device execution, evidence, compatibility matrices or release gates
Last reviewed: 2026-08-14

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
- [`../sr6-release-evidence.md`](../sr6-release-evidence.md)
- [`../sr6-release-governance-review.md`](../sr6-release-governance-review.md)
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

Primary development/preflight path:

- host: `apps/local-llm-phone-test`;
- client: `apps/local-llm-console`;
- debug variants signed by the common Android debug signing identity;
- exact host component and permission names supplied to the client build.

Primary SR-6 release-like path:

- host: release `apps/local-llm-phone-test` APK;
- client: release `apps/shared-runtime-client-consumer-fixture` APK consuming packaged release Binder AARs;
- host/client/test APKs signed by the accepted common external signing identity;
- exact package, version and certificate digests recorded before execution.

Negative authorization path:

- rebuild only the consumer fixture with an ephemeral independent PKCS12 key generated outside source control;
- attempt explicit binding and verify `PERMISSION_DENIED` before protocol/runtime information;
- delete the key and password with the runner temporary workspace;
- never commit keystores, passwords or exported signing material.

Do not use `sharedUserId`. Do not weaken the service permission to simplify instrumentation.

## Device runners

SR-4 debug/emulator/device functional preflight:

```text
scripts/run-shared-runtime-device-e2e.sh
```

SR-6 release-like evidence capture:

```text
scripts/capture-shared-runtime-release-evidence.sh
```

SR-6 compatible host package replacement evidence:

```text
scripts/capture-shared-runtime-package-upgrade-evidence.sh
```

SR-VAL-09 matched transport comparison:

```text
scripts/compare-shared-runtime-transport-evidence.py
```

The SR-6 release-like runner:

1. requires an explicit device or exactly one connected device;
2. rejects emulators by default and labels `--allow-emulator` execution as preflight only;
3. requires a clean checkout and records the exact git commit;
4. builds same-signer release host/client/test APKs using external signing material;
5. verifies APK signing-certificate SHA-256 digests before installation;
6. installs/upgrades the host while preserving its selected curated-model state;
7. executes packaged-AAR prepare/session/stream/complete/cancel/close tests;
8. terminates/restarts the host and verifies typed disconnect/reconnect behavior;
9. captures privacy-safe timing/token/cancellation markers plus memory/thermal snapshots;
10. generates a temporary independent signing identity and verifies denial;
11. archives reviewable evidence without prompts, outputs, keys, Binder tokens, GGUF bytes, adb serials or host-private paths.

Use `--host-only` to install/launch the correctly signed release host for first-time curated-model setup. The runner never downloads arbitrary models or reaches into host-private model storage.

The package-replacement runner is intentionally separate from the main release-like runner. It accepts exact base/replacement host APKs plus their full source commit SHAs, verifies the same accepted signing certificate across host/client/test artifacts, executes packaged-client Binder traffic before replacement, applies `adb install -r`, executes the same traffic after replacement, and requires the selected model digest to remain unchanged. It does not claim that a live session survives package/process replacement.

The SR-VAL-09 comparator consumes one packaged Binder instrumentation log and one Qwen3.5 in-process tuning log. It accepts only warm in-process samples with the same model digest, context size and thinking mode plus the explicit `sr6-transport-v1` tuning case. It validates the Binder transport envelope against `clientObservedTotalMs - coreTotalMs` and reports comparison measurements without inventing a pass/fail threshold. The paired tuning run must use the same non-sensitive generation intent as the Binder scenario; `tuningCaseId` is retained instead of prompt text.

## Functional matrix

| Case | Expected result | Evidence owner |
| --- | --- | --- |
| Host absent | Typed host-not-installed state; no retry loop. | SR-3 deterministic / SR-4 preflight |
| Host present, model absent | Bind succeeds; prepare fails explicitly without download. | SR-4/SR-6 device |
| Valid same-signer client | Register, prepare, stream, complete and close. | SR-4 debug + SR-6 release-like |
| Invalid signer | Bind/authorization denied before runtime information. | SR-6 release-like |
| Unauthorized use case | Typed denial; no model load. | SR-2/SR-5 deterministic |
| Protocol major mismatch | Incompatible before registration/prepare. | SR-1 fixtures |
| Compatible minor mismatch | Negotiated common feature set. | SR-1 fixtures |
| Cancel while queued | One cancelled terminal event and released mapping. | SR-5 deterministic |
| Cancel during decode | Cooperative backend cancellation and reusable runtime. | SR-6 physical |
| Client killed | Host cancels/closes only that client's resources. | SR-5 deterministic / device review |
| Host killed | Client receives one disconnect outcome; reconnect starts clean. | SR-5 deterministic + SR-6 physical |
| Two clients | Existing scheduler serializes decode; ownership remains isolated. | SR-5 deterministic |
| Same external IDs | Host internal IDs remain collision-free across clients. | SR-5 deterministic |
| Slow callback | Bounded backpressure cancellation; no unbounded growth. | SR-5 deterministic |
| Unbind/rebind | Old sessions invalid; new registration works. | SR-5 deterministic / SR-6 device |
| Compatible host package replacement | New packaged-client traffic works before and after replacement; selected model identity is preserved. | SR-6 physical |

## Lifecycle and resource matrix

Measure or assert:

- service create/bind/unbind/destroy counts where instrumentation exposes them safely;
- active client/session/request ledger cleanup through deterministic host tests;
- Binder death-recipient registration/removal through deterministic host tests;
- runtime loaded-model and scheduler state after client death;
- model remains installed and selected after service/client cleanup;
- host UI recreation does not duplicate runtime/service graph;
- process PSS before and after the release-like evidence sequence;
- cancellation latency and generation timing metrics;
- thermal snapshots before and after the evidence sequence;
- no sustained callback/executor/thread growth over repeated runs when the matrix is extended.

Transport overhead is compared with an equivalent in-process run only when execution identity, model state and device conditions are comparable. Do not attribute model performance changes to Binder without matching evidence.

## Privacy and security checks

Use unique non-sensitive sentinel strings in deterministic tests and assert absence from:

- normal host/client structured telemetry;
- Room databases or exported reports;
- filtered logcat captured by the runner;
- saved instance state and app-private preference/database projections;
- failure messages and evidence files.

The release-like instrumentation may hold bounded generated output in memory only long enough to reconstruct the core terminal event. Shared artifacts omit prompt, reasoning and answer text.

Security review covers:

- exported service and permission ownership;
- explicit component binding;
- calling UID/package/signing verification;
- token entropy and cross-UID rejection;
- use-case allowlist and absence of client model control;
- request/session quota enforcement;
- independently signed client denial without information leakage;
- no path, Binder token, key/password or full-certificate disclosure;
- no unintended diagnostics/control-plane access.

The repository-level public API/security/versioning/packaging/console-governance review is recorded in [`../sr6-release-governance-review.md`](../sr6-release-governance-review.md). That review accepts the implementation for exact-candidate device validation but explicitly does not approve publication without the physical consumer-release gate.

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
Binder protocol major/minor and negotiated features where available
Android device model/version/ABI
exact host-selected model artifact identity from the prepare/runtime evidence
llama.cpp revision
scenario and cold/warm classification where applicable
terminal code/stop reason
timing, token, memory and thermal fields applicable to the scenario
```

It excludes GGUF bytes, prompt/output/schema text, app-private paths, Binder tokens, signing keys/passwords, adb serials and full certificate data.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| SR-VAL-01 | DONE | Shared-runtime fixtures, matrices and evidence identity are defined. |
| SR-VAL-02 | DONE | Host/client deterministic coverage and real two-APK Binder instrumentation exist. |
| SR-VAL-03 | DONE | Repeatable SR-4 two-APK debug emulator/device preflight runner is integrated. |
| SR-VAL-04 | IN PROGRESS | Ephemeral independently signed denial fixture and runner are implemented; physical execution is pending. |
| SR-VAL-05 | DONE | Multi-client, ID collision, death and bounded-backpressure matrices are deterministic and green. |
| SR-VAL-06 | DONE | Binder-boundary runtime-detail/sentinel privacy assertions are deterministic and green. |
| SR-VAL-07 | IN PROGRESS | Protocol fixtures and a dedicated package-replacement pre/post traffic runner are complete; physical replacement evidence remains pending. |
| SR-VAL-08 | IN PROGRESS | Physical release-like functional/lifecycle runner is implemented; representative device evidence is pending. |
| SR-VAL-09 | IN PROGRESS | Matched Binder/in-process comparator and identity checks are implemented; same-device/model/profile physical comparison remains pending. |
| SR-VAL-10 | DONE | Packaged release AAR consumer plus repository-level security/public-API/versioning/packaging/console-governance review are complete; publication remains gated by exact-candidate physical evidence. |

## Merge gate

Each implementation PR runs the narrowest deterministic checks for its owner. SR-4 and later additionally require:

- both application unit/lint/assembly checks;
- binder contract/client/host checks;
- packaged consumer-fixture compilation;
- repository formatting, Detekt and model-artifact guard;
- repository-wide Android checks for shared contracts/Gradle/manifests;
- packaging verification;
- documentation and agent-navigation guards;
- `python3 -m unittest scripts.tests.test_compare_shared_runtime_transport_evidence` for the SR-VAL-09 comparator;
- `bash -n scripts/capture-shared-runtime-package-upgrade-evidence.sh` for the upgrade runner.

Physical runner execution is mandatory for the SR-6 exit gate, but it is not fabricated in generic GitHub-hosted CI when no representative Android device, selected model or signing identity is available.

## Consumer-release gate

Do not publish the client AAR or describe the host as application-consumable until:

- SR-1 through SR-5 are integrated and their deterministic gates pass;
- physical two-APK evidence passes for supported release-like variants;
- applicable Q35 artifact/runtime physical evidence is complete;
- cancellation, death, memory and thermal behavior is reviewable;
- invalid-signer and incompatible-version cases pass;
- public API and protocol compatibility policies are accepted;
- the consumer sample executes against the packaged release AAR;
- release notes bind host/client/protocol/runtime/backend/model identities;
- release artifacts are built from an exact validated `main` commit.

## Completion criteria

SR-5 completes when isolation, death, bounded transport and privacy matrices pass deterministically. SR-6 completes only when physical evidence and release review support the exact distribution claim. Host/client emulator success or repository CI alone cannot close SR-6.

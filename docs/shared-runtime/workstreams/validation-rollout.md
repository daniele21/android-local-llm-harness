# Shared runtime validation and rollout

Status: active
Document type: feature-specification
Owner: shared-runtime-validation
Canonical scope: shared-runtime.validation-rollout
Read when: adding shared-runtime tests, two-APK device execution, evidence, compatibility matrices or release gates
Last reviewed: 2026-09-05

## Goal

Prove that the Binder boundary preserves functional behavior, authorization, isolation, cleanup, privacy and acceptable device overhead before any client SDK or host capability is promoted as release-ready.

This workstream does not certify model quality. It consumes exact Qwen3.5 runtime evidence and adds cross-process transport/deployment evidence.

## Dependencies

- ADR 0017 independently signed consumer trust boundary.
- SR-1 protocol fixtures.
- SR-2 host and SR-3 client deterministic tests.
- SR-4 two-APK vertical slice.
- Applicable Q35 tuning/validation evidence before a release claim that includes real model execution.

## Inspect before editing

- [`../../device-e2e-testing.md`](../../device-e2e-testing.md)
- [`../../device-e2e-evidence.md`](../../device-e2e-evidence.md)
- [`../../definition-of-done.md`](../../definition-of-done.md)
- [`../../versioning.md`](../../versioning.md)
- [`../sr6-release-evidence.md`](../sr6-release-evidence.md)
- app scoped guides, manifests, build variants and packaging scripts
- existing device runner and evidence redaction scripts

Read backend/native evidence sources only for cases that exercise JNI/model lifecycle. Do not duplicate Q35 semantic or tuning matrices here.

## Validation layers

```text
wire unit/parcel tests
  -> host/client deterministic tests with fakes
  -> same-APK separate-process Binder instrumentation where useful
  -> production-shaped two-APK emulator preflight with the claimed signing topology
  -> applicable two-APK physical/Play evidence
  -> packaged AAR consumer and release compatibility review
```

Passing a lower layer never substitutes for a higher layer when that higher layer supplies unique evidence. Emulator evidence can fully satisfy deterministic Binder/authorization behavior, but it cannot substitute for Play App Signing identity, representative hardware/runtime or other REAL_ENVIRONMENT evidence when those are part of the claim.

## Test applications

Same-publisher development path:

- host: `apps/local-llm-phone-test`;
- client: `apps/local-llm-console`;
- debug variants may share the common Android debug signing identity where same-publisher behavior is the intended topology;
- exact host component and permission names are supplied to the client build.

Independent-consumer path:

- host: `apps/local-llm-phone-test`;
- consumer: a separately developed application such as RedactGuard using the packaged/source-built exact Consumer SDK candidate;
- Host and consumer/test APKs use distinct signing identities;
- Harnex observes the exact installed consumer package/current signer as `PENDING`;
- consumer is denied before explicit Harnex authorization;
- Host-owned authorization promotes only that exact observed identity;
- authorized connect -> disconnect -> reconnect is exercised without sharing signing credentials.

Packaged SR-6 fixture path remains useful for same-publisher packaged-AAR and invalid-signer denial evidence. It is not, by itself, evidence that an independently distributed consumer works.

Negative authorization paths include unknown/mismatched package/signer identities using ephemeral signing material generated outside source control. Keys/passwords are deleted with the runner temporary workspace and are never committed.

Do not use `sharedUserId`. Do not weaken Binder caller verification or reuse the normal inference bind permission for emulator fault/control surfaces.

## Device and CI runners

SR-4 same-publisher debug emulator/device functional preflight:

```text
scripts/run-shared-runtime-device-e2e.sh
```

SR-6 packaged release-like evidence capture:

```text
scripts/capture-shared-runtime-release-evidence.sh
```

Cross-repository independent-signer CI owns the production-topology Binder authorization proof for external consumers. Its evidence must record exact Harnex/consumer source revisions, distinct APK signer digests and the deny -> explicit authorize -> connect/disconnect/reconnect sequence.

The SR-6 physical runner remains appropriate for runtime/device evidence and same-publisher packaged fixture scenarios. For an independently distributed Play consumer, Play Internal installation and physical confirmation record the actual Host/consumer Play App Signing identities rather than pretending local external signing keys are equivalent to Play.

## Functional matrix

| Case | Expected result | Evidence owner |
| --- | --- | --- |
| Host absent | Typed host-not-installed state; no retry loop. | SR-3 deterministic / consumer UI preflight |
| Host present, model absent | Bind/auth can succeed; prepare fails explicitly without download. | SR-4/SR-6 |
| Known independent consumer first observed | Persisted pending; Binder runtime access denied. | Host tests + independent-signer E2E |
| Explicitly authorized independent consumer | Exact package/signer can register only for enabled use cases. | independent-signer E2E |
| Consumer signer replaced | Fail closed as signature-changed until explicit reauthorization. | Host tests + replacement evidence where required |
| Unknown/mismatched signer | Authorization denied before runtime information. | deterministic/E2E negative |
| Valid same-publisher client | Register, prepare, stream, complete and close when intentionally configured. | SR-4 + packaged fixture |
| Unauthorized use case | Typed denial; no model load. | SR-2/SR-5 deterministic |
| Protocol major mismatch | Incompatible before registration/prepare. | SR-1 fixtures |
| Compatible minor mismatch | Negotiated common feature set. | SR-1 fixtures |
| Explicit disconnect/reconnect | Registration releases; same reusable client can establish a fresh authorized epoch. | Consumer SDK tests + independent-signer E2E |
| Cancel while queued | One cancelled terminal event and released mapping. | SR-5 deterministic |
| Cancel during decode | Cooperative backend cancellation and reusable runtime. | SR-6 physical when real runtime claimed |
| Client killed | Host cancels/closes only that client's connection-scoped resources. | SR-5 deterministic / device review |
| Host killed | Client receives one disconnect outcome; reconnect starts clean. | SR-5 deterministic + applicable E2E |
| Two clients | Existing scheduler serializes decode; ownership remains isolated. | SR-5 deterministic |
| Same external IDs | Host internal IDs remain collision-free across clients. | SR-5 deterministic |
| Slow callback | Bounded backpressure cancellation; no unbounded growth. | SR-5 deterministic |

## Lifecycle and resource matrix

Measure or assert as applicable:

- service create/bind/unbind/destroy counts where instrumentation exposes them safely;
- active client/session/request ledger cleanup through deterministic host tests;
- Binder death-recipient registration/removal through deterministic host tests;
- reusable Consumer SDK disconnect/reconnect creates fresh connection epochs;
- runtime loaded-model and scheduler state after client death;
- model remains installed and selected after service/client cleanup;
- host UI recreation does not duplicate runtime/service graph;
- process PSS before and after release-like real-runtime evidence;
- cancellation latency and generation timing metrics;
- thermal snapshots before and after real-runtime evidence;
- no sustained callback/executor/thread growth over repeated runs when the matrix is extended.

Transport overhead is compared with an equivalent in-process run only when execution identity, model state and device conditions are comparable. Do not attribute model performance changes to Binder without matching evidence.

## Privacy and security checks

Use unique non-sensitive sentinel strings in deterministic tests and assert absence from:

- normal host/client structured telemetry;
- Room databases or exported reports;
- filtered logcat captured by runners;
- saved instance state and app-private preference/database projections;
- failure messages and evidence files.

Release-like instrumentation may hold bounded generated output in memory only long enough to reconstruct the core terminal event. Shared artifacts omit prompt, reasoning and answer text.

Security review covers:

- exported service and `BIND_LOCAL_LLM` capability-permission ownership;
- explicit component binding;
- Binder calling UID -> exact installed package -> signing certificate verification;
- Harnex Control Plane pending/authorized/disabled/signature-changed state projection;
- explicit authorization of source-observed identity rather than caller-supplied identity;
- token entropy and cross-UID rejection;
- use-case allowlist and absence of client model control;
- request/session quota enforcement;
- unknown/mismatched signer denial without information leakage;
- no path, Binder token, key/password or full-certificate disclosure;
- emulator fault/control and diagnostics permissions remain separate from inference binding.

## Compatibility matrix

Maintain fixtures and device/package coverage for:

| Client | Host | Expected |
| --- | --- | --- |
| compatible client | compatible host | Common protocol features negotiate after caller authorization. |
| older compatible minor | newer minor | Common negotiated features. |
| newer compatible minor | older minor | Client avoids unavailable optional features. |
| current client using `BIND_LOCAL_LLM` | legacy signature-permission host | No false compatibility claim; migration/unavailability is explicit. |
| v1 | future incompatible major | Fail before runtime preparation. |
| current SDK | host missing required feature | Typed incompatibility. |

Upgrade tests cover host replacement while the client is installed and consumer replacement while the host/model state remains. A signer replacement must never inherit authorization silently. No upgrade test assumes live sessions survive process/package replacement.

## Evidence identity

Every cross-APK evidence record includes the fields material to the scenario:

```text
harness git commit
host package/version/build type/signing certificate digest
consumer package/version/build type/signing certificate digest
client SDK version
Binder protocol major/minor and negotiated features where available
Harnex authorization state transition where tested
Android device model/version/ABI for physical evidence
exact host-selected model artifact identity when runtime execution is tested
llama.cpp revision when native execution is tested
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
| SR-VAL-03 | DONE | Repeatable same-publisher two-APK debug emulator/device preflight runner is integrated. |
| SR-VAL-04 | DONE | Independent signer negative authorization path is deterministic. |
| SR-VAL-05 | DONE | Multi-client, ID collision, death and bounded-backpressure matrices are deterministic and green. |
| SR-VAL-06 | DONE | Binder-boundary runtime-detail/sentinel privacy assertions are deterministic and green. |
| SR-VAL-07 | IN PROGRESS | Protocol fixtures are complete; package/signing replacement evidence remains claim-dependent. |
| SR-VAL-08 | IN PROGRESS | Physical release-like functional/lifecycle runner is implemented; representative device evidence remains claim-dependent. |
| SR-VAL-09 | PLANNED | Compare Binder overhead against matching in-process evidence on the same device/model/profile identity. |
| SR-VAL-10 | IN PROGRESS | Packaged release AAR consumer is executable; final security/public-API/versioning/release review remains pending. |
| SR-VAL-11 | IN PROGRESS | Distinct-signer external consumer deny -> authorize -> disconnect/reconnect E2E is integrated and awaits exact-head confirmation. |

## Merge gate

Each implementation PR runs the narrowest sufficient selector-driven deterministic checks for its blast radius. Security/Manifest/Binder/public-SDK/cross-app changes are STRONG or stronger as selected and include, as applicable:

- affected application unit/lint/assembly checks;
- Binder contract/client/host checks;
- packaged consumer-fixture compilation;
- Consumer SDK ABI/external-consumer validation;
- repository formatting, Detekt and model-artifact guard;
- Android manifest/packaging verification;
- distinct-signer cross-APK authorization E2E for independent-consumer trust changes;
- documentation, ADR and agent-navigation guards.

An automatable deterministic gate remains REMOTE_AUTOMATED when the agent lacks the local Android environment; it is not delegated to a human. REAL_ENVIRONMENT evidence is reserved for facts automation cannot establish, such as actual Play App Signing identities on installed Internal builds, representative hardware/runtime behavior and human UX/accessibility judgement where required.

## Consumer-release gate

Do not describe the Host/Consumer combination as release-ready until:

- SR-1 through SR-5 applicable deterministic gates pass on the exact candidate;
- the claimed Host/consumer signing topology is represented by exact deterministic evidence;
- independently distributed consumers prove distinct signers, denial before explicit authorization and success after authorizing the exact observed identity;
- applicable Play/physical confirmation passes when actual distribution identity is material;
- applicable Q35 artifact/runtime physical evidence is complete for model/runtime claims;
- cancellation, death, memory and thermal behavior is reviewable where material;
- invalid-signer and incompatible-version cases pass;
- public API and protocol compatibility policies are accepted;
- the consumer sample executes against the packaged release AAR;
- release notes bind host/consumer/client-SDK/protocol/signing/runtime/backend/model identities as applicable;
- stable release artifacts are built from an exact validated promotion commit.

## Completion criteria

SR-5 completes when isolation, death, bounded transport and privacy matrices pass deterministically. SR-6 completes only when deterministic automation plus the genuinely required REAL_ENVIRONMENT evidence support the exact distribution/runtime claim. Repository CI is sufficient for deterministic Binder authorization claims; it cannot fabricate Play App Signing or representative physical-runtime evidence.
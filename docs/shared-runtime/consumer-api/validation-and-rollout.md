# Consumer API validation and rollout

Status: active
Document type: feature-specification
Owner: shared-runtime-consumer-api-validation
Canonical scope: shared-runtime.consumer-api.validation-rollout
Read when: adding consumer API tests, compatibility/security review, reference-app validation or release gates
Last reviewed: 2026-08-13

## Goal

Prove that the public consumer API is smaller than the internal runtime surface, correctly policy-scoped, compatible across supported host/client versions and usable by a real external app without embedding Harness infrastructure.

This workstream extends—not replaces—the shared-runtime SR-5/SR-6 security, lifecycle and physical-device evidence.

## Validation layers

```text
contract/unit semantics
  -> host policy resolution with fakes
  -> Binder wire mapping/compatibility fixtures
  -> packaged client SDK consumer compilation
  -> reference Console two-APK behavior
  -> physical same-signer host/client evidence
  -> public API/security/versioning review
```

A UI demonstration does not substitute for contract tests, and emulator success does not substitute for the applicable physical-device release gate.

## Contract validation

### Capability discovery

Verify:

- authenticated caller receives only its allowed use cases;
- capability response contains only caller/use-case-scoped model/preset options;
- defaults are valid and deterministic;
- unauthorized callers/use cases cannot enumerate global inventory;
- readiness changes are revalidated during prepare;
- capability revision changes when policy-visible state requires it;
- no file paths, model URLs, native handles or private diagnostic fields leak.

### Model/preset policy

Verify:

- omitted model/preset resolves advertised defaults;
- allowed model + compatible preset resolves exact host-owned configuration;
- explicit unavailable model fails without download or fallback;
- disallowed model fails before native model load;
- incompatible preset fails without substitution;
- consumer selection cannot specify arbitrary digest/path/runtime profile;
- session/request cannot mutate selection outside the accepted lifecycle semantics.

### Reasoning/output behavior

Verify:

- answer-only models remain valid consumers;
- surfaced reasoning is separately typed from answer;
- reasoning-disabled client receives no reasoning channel;
- explicit unsupported reasoning request follows the accepted typed failure rule;
- JSON/schema requests are accepted only for allowlisted use cases/capabilities;
- reasoning/answer content never enters normal telemetry/evidence.

### Metrics

Verify the stable public projection independently from the internal superset:

- TTFT anchor;
- total-time anchor;
- output token count;
- decode tok/s calculation;
- optional input/reasoning/answer counts;
- stable stop-reason mapping;
- nullability/unknown handling;
- no accidental exposure of diagnostic-only metrics.

Use fake clocks/token accounting where practical for deterministic semantics. Physical runs validate realism, not the definition itself.

## Security review

The API must preserve the accepted same-signer/shared-runtime trust boundary.

Review:

- signature permission and explicit component binding remain intact;
- application identity remains derived from the verified caller;
- capability discovery authenticates before returning policy information;
- model/preset selection is an allowlist selector, not arbitrary model control;
- request/session ownership remains UID/client scoped;
- one client cannot enumerate or control another client's capability/session/request state;
- typed errors do not reveal private paths, signing data or unrelated model inventory;
- capability/result payload sizes are bounded;
- schema/input payloads retain transport validation limits;
- no diagnostics/control-plane permission is implicitly granted by inference access.

Any proposal to support third-party publishers requires a separate trust/product decision and is outside this rollout.

## Compatibility strategy

Classify every wire/public change before implementation.

### Additive SDK-only

Examples:

- convenience facade around existing semantics;
- UI/reference-app projection;
- derived metric naming that does not alter wire behavior.

No Binder protocol bump is needed if wire semantics are unchanged.

### Optional protocol feature

Examples may include:

- capability-discovery transaction;
- optional logical model/preset selection fields;
- optional surfaced reasoning feature flag;
- additional stable metric fields.

These may fit a protocol minor/feature negotiation only if an older peer can safely reject/ignore the feature without changing accepted invariants.

### Incompatible semantic change

If external logical model choice invalidates the accepted v1 invariant that the host alone selects one exact model for the use case, update the durable decision/compatibility boundary explicitly. Do not hide an incompatible semantic change behind a nullable parcel field.

## Compatibility matrix

Maintain at least:

| Client | Host | Expected |
| --- | --- | --- |
| Old client | New host | Existing fixed-use-case flow remains valid. |
| New client, defaults only | Old compatible host | Works only when required discovery/selection features are not required; otherwise typed incompatibility. |
| New client using optional selection | Host supports feature | Capability -> prepare -> generate succeeds. |
| New client using unsupported feature | Older host | Fails before model preparation with typed incompatibility. |
| Compatible minor versions | Compatible peer | Common feature set is negotiated. |
| Incompatible major/semantic boundary | Other peer | Fail closed before preparation. |

Exact version numbers are assigned during implementation/release planning, not in this design plan.

## Reference consumer requirement

`apps/local-llm-console` should become the reference consumer, not a second Harness control plane.

Its success criteria:

- depends on packaged public client/contracts required for inference;
- contains no llama.cpp/JNI runtime;
- contains no GGUF model store/download/install pipeline;
- contains no independent runtime tuning engine;
- does not recreate Harness-wide health/cache/thermal/benchmark controls;
- discovers authorized model/preset capabilities from the host;
- runs prompt -> reasoning/answer stream -> terminal result;
- shows only public metrics/request details;
- handles unavailable/denied/incompatible/disconnected states clearly.

Any remaining Console-local diagnostics must be justified as consumer-SDK diagnostics rather than duplicated host control-plane behavior.

## Reference UI acceptance

The reference Console should use progressive disclosure:

### Primary

- connection state;
- prompt/input;
- allowed model/preset selectors only when more than one useful choice exists;
- reasoning toggle only when surfaced reasoning is available;
- answer;
- Tier 1 metrics.

### Secondary request details

- effective logical model/preset;
- input/reasoning/answer tokens when available;
- stop reason;
- request/session/protocol IDs useful for debugging.

### Not present as normal consumer UI

- model download/remove/import;
- raw runtime knobs;
- global logs;
- health checks;
- memory/thermal dashboards;
- cache repair;
- benchmark administration.

## API-surface review

Before release, inspect the packaged client AAR public surface and enforce:

- no AIDL-generated type required by consumer application code;
- no Android service implementation internals exposed;
- no `llama.cpp`/JNI/native types;
- no host model-store/path types;
- no internal `GenerationOverrides` leakage unless explicitly accepted;
- public enums/codes documented for compatibility;
- nullability/default semantics documented;
- consumer ProGuard/R8 rules validated.

A binary/API compatibility tool may be added if/when the client artifact becomes versioned for external consumption.

## Evidence scenarios

On the reference physical device/model matrix, capture privacy-safe evidence for:

1. capability discovery with one default model/preset;
2. explicit allowed model/preset selection where supported;
3. answer-only generation;
4. surfaced reasoning + answer where supported;
5. cancellation;
6. invalid/disallowed model or preset;
7. host process death/reconnect;
8. package upgrade/replacement as required by SR-6;
9. client-observed wall time versus host public metrics for transport sanity.

Do not store prompt/reasoning/answer text in evidence archives.

## Performance boundary

The public API should not materially change model execution performance beyond the shared-runtime transport/configuration overhead already owned by SR-6.

When comparing configurations:

- use the same device;
- use the same exact model artifact;
- use the same effective preset/profile;
- use comparable cold/warm state;
- distinguish host inference metrics from client wall time;
- do not attribute model/preset differences to Binder overhead.

## Documentation/release requirements

Before consumer release:

- accepted target/boundary is reflected in the relevant ADR/shared-runtime target;
- API reference documents the supported public surface and examples;
- compatibility policy identifies SDK/protocol/capability versioning;
- release notes bind host version, client SDK version, protocol version and capability revision policy;
- reference consumer uses the packaged candidate artifact;
- supported model/preset claims point to applicable device evidence;
- private signing/model/prompt/output information remains excluded.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| CA-VAL-01 | PLANNED | Add deterministic capability-policy/default/allowlist tests. |
| CA-VAL-02 | PLANNED | Add reasoning/answer/result projection and metric-semantics tests. |
| CA-VAL-03 | PLANNED | Add protocol mapping/feature-negotiation compatibility fixtures. |
| CA-VAL-04 | PLANNED | Enforce packaged client public-surface dependency boundary. |
| CA-VAL-05 | PLANNED | Simplify Console into a pure/reference consumer and validate UI states. |
| CA-VAL-06 | PLANNED | Extend two-APK device flow for capability/model/preset/result scenarios. |
| CA-VAL-07 | PLANNED | Complete security/public-API/versioning review. |
| CA-VAL-08 | PLANNED | Capture applicable physical evidence and close release gate. |

## Completion criteria

This workstream is complete only when the reference external APK proves the accepted consumer contract through packaged client artifacts, policy/security tests are deterministic, compatibility behavior is explicit, and applicable physical-device evidence supports the distribution claim.
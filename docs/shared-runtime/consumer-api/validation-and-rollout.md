# Consumer API validation and rollout

Status: active
Document type: feature-specification
Owner: shared-runtime-consumer-api-validation
Canonical scope: shared-runtime.consumer-api.validation-rollout
Read when: adding consumer API tests, compatibility/security review, reference-app validation or release gates
Last reviewed: 2026-09-05

## Goal

Prove that the public consumer API is smaller than the internal runtime surface, correctly policy-scoped, compatible across supported host/client versions and usable by a real external app without embedding Harness infrastructure.

This workstream extends—not replaces—the shared-runtime SR-5/SR-6 security, lifecycle and physical-device evidence.

## Validation layers

```text
contract/unit semantics
  -> host policy resolution with fakes
  -> Binder wire mapping/compatibility fixtures
  -> packaged client SDK consumer compilation
  -> independent-signer two-APK authorization/lifecycle behavior
  -> applicable physical Host/consumer/runtime evidence
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

The API must preserve the ADR 0017 trust boundary for independently signed consumers.

Review:

- `BIND_LOCAL_LLM` remains a normal capability permission and is never treated as an authorization grant;
- explicit component binding remains intact;
- application identity remains derived from Binder calling UID, exact installed package and signing certificate;
- source-observed independent consumers remain pending until explicit Harnex authorization;
- signing identity replacement fails closed until explicit reauthorization;
- capability discovery authenticates before returning policy information;
- model/preset selection is an allowlist selector, not arbitrary model control;
- request/session ownership remains authenticated-caller/client scoped;
- one client cannot enumerate or control another client's capability/session/request state;
- typed errors do not reveal private paths, signing data or unrelated model inventory;
- capability/result payload sizes are bounded;
- schema/input payloads retain transport validation limits;
- no diagnostics/control-plane or emulator-fault permission is implicitly granted by inference access.

Same-publisher consumers may retain a reviewed signing policy where intentional, but external-consumer release claims must use the signing topology actually distributed. Same-key evidence cannot establish independent-signer compatibility.

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

If external logical model choice invalidates the accepted invariant that the host alone selects one exact model for the use case, update the durable decision/compatibility boundary explicitly. Do not hide an incompatible semantic change behind a nullable parcel field.

## Compatibility matrix

Maintain at least:

| Client | Host | Expected |
| --- | --- | --- |
| Old client | New host | Existing fixed-use-case flow remains valid when the client declares a compatible binding permission and its caller identity remains authorized. |
| New client, defaults only | Old compatible host | Works only when required discovery/selection features and bind-permission contract are supported; otherwise typed incompatibility/unavailability. |
| New client using optional selection | Host supports feature | Capability -> prepare -> generate succeeds. |
| New client using unsupported feature | Older host | Fails before model preparation with typed incompatibility. |
| Compatible minor versions | Compatible peer | Common feature set is negotiated. |
| Incompatible major/semantic boundary | Other peer | Fail closed before preparation. |

Exact version numbers are assigned during implementation/release planning, not in this design plan.

## Reference consumer requirement

A reference consumer must remain a consumer, not a second Harness control plane. Product-specific PDF, PII, review, export and visual acceptance belongs to the consuming product; this source retains the generic SDK boundary.

Its success criteria:

- depends on packaged public client/contracts required for inference;
- contains no llama.cpp/JNI runtime;
- contains no GGUF model store/download/install pipeline;
- contains no independent runtime tuning engine;
- does not recreate Harness-wide health/cache/thermal/benchmark controls;
- discovers authorized use-case/output/default capabilities from the host;
- runs bounded input -> structured answer stream -> terminal result;
- shows only public metrics/request details;
- handles host unavailable, pending authorization, signer change, denied, incompatible and disconnected states clearly.

Any remaining consumer-local diagnostics must be justified as Consumer SDK diagnostics rather than duplicated Host control-plane behavior.

OMBRA intentionally uses deterministic defaults, requires structured output and does not surface model/preset alternatives or reasoning in its primary product UI. Generic optional-capability, alternate-selection, surfaced-reasoning and disallowed-selection scenarios remain covered by contract tests, Binder fixtures and the packaged `apps/shared-runtime-client-consumer-fixture`; they must not be added to OMBRA merely to satisfy protocol coverage.

## Generic reference UI acceptance

The packaged reference consumer should use progressive disclosure. Exact product screens and brand mapping remain consumer-owned.

### Primary

- connection state;
- authorization-required state when applicable;
- product-owned bounded input/task state;
- allowed model/preset selectors only when more than one useful choice exists;
- reasoning toggle only when surfaced reasoning is available;
- application-facing structured result or its product projection;
- Tier 1 metrics.

### Secondary request details

- effective logical model/preset;
- input/reasoning/answer tokens when available;
- stop reason;
- request/session/protocol IDs useful for debugging.

### Not present as normal consumer UI

- signing-certificate administration;
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
- reversible `disconnect()` versus terminal `close()` semantics documented and compatibility-tested;
- nullability/default semantics documented;
- consumer ProGuard/R8 rules validated.

A binary/API compatibility tool may be added if/when the client artifact becomes versioned for external consumption.

## Evidence scenarios

For independent-consumer integration, capture privacy-safe deterministic evidence for:

1. Host and consumer APK signing digests are distinct;
2. source-observed consumer identity begins pending/denied;
3. explicit Harnex authorization promotes the exact observed package/signer;
4. authorized connect -> disconnect -> reconnect succeeds with a reusable client;
5. unknown or mismatched signer remains denied;
6. capability discovery returns only the authorized use case;
7. cancellation and host process death/reconnect retain their typed semantics;
8. package/signer replacement fails closed until explicit reauthorization.

On the applicable physical device/model matrix, additionally capture runtime/model/performance evidence required by the release claim. Do not store prompt/reasoning/answer text or certificate material beyond privacy-safe digest identity in evidence archives.

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

- accepted target/boundary is reflected in ADR 0017 and the shared-runtime target/architecture;
- API reference documents the supported public surface and examples;
- compatibility policy identifies SDK/protocol/capability versioning;
- release notes bind host version, client SDK version, protocol version, Host/consumer signing digest identities and capability revision policy;
- reference consumer uses the packaged candidate artifact;
- independently signed distribution claims have exact-topology deterministic evidence and applicable Play/physical confirmation;
- supported model/preset claims point to applicable device evidence;
- private signing keys/certificates, model paths and prompt/output information remain excluded.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| CA-VAL-01 | PLANNED | Add deterministic capability-policy/default/allowlist tests. |
| CA-VAL-02 | PLANNED | Add reasoning/answer/result projection and metric-semantics tests. |
| CA-VAL-03 | PLANNED | Add protocol mapping/feature-negotiation compatibility fixtures. |
| CA-VAL-04 | PLANNED | Enforce packaged client public-surface dependency boundary. |
| CA-VAL-05 | PLANNED | Validate a pure/reference consumer and its connection/authorization UI states. |
| CA-VAL-06 | IN PROGRESS | Prove two-APK independent-signer authorization and reconnect behavior. |
| CA-VAL-07 | IN PROGRESS | Complete security/public-API/versioning review for ADR 0017. |
| CA-VAL-08 | PLANNED | Capture applicable physical evidence and close release gate. |

## Completion criteria

This workstream is complete only when a real external APK proves the accepted consumer contract through packaged client artifacts, policy/security tests are deterministic, compatibility behavior is explicit, the distributed signing topology is represented truthfully, and applicable physical-device evidence supports the release claim.

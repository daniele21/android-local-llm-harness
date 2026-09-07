# ADR 0018: Independently signed consumer authorization

- Status: Accepted
- Date: 2026-09-05
- Amended: 2026-09-07
- Supersedes: trust and exported-service permission portions of ADR 0012

## Context

ADR 0012 intentionally limited the first shared-runtime deployment to same-publisher, same-signing-lineage APKs protected by a signature-level Android permission. That assumption was useful for the initial Binder proof but does not match the general distributed product topology: Harnex must support consumer applications that may be signed and distributed independently.

A production-shaped physical-device test exposed the mismatch. The earlier emulator cross-APK evidence had signed Host and consumer with one ephemeral key, so it proved the same-signer design rather than the independently signed distribution topology.

The security properties that matter are not co-signing. Harnex must derive caller identity from Binder/Android, bind it to the exact installed package and signing certificate, map that identity to a Host-owned application/use-case policy, and fail closed when the identity is unknown or changes.

The first revision of this ADR replaced the signature permission with a custom `normal` `BIND_LOCAL_LLM` permission used only as a coarse bind-capability opt-in. Production-shaped API 35 cross-APK evidence then exposed a second distribution mismatch: when RedactGuard was installed before Harnex, Android did not retroactively grant the previously unknown custom permission after Harnex was installed. `checkSelfPermission` remained denied, so install order incorrectly determined whether an otherwise valid Consumer could even reach the Binder authorization boundary.

A custom permission that can permanently deny a Consumer solely because it was installed before the Host is not a valid public capability contract for independently distributed applications.

During the pre-release Internal Testing phase, the actual Harnex and RedactGuard Play installations were also observed to use the same current Play App Signing SHA-256 digest. That does not invalidate the signer-aware authorization design: it means only that this first-party pre-release pair is not yet physical evidence of the distinct-signer distribution case. The repository must distinguish runtime/security support for arbitrary signer identities from the release milestone at which a physical distinct-signer Play topology is required.

## Decision

### Public binding is install-order safe; authorization is Binder-owned

The public Harnex inference service is exported for explicit-component binding and does **not** require a custom Android bind permission. Consumers likewise do not request `USE_LOCAL_LLM` or `BIND_LOCAL_LLM`.

This is deliberate. Reachability of the Binder object is not authority to perform inference. Every privileged operation is authorized from Host-derived Android identity before model resolution, runtime preparation or expensive work.

The reusable service-host integration may still support an optional manifest/service permission for another Host that owns such a deployment constraint, but Harnex must not depend on one for the public Consumer boundary.

Because an unauthenticated application can bind to the exported service, pre-authorization behavior must remain cheap and side-effect bounded: binding/handshake does not create a second runtime, choose a model or load GGUF data, and unauthorized calls fail before expensive work.

### Binder caller identity is authoritative

Every privileged Binder operation remains fail-closed on Host-derived caller identity:

```text
Binder calling UID
  -> exact package resolved for that UID
  -> exact installed signing certificate
  -> persisted Harnex application authorization
  -> enabled Host-owned use-case binding
  -> runtime operation
```

Caller-supplied package names, application IDs, UIDs, signing digests, model IDs or policy claims never grant authority.

Ambiguous UID/package resolution, unknown packages, signer mismatch, disabled/pending applications, changed signer identity and unauthorized use cases are denied before model resolution or expensive work.

### Source-backed discovery and explicit user authorization

For a known consumer such as RedactGuard, Harnex observes the package and current signing certificate from Android `PackageManager`. Observation is not authorization.

A newly observed consumer is persisted as `PENDING`. The user must explicitly enable that exact observed application identity in the Harnex Control Plane before it enters the live Binder authorization policy.

If the installed signing identity later changes, reconciliation records the new source-backed identity as `SIGNATURE_CHANGED` and removes effective access until the user explicitly authorizes it again. Harnex never silently carries authorization across an unreviewed signer replacement.

Same-publisher built-ins or first-party applications may share a reviewed signing lineage where that is an intentional distribution choice. Sharing a signer is never used as the Binder authorization decision and does not remove exact package/signer/policy checks.

### Live policy is Control-Plane-owned

The live Binder policy is projected from authorized persisted Control Plane state: exact package, exact signer and enabled use-case bindings. Disabling the application removes it from the live policy without changing model ownership or consumer configuration.

Manual application registration, where supported, remains an explicit advanced Control Plane operation and does not weaken Binder-derived per-call verification.

### Consumer lifecycle remains independent from authorization

A consumer may explicitly connect, disconnect and reconnect. `disconnect()` releases the current Binder registration while keeping the Consumer SDK client reusable. Reconnect performs a fresh bind, protocol negotiation and caller authorization. Disconnect never grants, revokes or mutates Harnex Control Plane authorization.

### Emulator-only control remains separate

Test-only fault/control surfaces are not part of the public inference bind surface. The primary emulator fault receiver remains variant-scoped and protected by Harnex's separate signature-level test permission.

For independently signed cross-application CI, the `emulatorE2e` Host variant also exposes a distinct Host-process shell bridge protected by the platform `android.permission.DUMP` permission. The bridge accepts only the bounded emulator E2E action allowlist and invokes the same canonical test-only command handler as the signature-protected receiver. The production-shaped Consumer neither receives the signature test permission nor holds `DUMP`, and the bridge is absent from production variants.

Keeping this bridge in the Host process is intentional: shell control must not depend on a nested ordered-broadcast relay through the androidTest APK, whose result delivery can be blocked behind the outer ordered broadcast.

### Pre-release versus public-release signer evidence

The repository distinguishes three claims:

1. **Signer-aware authorization support.** This is a runtime/security property and must be proven deterministically with distinct Host and Consumer signing keys, including signer replacement denial.
2. **Current first-party Internal Testing topology.** Before the apps are publicly released, a stable repository promotion may use the actual current Play Internal signing topology, including a same-signer first-party pair, provided the exact topology is recorded truthfully and the physical install-order/authorization/runtime journey passes.
3. **Public distinct-signer distribution readiness.** Before the first public release that relies on independently signed applications, or before claiming physical qualification of the distinct-signer Play topology, the actual Play-distributed applications must be tested with distinct observed signing identities.

A pre-release `dev -> main` promotion therefore does not require changing Play signing identities solely to manufacture a distinct-signer physical topology before the product is publicly released. It does require FULL automated release validation, truthful recording of the actual Play signer topology, and deterministic distinct-signer authorization evidence.

## Consequences

- Harnex and consumer APKs can be independently signed, independently installed and distributed through separate signing identities.
- A first-party pre-release pair may temporarily share a signing identity without weakening Binder authorization semantics or blocking stable repository promotion.
- Consumer-before-Host and Host-before-Consumer installation orders converge on the same Binder authorization semantics.
- The Android manifest does not act as the authorization layer for the public inference service; exact Binder caller verification plus Harnex Control Plane policy is the security boundary.
- An arbitrary app may reach the exported Binder object, so every privileged entry point must authenticate before expensive work and the Host binding path must remain bounded.
- User authorization is explicit and reviewable, while package/signing identity is source-backed rather than manually asserted.
- Signing identity changes fail closed and require reauthorization.
- Deterministic cross-APK validation must use distinct Host and consumer signing keys, include Consumer-before-Host installation, and include an unauthorized-before-approval negative proof.
- Same-signer-only deterministic evidence is insufficient to prove signer-aware authorization support.
- Physical same-signer Internal Testing evidence is sufficient only for the explicitly recorded pre-release first-party topology; it is not evidence of physical distinct-signer Play readiness.
- Cross-signer emulator fault injection remains test-only: ordinary test control is signature-protected, while the separate Host-process CI bridge is `DUMP`-protected and allowlisted.

## Compatibility

The historical `USE_LOCAL_LLM` signature permission is not weakened or reinterpreted. The short-lived candidate `BIND_LOCAL_LLM` normal permission is removed rather than made part of the public contract because its grant semantics are install-order-sensitive for independently distributed applications.

Consumers bind by explicit Host package/service component and rely on Binder/Control Plane authorization. Existing Binder protocol, application/use-case/model authority and explicit component identity remain unchanged.

The Consumer Android SDK adds reversible `disconnect()` as an additive lifecycle API and versions that surface as `0.1.0-alpha.11`.

## Validation requirements

Deterministic evidence must prove at least:

- the public service is exported for explicit binding and has no custom bind permission;
- the Consumer does not require a custom Harnex permission;
- a Consumer installed before Harnex can reach the Binder boundary after Harnex installation;
- independently signed Host and consumer APKs have different certificate digests in the deterministic cross-signer lane;
- the independent consumer is denied by Binder/Control Plane authorization before explicit Harnex authorization;
- Harnex observes the exact installed consumer signer and persists it as pending;
- explicit authorization promotes that exact identity and enables its reviewed use case;
- the authorized consumer can connect, disconnect and reconnect without destroying the client;
- an unapproved replacement signer cannot inherit access;
- binding/handshake does not load a model or create a parallel runtime;
- production variants do not expose emulator-only control surfaces;
- cross-signer emulator control reaches only the `emulatorE2e` Host's bounded `DUMP`-protected shell bridge, while the ordinary fault receiver remains separately signature-protected.

For a pre-release stable repository promotion, physical Play Internal evidence must record the actual installed Harnex and Consumer signer identities and prove Consumer-first install, `PENDING`, exact authorization, reconnect behavior and representative production Binder/runtime use. A same-signer first-party pair is acceptable for this pre-release promotion only when recorded explicitly and must not be described as physical distinct-signer evidence.

Before the first public release that depends on independently signed application distribution, or before claiming physical distinct-signer Play qualification, rerun the focused physical Play journey with distinct observed Harnex and Consumer signing identities.

## Relationship to earlier ADRs

ADR 0012 remains authoritative for Host-owned application/use-case/model authority, Binder-derived caller context, protocol compatibility, payload/privacy boundaries and ordinary connection-scoped cleanup except where later ADRs supersede those topics. Its same-signer requirement and signature-level inference permission are superseded by this ADR.

ADR 0016 remains authoritative for explicitly durable Consumer jobs and transport-independent execution lifetime. Reattachment and durable-job access use the caller authorization model defined here.

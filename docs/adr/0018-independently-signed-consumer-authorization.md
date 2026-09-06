# ADR 0018: Independently signed consumer authorization

- Status: Accepted
- Date: 2026-09-05
- Amended: 2026-09-06
- Supersedes: trust and exported-service permission portions of ADR 0012

## Context

ADR 0012 intentionally limited the first shared-runtime deployment to same-publisher, same-signing-lineage APKs protected by a signature-level Android permission. That assumption was useful for the initial Binder proof but does not match the distributed product topology: Harnex and independently developed consumer applications such as RedactGuard are separate Play applications with independent Play App Signing identities.

A production-shaped physical-device test exposed the mismatch. The earlier emulator cross-APK evidence had signed Host and consumer with one ephemeral key, so it proved the same-signer design rather than the independently signed distribution topology.

The security properties that matter are not co-signing. Harnex must derive caller identity from Binder/Android, bind it to the exact installed package and signing certificate, map that identity to a Host-owned application/use-case policy, and fail closed when the identity is unknown or changes.

The first revision of this ADR replaced the signature permission with a custom `normal` `BIND_LOCAL_LLM` permission used only as a coarse bind-capability opt-in. Production-shaped API 35 cross-APK evidence then exposed a second distribution mismatch: when RedactGuard was installed before Harnex, Android did not retroactively grant the previously unknown custom permission after Harnex was installed. `checkSelfPermission` remained denied, so install order incorrectly determined whether an otherwise valid Consumer could even reach the Binder authorization boundary.

A custom permission that can permanently deny a Consumer solely because it was installed before the Host is not a valid public capability contract for independently distributed applications.

## Decision

### Public binding is install-order safe; authorization is Binder-owned

The public Harnex inference service is exported for explicit-component binding and does **not** require a custom Android bind permission. Consumers likewise do not request `USE_LOCAL_LLM` or `BIND_LOCAL_LLM`.

This is deliberate. Reachability of the Binder object is not authority to perform inference. Every privileged operation is authorized from Host-derived Android identity before model resolution, runtime preparation or expensive work.

The reusable service-host integration may still support an optional manifest/service permission for another Host that owns such a deployment constraint, but Harnex must not depend on one for the independently distributed public Consumer boundary.

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

For a known independently signed consumer such as RedactGuard, Harnex may observe the package and current signing certificate from Android `PackageManager`. Observation is not authorization.

A newly observed independent consumer is persisted as `PENDING`. The user must explicitly enable that exact observed application identity in the Harnex Control Plane before it enters the live Binder authorization policy.

If the installed signing identity later changes, reconciliation records the new source-backed identity as `SIGNATURE_CHANGED` and removes effective access until the user explicitly authorizes it again. Harnex never silently carries authorization across an unreviewed signer replacement.

Same-publisher built-ins may continue to use their reviewed Host signing lineage where that is an intentional product invariant.

### Live policy is Control-Plane-owned

The live Binder policy for independently signed consumers is projected from authorized persisted Control Plane state: exact package, exact signer and enabled use-case bindings. Disabling the application removes it from the live policy without changing model ownership or consumer configuration.

Manual application registration, where supported, remains an explicit advanced Control Plane operation and does not weaken Binder-derived per-call verification.

### Consumer lifecycle remains independent from authorization

A consumer may explicitly connect, disconnect and reconnect. `disconnect()` releases the current Binder registration while keeping the Consumer SDK client reusable. Reconnect performs a fresh bind, protocol negotiation and caller authorization. Disconnect never grants, revokes or mutates Harnex Control Plane authorization.

### Emulator-only control remains separate

Test-only fault/control surfaces are not part of the public inference bind surface. Emulator E2E controls remain variant-scoped and separately signature-protected so install-order-safe inference reachability does not broaden test-control authority.

## Consequences

- Harnex and consumer APKs can be independently signed, independently installed and distributed through separate Play App Signing identities.
- Consumer-before-Host and Host-before-Consumer installation orders converge on the same Binder authorization semantics.
- The Android manifest no longer pretends to be an authorization layer for the public inference service; exact Binder caller verification plus Harnex Control Plane policy is the security boundary.
- An arbitrary app may reach the exported Binder object, so every privileged entry point must authenticate before expensive work and the Host binding path must remain bounded.
- User authorization is explicit and reviewable, while package/signing identity is source-backed rather than manually asserted for known consumers.
- Signing identity changes fail closed and require reauthorization.
- Cross-APK validation must use distinct Host and consumer signing keys, include Consumer-before-Host installation, and include an unauthorized-before-approval negative proof.
- Same-signer-only or Host-first-only evidence is insufficient for production readiness of independently distributed consumers.

## Compatibility

The historical `USE_LOCAL_LLM` signature permission is not weakened or reinterpreted. The short-lived candidate `BIND_LOCAL_LLM` normal permission is removed rather than made part of the public contract because its grant semantics are install-order-sensitive for independently distributed applications.

Consumers bind by explicit Host package/service component and rely on Binder/Control Plane authorization. Existing Binder protocol, application/use-case/model authority and explicit component identity remain unchanged.

The Consumer Android SDK adds reversible `disconnect()` as an additive lifecycle API and versions that surface as `0.1.0-alpha.11`.

## Validation requirements

Deterministic evidence must prove at least:

- the public service is exported for explicit binding and has no custom bind permission;
- the Consumer does not require a custom Harnex permission;
- a Consumer installed before Harnex can reach the Binder boundary after Harnex installation;
- independently signed Host and consumer APKs have different certificate digests;
- the independent consumer is denied by Binder/Control Plane authorization before explicit Harnex authorization;
- Harnex observes the exact installed consumer signer and persists it as pending;
- explicit authorization promotes that exact identity and enables its reviewed use case;
- the authorized consumer can connect, disconnect and reconnect without destroying the client;
- an unapproved replacement signer cannot inherit access;
- binding/handshake does not load a model or create a parallel runtime;
- emulator-only control surfaces remain absent from production variants or separately protected.

Physical Play Internal testing remains required before a stable promotion claim because Play App Signing identity is the real distribution environment that exposed the original assumption gap.

## Relationship to earlier ADRs

ADR 0012 remains authoritative for Host-owned application/use-case/model authority, Binder-derived caller context, protocol compatibility, payload/privacy boundaries and ordinary connection-scoped cleanup except where later ADRs supersede those topics. Its same-signer requirement and signature-level inference permission are superseded by this ADR.

ADR 0016 remains authoritative for explicitly durable Consumer jobs and transport-independent execution lifetime. Reattachment and durable-job access use the caller authorization model defined here.

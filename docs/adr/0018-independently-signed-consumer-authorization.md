# ADR 0018: Independently signed consumer authorization

- Status: Accepted
- Date: 2026-09-05
- Supersedes: trust and exported-service permission portions of ADR 0012

## Context

ADR 0012 intentionally limited the first shared-runtime deployment to same-publisher, same-signing-lineage APKs protected by a signature-level Android permission. That assumption was useful for the initial Binder proof but does not match the distributed product topology: Harnex and independently developed consumer applications such as RedactGuard are separate Play applications with independent Play App Signing identities.

A production-shaped physical-device test exposed the mismatch. The earlier emulator cross-APK evidence had signed Host and consumer with one ephemeral key, so it proved the same-signer design rather than the independently signed distribution topology.

The security properties that matter are not co-signing. Harnex must derive caller identity from Binder/Android, bind it to the exact installed package and signing certificate, map that identity to a Host-owned application/use-case policy, and fail closed when the identity is unknown or changes.

## Decision

### Binding capability is not authorization

The exported inference service uses a variant-specific `normal` permission as a coarse, explicit opt-in to the Binder surface:

- release: `io.github.daniele21.localllm.permission.BIND_LOCAL_LLM`;
- debug: `io.github.daniele21.localllm.debug.permission.BIND_LOCAL_LLM`.

Possessing this permission does not authorize inference. The permission only allows an application that deliberately declares the capability to reach the service binding boundary.

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

Test-only fault/control surfaces are not protected by the normal inference bind permission. Emulator E2E controls remain variant-scoped and separately signature-protected so production binding semantics do not broaden test-control authority.

## Consequences

- Harnex and consumer APKs can be independently signed and distributed through separate Play App Signing identities.
- The Android manifest permission is no longer the security boundary; exact Binder caller verification plus Harnex Control Plane policy is.
- User authorization is explicit and reviewable, while package/signing identity is source-backed rather than manually asserted for known consumers.
- Signing identity changes fail closed and require reauthorization.
- Cross-APK validation must use distinct Host and consumer signing keys and must include an unauthorized-before-approval negative proof.
- Same-signer-only evidence is insufficient for production readiness of independently distributed consumers.

## Compatibility

This changes the exported-service capability permission name instead of weakening the protection level of the historical `USE_LOCAL_LLM` permission in place. Consumers move to `BIND_LOCAL_LLM`; the old permission name is not reinterpreted with broader semantics.

The Binder protocol and application/use-case/model authority remain unchanged. The Consumer Android SDK adds reversible `disconnect()` as an additive lifecycle API and versions that surface as `0.1.0-alpha.11`.

## Validation requirements

Deterministic evidence must prove at least:

- the service manifest uses the `BIND_LOCAL_LLM` normal capability permission;
- independently signed Host and consumer APKs have different certificate digests;
- the independent consumer is denied before explicit Harnex authorization;
- Harnex observes the exact installed consumer signer and persists it as pending;
- explicit authorization promotes that exact identity and enables its reviewed use case;
- the authorized consumer can connect, disconnect and reconnect without destroying the client;
- an unapproved signer/identity cannot inherit access;
- emulator-only control surfaces remain absent from production variants or separately protected.

Physical Play Internal testing remains required before a stable promotion claim because Play App Signing identity is the real distribution environment that exposed the original assumption gap.

## Relationship to earlier ADRs

ADR 0012 remains authoritative for Host-owned application/use-case/model authority, Binder-derived caller context, protocol compatibility, payload/privacy boundaries and ordinary connection-scoped cleanup except where later ADRs supersede those topics. Its same-signer requirement and signature-level inference permission are superseded by this ADR.

ADR 0016 remains authoritative for explicitly durable Consumer jobs and transport-independent execution lifetime. Reattachment and durable-job access use the caller authorization model defined here.
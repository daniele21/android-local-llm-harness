# Shared Android runtime target

Status: active
Document type: target-specification
Owner: shared-runtime
Canonical scope: shared-runtime.target
Read when: deciding the shared-runtime product boundary, trust model, user flow, goals or non-goals
Last reviewed: 2026-09-05

## Problem

An Android application should be able to request local LLM generation from a separately installed host application under a small, stable input/output contract. The client must not embed `llama.cpp`, copy a GGUF into its sandbox or understand model installation and runtime policy.

The host centralizes model bytes and RAM residency while preserving the repository's explicit application/use-case binding, single-decode default, privacy rules and backend-neutral public lifecycle.

The distributed product cannot require Host and consumer APKs to share one signing key. Harnex and independently developed consumers may use distinct Play App Signing identities, so caller trust must be derived from Binder/Android identity and explicit Harnex authorization rather than co-signing.

## Actors

| Actor | Responsibility |
| --- | --- |
| Host application | Installs and selects reviewed models, owns runtime state, application authorization and the protected service. |
| Client application | Declares the bind capability, connects, chooses an authorized use case, submits bounded input and consumes typed events. |
| Host operator/user | Installs/selects models and explicitly controls whether an observed consumer identity may use Harnex. |
| Android platform | Isolates processes, supplies Binder caller identity/package/signing information and carries Binder transactions. |

## Target user flow

```text
install Harnex and an independently signed consumer
  -> Harnex observes the consumer package + current signing certificate
  -> consumer remains pending until the user explicitly authorizes it in Harnex
  -> user installs/selects a curated model in the host
  -> client binds with an explicit ComponentName and BIND_LOCAL_LLM capability permission
  -> host verifies Binder UID -> exact package -> signer -> Control Plane authorization
  -> host verifies protocol compatibility and requested use case
  -> client prepares an allowlisted use case
  -> host resolves caller ApplicationId + UseCaseId to one exact model profile
  -> client opens a session and submits bounded input
  -> host streams queued/prepared/started/delta/terminal events
  -> client disconnects/reconnects or closes explicitly
  -> caller death or connection cleanup releases caller-owned connection-scoped resources
```

Model absence, pending/disabled authorization, signer replacement, incompatible protocol and service disconnection are normal typed outcomes. The host never downloads or silently selects a model because a client requested generation.

## Product contract

ADR 0017 defines the current trust boundary. Host and consumer APKs may be independently signed. The exported service uses the variant-specific `BIND_LOCAL_LLM` normal permission only as an explicit binding capability; actual authority is revalidated inside the Binder service from Android-derived caller identity and Harnex Control Plane policy.

The client may provide:

- one client correlation identifier;
- an allowlisted `UseCaseId`;
- session options supported by the host;
- text, message or explicitly allowed raw-completion input;
- bounded generation overrides already represented by core contracts;
- an output constraint supported by the resolved use case;
- connect/disconnect/cancellation/close intent.

The client may not provide trusted package, `ApplicationId`, UID, signing certificate, model identity or authorization claims.

The host provides:

- protocol and capability information;
- preparation and session outcomes;
- ordered generation lifecycle events;
- answer/reasoning deltas with content type;
- privacy-safe effective configuration and terminal metrics;
- stable error codes without backend, filesystem or signing-certificate disclosure.

The host derives application identity from the verified Binder caller. Known independent consumers are source-observed from Android and require explicit user authorization. Signing identity changes fail closed and require reauthorization. The host owns internal session/request identifiers and maps external correlation identifiers without allowing cross-client collision or control.

## Goals

- Reuse the existing runtime data plane without duplicating generation policy.
- Deduplicate installed model bytes and loaded model memory across authorized clients.
- Support independently signed Android consumers without sharing Harnex signing credentials.
- Preserve exact Binder UID/package/signer verification and explicit Harnex application authorization.
- Preserve explicit `ApplicationId + UseCaseId` resolution and exact artifact identity.
- Make streaming, cancellation, terminal outcomes and cleanup deterministic across transport/process changes.
- Publish a client artifact that hides generated Binder details from consuming application code.
- Version the Binder protocol independently from SDK, model, profile and backend identities.
- Keep prompts and generated content out of normal telemetry and evidence.
- Keep user-facing connection intent consumer-owned while authorization remains Harnex-owned.
- Support the phone-test Host, Console and RedactGuard as real proof consumers for their intended trust modes.

## Non-goals

- Treating `BIND_LOCAL_LLM` possession as authorization.
- Trusting caller-supplied package/signing/application identity.
- Automatically authorizing an app merely because it is installed or known by package name.
- Silently carrying authorization across an unreviewed signing identity change.
- Client-controlled model download, installation, selection, removal or arbitrary GGUF import.
- Sharing private file paths, model bytes, native handles or Room databases.
- Cross-device or network inference.
- A generic always-on foreground-service background execution product.
- Folding emulator fault controls or diagnostics/control-plane administration into the inference bind permission.
- Changing core lifecycle contracts to Android parcelables.

ADR 0016 separately allows explicit durable logical jobs to outlive transient Binder transport loss; ordinary connection-scoped operations keep their published cleanup semantics.

## Required trust decisions

The accepted architecture resolves:

1. **Trust:** exact Binder UID -> installed package -> signing certificate -> Harnex Control Plane authorization; no co-signing requirement for independent consumers.
2. **Binding capability:** `BIND_LOCAL_LLM` is a normal manifest permission and not a security grant.
3. **Authorization UX:** observation is `PENDING`; signer replacement is `SIGNATURE_CHANGED`; both require explicit Harnex approval before live access.
4. **Host identity:** `apps/local-llm-phone-test` is the proof Host, not automatically the final distributed Host product.
5. **API ownership:** generated AIDL is an internal transport detail; the Consumer AAR is the supported consumer surface.
6. **Compatibility:** protocol major/minor and feature negotiation fail closed before model preparation.
7. **Model control:** the client chooses an authorized use case, while the Host owns exact model binding and readiness.
8. **Diagnostics:** inference, emulator fault controls and broader diagnostics remain separate capabilities/permissions.

Changes to these assumptions require an ADR update before dependent implementation changes.

## Entry conditions

Implementation may proceed when:

- ADR 0010, ADR 0011 and ADR 0017 remain satisfied;
- the intended `dev` base is green and synchronized according to `BRANCHING.md`;
- no competing PR owns the same transport or service responsibility;
- the selected slice does not claim consumer or production readiness without the required cross-app evidence.

Consumer distribution additionally requires the applicable physical Qwen3.5 runtime, cancellation, memory, JNI and thermal evidence where those dimensions are material to the claim.

## Product-level success

The shared runtime succeeds when separately installed, independently signed Host and authorized consumer APKs can complete:

```text
observe pending identity -> explicit authorize -> bind -> negotiate -> prepare -> open session -> stream -> complete -> disconnect -> reconnect
```

and can safely recover or fail closed for:

```text
pending authorization
disabled authorization
signing identity replacement
cancel queued/running request
client process death
host process death
protocol mismatch
model unavailable
unauthorized use case
two concurrent clients
```

Success includes no client access to model storage, no cross-client resource control, no prompt/output persistence and reviewable evidence for the exact Host/client/protocol/runtime identity.

## Release boundary

Publishing the Consumer AAR or presenting Harnex as application-consumable requires:

- security and public API review;
- compatibility policy and consumer sample/evidence;
- deterministic cross-APK evidence using distinct Host and consumer signing identities for independent-consumer claims;
- an unauthorized-before-approval negative proof;
- exact release notes for Host, Consumer SDK and protocol versions;
- physical Play Internal confirmation before stable promotion when Play App Signing identity is material.

Same-key emulator evidence is not sufficient to prove independently signed distribution compatibility.
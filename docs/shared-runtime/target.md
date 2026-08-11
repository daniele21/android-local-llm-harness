# Shared Android runtime target

Status: active
Document type: target-specification
Owner: shared-runtime
Canonical scope: shared-runtime.target
Read when: deciding the shared-runtime product boundary, trust model, user flow, goals or non-goals
Last reviewed: 2026-08-11

## Problem

An Android application should be able to request local LLM generation from a separately installed host application under a small, stable input/output contract. The client must not embed `llama.cpp`, copy a GGUF into its sandbox or understand model installation and runtime policy.

The host centralizes model bytes and RAM residency while preserving the repository's explicit application/use-case binding, single-decode default, privacy rules and backend-neutral public lifecycle.

## Actors

| Actor | Responsibility |
| --- | --- |
| Host application | Installs and selects reviewed models, owns runtime state and exposes the protected service. |
| Client application | Connects, chooses an authorized use case, submits bounded input and consumes typed events. |
| Host operator/user | Installs or selects a model and controls whether the host is available. |
| Android platform | Isolates processes, authenticates signature permission and carries Binder transactions. |

## Target user flow

```text
install same-signer host and client
  -> user installs/selects a curated model in the host
  -> client binds with an explicit ComponentName
  -> host verifies caller identity and protocol compatibility
  -> client prepares an allowlisted use case
  -> host resolves caller ApplicationId + UseCaseId to one exact model profile
  -> client opens a session and submits bounded input
  -> host streams queued/prepared/started/delta/terminal events
  -> client cancels or closes explicitly
  -> caller death or disconnect releases caller-owned resources
```

Model absence, authorization denial, incompatible protocol and service disconnection are normal typed outcomes. The host never downloads or silently selects a model because a client requested generation.

## V1 product contract

V1 targets two APKs controlled by the same publisher and signed by an accepted signing lineage. Access is protected at the manifest boundary and revalidated inside the service.

The client may provide:

- one client correlation identifier;
- an allowlisted `UseCaseId`;
- session options supported by the host;
- text, message or explicitly allowed raw-completion input;
- bounded generation overrides already represented by core contracts;
- an output constraint supported by the resolved use case;
- cancellation and close intent.

The host provides:

- protocol and capability information;
- preparation and session outcomes;
- ordered generation lifecycle events;
- answer/reasoning deltas with content type;
- privacy-safe effective configuration and terminal metrics;
- stable error codes without backend or filesystem disclosure.

The host derives application identity from the verified caller. It owns internal session/request identifiers and maps external correlation identifiers without allowing cross-client collision or control.

## Goals

- Reuse the existing runtime data plane without duplicating generation policy.
- Deduplicate installed model bytes and loaded model memory across authorized clients.
- Preserve explicit `ApplicationId + UseCaseId` resolution and exact artifact identity.
- Make streaming, cancellation, terminal outcomes and cleanup deterministic across process death.
- Publish a client artifact that hides generated Binder details from consuming application code.
- Version the Binder protocol independently from SDK, model, profile and backend identities.
- Keep prompts and generated content out of normal telemetry and evidence.
- Support the existing phone-test host and console client as the first real proof.

## Non-goals for v1

- Access by arbitrary third-party publishers.
- User-granted runtime permission as a substitute for caller authentication.
- Client-controlled model download, installation, selection, removal or arbitrary GGUF import.
- Sharing private file paths, model bytes, native handles or Room databases.
- Cross-device or network inference.
- Continuing generation after all clients disconnect or after client process death.
- A generic foreground-service background execution product.
- Simultaneous decode, GPU/Vulkan, embeddings, reranking, multimodal or remote fallback.
- Folding the separate diagnostics/control-plane bridge into the inference protocol.
- Changing core lifecycle contracts to Android parcelables.

## Required decisions before implementation

SR-0 must produce an accepted ADR that resolves:

1. **Trust:** same signing lineage is mandatory for v1; any third-party access requires a separate security/product decision.
2. **Host identity:** `apps/local-llm-phone-test` is the proof host, not automatically the final distributed host product.
3. **Lifecycle:** bound-only operation cancels and cleans work on disconnect; background continuation is deferred.
4. **API ownership:** generated AIDL is an internal transport detail; the client AAR is the supported consumer surface.
5. **Compatibility:** protocol major/minor and feature negotiation fail closed before model preparation.
6. **Model control:** the client chooses an authorized use case, while the host owns exact model binding and readiness.
7. **Diagnostics:** inference and cross-application diagnostics remain separate protocols and permissions.

If one of these assumptions is rejected, stop the dependent workstream and revise the target/ADR before implementing an alternative.

## Entry conditions

Implementation may begin experimentally when:

- ADR 0010 and ADR 0011 remain satisfied;
- SR-0 is accepted;
- the intended `dev` base is green and synchronized according to `BRANCHING.md`;
- no competing PR owns the same transport or service responsibility;
- the selected slice does not claim consumer or production readiness.

Consumer distribution additionally requires the applicable physical Qwen3.5 runtime, cancellation, memory, JNI and thermal evidence.

## Product-level success

V1 succeeds when two separately installed same-signer APKs can complete:

```text
bind -> negotiate -> prepare -> open session -> stream -> complete -> close
```

and can safely recover from:

```text
cancel queued/running request
client process death
host process death
protocol mismatch
model unavailable
unauthorized use case
two concurrent clients
```

Success includes no client access to model storage, no cross-client resource control, no prompt/output persistence and reviewable physical-device evidence for the exact host/client/protocol/runtime identity.

## Release boundary

An experimental Binder slice may merge without claiming distribution readiness. Publishing the client AAR or presenting the host as application-consumable requires:

- completed SR-5 and SR-6 gates;
- representative physical-device evidence for the supported Qwen3.5 artifacts and profiles;
- security and public API review;
- a compatibility policy and consumer sample;
- exact release notes for host, client SDK and protocol versions.

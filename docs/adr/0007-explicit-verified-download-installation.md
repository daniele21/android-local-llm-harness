# ADR 0007 — Explicit verified-download installation

- Status: Accepted
- Date: 2026-08-05

## Context

ADR 0005 separates remote model distribution from local inference. ADR 0006 establishes a secure transfer boundary that publishes verified bytes into an application-private holding area and returns an opaque `VerifiedDownloadHandle`.

A verified transfer is not yet an installed model. Passing the verified backing file directly to UI or runtime code would expose filesystem details, weaken ownership of the holding area and make it easy to couple download completion to model activation.

The installation step must also reconcile three independent sources of truth:

- the administrator-managed catalog release;
- the application-owned reviewed model profile;
- metadata inspected from the downloaded GGUF itself.

## Decision

Introduce a separate `models/model-install` boundary between verified transfer and `ModelStore` publication.

The installer:

1. accepts an opaque `VerifiedDownloadHandle`, catalog release, target and resolved application profile;
2. validates release availability, target authorization and exact profile/artifact correspondence before reading bytes;
3. requests a revalidated copy from `VerifiedDownloadAccess` into a controlled installation staging area;
4. performs metadata-only GGUF inspection through the backend-neutral `GgufArtifactInspector` contract;
5. validates inspected architecture and any quantization metadata available from the backend;
6. imports through the existing `ModelStore` contract;
7. verifies the installed content-addressed artifact after import;
8. removes the imported artifact when post-import verification fails or cannot be completed;
9. discards the verified holding artifact only after successful installation and only according to an explicit retention policy;
10. returns a path-free installed-model descriptor.

The first backend adapter is `LlamaCppGgufArtifactInspector`, which maps the existing `LlamaCppBridge.inspectGguf()` result into the neutral installation contract without forwarding backend messages or paths.

Installation does not:

- activate or alter an `AppModelBinding`;
- load a model or create a runtime context;
- start inference;
- persist prompts, generated output, signed URLs or backing paths;
- treat a catalog URL as model identity.

Model identity remains the SHA-256 digest.

## Consequences

### Positive

- verified transfer ownership remains encapsulated;
- download, installation, binding and runtime activation stay individually observable and testable;
- catalog metadata cannot bypass application-owned profile review;
- GGUF structure is inspected before final publication;
- failed post-import verification triggers deterministic rollback;
- UI integrations can expose explicit `verified`, `installing` and `installed` states without hidden side effects;
- alternative backends may provide their own metadata inspector without changing installation orchestration.

### Costs

- installation temporarily holds a staging copy in addition to the verified download and final store artifact;
- metadata inspection support is limited by what each backend exposes;
- the current installer is synchronous and must execute off the Android main thread;
- cancellation inside the existing synchronous `ModelStore.import()` operation is not introduced by this decision.

## Deferred decisions

This ADR does not define:

- durable persistence of the returned installed-model catalog/profile metadata;
- automatic application/use-case binding after installation;
- WorkManager or foreground-service orchestration;
- cancellation during the atomic `ModelStore.import()` call;
- UI composition for catalog selection and installation progress;
- physical-device evidence for remote download and installation.

Those are follow-up slices and must preserve the explicit boundaries recorded here.

## Alternatives considered

### Let the downloader import directly into `ModelStore`

Rejected because transfer success would become installation success, bypassing explicit GGUF inspection and application-profile reconciliation.

### Expose the verified backing `File`

Rejected because callers could retain, move, disclose or import bytes outside the controlled holding-area policy.

### Let the runtime prepare operation install missing models

Rejected because inference preparation must not perform network or storage mutation implicitly.

### Validate only SHA-256 and size

Rejected because immutable identity verifies the bytes but does not prove that the artifact metadata matches the reviewed architecture/profile expectations.

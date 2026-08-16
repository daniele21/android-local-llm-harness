# ADR 0014: Backend SPI dependency boundary

- Status: Accepted
- Date: 2026-08-16

## Context

`RuntimeOrchestrator` already consumes an `InferenceBackend` abstraction, but before RA-1 both the backend-neutral contract and the concrete `LlamaCppInferenceBackend` adapter lived in `core:runtime-core`, and that module declared a production dependency on `:backends:llama-cpp`. The conceptual abstraction therefore did not establish a real Gradle dependency boundary.

The backend contract also accepted `StoredModel`, coupling backend implementations to the model-store persistence representation even though integrity verification and storage ownership belong to runtime/model-store code.

The architecture-hardening target requires runtime policy to compile and test without a concrete inference implementation while preserving thin backend adapters and explicit model/resource ownership.

## Decision

### 1. `core:backend-spi` owns backend-neutral execution contracts

A dedicated `core:backend-spi` module owns the contracts used by runtime policy to initialize a backend, load/unload a verified model source, inspect backend capabilities, plan prompts, create/release contexts, generate/stream, cancel, and map backend-neutral outcomes/errors.

The first extraction intentionally keeps the existing Kotlin package/FQCNs to avoid combining dependency inversion with cosmetic API churn. Module ownership, not package renaming, is the boundary being established.

### 2. Runtime core depends on the SPI, never on a concrete backend

`core:runtime-core` may depend on `core:backend-spi` and continue to own model verification, sessions, scheduling, lifecycle, recovery policy and telemetry. It must not declare a production dependency on `backends:*`.

Concrete backend selection happens at composition roots such as applications or host integrations.

### 3. Backend implementations depend inward on the SPI

`backends:llama-cpp` implements the SPI and owns its Kotlin/JNI/C++ adaptation, opaque native handles and backend-specific error mapping. Native/backend structures do not cross the SPI.

A future backend must implement the same contract rather than adding backend-specific branches to runtime core.

### 4. Persistence representation does not cross the SPI

The SPI accepts a `BackendModelSource` containing the immutable digest, materialized file and size required for backend loading. It does not depend on `models:model-store` or accept `StoredModel` directly.

Runtime core adapts an already resolved and integrity-verified `StoredModel` to `BackendModelSource` immediately before backend loading. The backend does not decide storage validity, installation state or model selection.

### 5. Replaceability is proven with deterministic fakes and conformance tests

Runtime tests use deterministic `InferenceBackend` implementations without JNI/llama.cpp. RA-10 will expand this into a reusable conformance suite that every real backend must pass.

## Consequences

- Runtime policy can compile and test without the llama.cpp implementation in its dependency graph.
- Model-store ownership remains outside backend implementations.
- llama.cpp integration becomes a replaceable adapter rather than a runtime-core implementation detail.
- Composition roots become the explicit place where a concrete backend is selected.
- The SPI is a real module/reuse/testing boundary, satisfying the repository rule against speculative modules.
- A later package rename may improve naming, but is not required to preserve this dependency direction.

## Alternatives considered

### Keep the interface in `runtime-core` and only move the adapter

Rejected because runtime-core would remain the contract owner for a dependency it consumes and future backends would still depend on the orchestration module.

### Put backend contracts in `core:contracts`

Rejected because `core:contracts` is the public application/consumer contract layer. Backend lifecycle/context/generation handles are internal infrastructure contracts and should not enlarge the public API surface.

### Let the SPI accept `StoredModel`

Rejected because it leaks persistence representation into backend implementations and prevents model-store independence.

### Introduce a second production backend immediately

Rejected. Replaceability is first proven with deterministic fakes and architecture/conformance tests; a second backend is justified only by a concrete product requirement.

## Implementation gate

RA-1 is complete only when `runtime-core` has no production `backends:*` dependency, llama.cpp implements the SPI from its own module, deterministic runtime tests pass without llama.cpp, architecture fitness rules enforce the direction, and cumulative `dev` validation remains green.

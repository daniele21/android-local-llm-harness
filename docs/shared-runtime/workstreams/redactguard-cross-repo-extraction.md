# RedactGuard cross-repository extraction

Status: active
Document type: feature-specification
Owner: shared-runtime
Canonical scope: shared-runtime.redactguard-cross-repo-extraction
Read when: coordinating Harness-owned SDK, identity, security, Control Plane, validation or final cutover work for the external RedactGuard application
Last reviewed: 2026-08-23
Started: 2026-08-17

## Purpose

Own the Harness side of moving the OMBRA/RedactGuard product out of this repository while preserving the Harness as the runtime, model and Host Control Plane owner.

RedactGuard is the canonical owner of product-domain migration and product hardening. Harness owns only the shared-runtime boundary, public SDK, Host policy, activation/residency and the final removal of legacy in-repository product code after cross-repository proof.

## Target boundary

Harness remains responsible for:

- public Consumer API and Android SDK;
- Binder client/contracts and host service;
- exact package/UID/signer authorization and compatibility;
- Host Control Plane application/use-case/preset/binding state;
- host-owned `document-pii-detection` execution policy;
- model/GGUF/runtime/resource/residency ownership;
- generic packaged consumer fixture and shared-runtime evidence.

RedactGuard owns:

- PDF and pasted-text document lifecycle;
- PII definitions and product policy;
- analysis composition/validation above the public Consumer API;
- Review/redaction/export UX and logic;
- product failure/recovery projection;
- PII quality corpus and product-specific quality evidence.

Harness must not remain a build-time source dependency of RedactGuard after cutover.

## Invariants

- External consumers never resolve through the phone-global manually selected model.
- Consumer execution requires an explicit Host Control Plane activation binding.
- Harness-internal Playground/device validation may retain manual model selection.
- Activation/deactivation owns residency acquisition/release; Binder death releases all connection-owned activations.
- Caller authorization remains fail-closed before application/use-case dispatch.
- Activation wire contracts expose use-case/binding/preset revisions and an opaque activation ID, never model identity.
- `apps/local-llm-console` legacy product code remains until physical cross-repository proof is green.
- No CI/emulator result is promoted to physical or release evidence.

## Completed prerequisites

### HSDK-1 — Publishable Consumer Android SDK

State: DONE
Issue: #310

The external Consumer SDK is published through a token-free public Maven channel and RedactGuard consumes the SDK without a Harness source checkout. The current cutover slice advances the API to the next immutable alpha because Consumer Control Plane contracts are additive public ABI.

### HHOST-1 — RedactGuard identity/authorization

State: DONE
Issue: #311

Fixed identity:

```text
ApplicationId: redactguard
UseCaseId: document-pii-detection
release package: io.github.daniele21.redactguard
debug package: io.github.daniele21.redactguard.debug
```

Harness already authorizes only the intended RedactGuard package identities/use case under the existing signer boundary. Legacy Console compatibility remains temporary until HCUT-1.

## Active cutover DAG

| Slice | State | Depends on | Acceptance |
| --- | --- | --- | --- |
| RC-1 Clean Control Plane replay | ACTIVE | — | Replay the still-required Binder 1.2 discovery/activation/residency semantics from current `dev`; external consumers cannot fall back to the global selected model; focused wire/lifecycle/cutover tests are present. |
| RC-2 Exact-head software gate | BLOCKED | RC-1 | Repository health, documentation validation, Consumer SDK ABI validation, scoped Android tests/lint/build and packaging are green on one exact head. |
| RC-3 RedactGuard SDK adaptation | BLOCKED | RC-2 + published SDK | RedactGuard discovers assigned use cases/presets, activates before Consumer API prepare/session/generate, deactivates/cleans up deterministically and remains consumer-only. |
| RC-4 Cross-repository physical proof | BLOCKED | RC-3 | Independently built same-signer Harness Host + RedactGuard APKs pass activation, generation, cancellation, Host death/recovery and representative import -> analysis -> Review -> export/failure scenarios on real arm64 hardware with exact identities recorded. |
| RC-5 HCUT-1 legacy removal | BLOCKED | RC-4 | Remove in-repo OMBRA product source/aliases/duplicated product data/docs while retaining the generic Consumer fixture, public SDK/Binder/Host and host-owned `document-pii-detection` policy. |
| RC-6 Final reconciliation | BLOCKED | RC-5 | Durable shared-runtime/current-state/roadmap docs describe the final ownership boundary; superseded HCP branches/PRs and HCUT issue are reconciled after unique-commit audit. |

## RC-1 implementation scope

The old stacked HCP branches are evidence only, not merge lines. They diverged materially from current `dev`; the current cutover replays the required behavior onto the latest baseline and deliberately excludes one-shot CI trigger files.

RC-1 owns:

- backend-neutral `ConsumerControlPlaneClient` contracts;
- Binder protocol minor 1.2 feature gating and AIDL/wire mapping;
- Binder client discovery/activate/deactivate adapter;
- Host control-plane authorization and connection lifecycle;
- activation-residency acquisition/release and Binder-death cleanup;
- phone Host composition with persistent control-plane store;
- explicit activation binding in the runtime model-profile registry;
- removal of external selected-model fallback while preserving internal Harness manual selection;
- next immutable Consumer SDK alpha and generic release fixture coverage.

The old HCP branch had a known Spotless failure in `HarnessCatalogResolution.kt`; the clean replay uses the current formatter contract rather than preserving that stale exact-head failure.

## HCUT-1 removal boundary

Remove only after RC-4 is green:

- `apps/local-llm-console` OMBRA/RedactGuard product implementation;
- PDF/product-specific domain and UI dependencies from Harness;
- temporary legacy OMBRA/Console package/application authorization aliases;
- duplicated PII corpus/policy after RedactGuard is canonical;
- obsolete docs describing OMBRA as an in-repository application.

Keep:

- Consumer API/SDK;
- Binder host/client/contracts;
- generic packaged consumer fixture;
- Harness Host Control Plane;
- host-owned `document-pii-detection` binding/preset policy;
- runtime/model/GGUF/scheduler/residency/telemetry ownership;
- shared-runtime security/version/evidence gates.

## Physical evidence gate

Before legacy removal, record and prove:

1. exact Harness Host source/build/APK identity;
2. exact published Consumer SDK version and ABI identity;
3. exact RedactGuard source/build/APK identity;
4. same-signer authorization success and invalid identity denial;
5. Consumer Control Plane feature negotiation and assignment discovery;
6. preset discovery and explicit activation;
7. Consumer API prepare/session/generate/cancel under that activation;
8. deactivation plus Host-death/restart cleanup/recovery;
9. representative RedactGuard text/PDF analysis reaches validated Review;
10. accept/ignore decisions produce independently verified exported output;
11. representative classified failure/recovery evidence is identity-bearing and privacy-safe;
12. no Harness source path/project/composite dependency exists in the RedactGuard build.

## Parallelism

RedactGuard product hardening and unrelated LLRT/evaluation work may continue in parallel while write ownership is disjoint. Physical-device evidence is serialized where one representative device is shared. HCUT-1 must not run in parallel with the physical proof because the legacy compatibility surface is intentionally retained until that gate succeeds.

## Rollback rule

Until RC-4 is green, legacy OMBRA source remains a compatibility/reference surface only. Contract defects discovered by extraction are fixed and versioned in the Harness public SDK; RedactGuard-specific copies of transport/runtime contracts are not an acceptable workaround.

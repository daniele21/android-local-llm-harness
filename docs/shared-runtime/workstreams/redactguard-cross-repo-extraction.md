# RedactGuard cross-repository extraction

Status: active
Document type: feature-specification
Owner: shared-runtime
Canonical scope: shared-runtime.redactguard-cross-repo-extraction
Read when: coordinating Harness-owned SDK, identity, security, Control Plane, validation or final cutover work for the external RedactGuard application
Last reviewed: 2026-08-25
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

The external Consumer SDK is published through a token-free public Maven channel and RedactGuard consumes `io.github.daniele21.localllm:consumer-android:0.1.0-alpha.4` without a Harness source checkout. Alpha.4 contains the additive Consumer Control Plane contracts required by the cutover.

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
| RC-1 Clean Control Plane replay | DONE | — | Binder 1.2 discovery/activation/residency semantics are integrated; external consumers cannot fall back to the global selected model; focused wire/lifecycle/cutover tests are present. |
| RC-2 Exact-head software gate | DONE | RC-1 | Repository health, Consumer SDK ABI validation, scoped Android tests/lint/build and packaging have passed on the integrated Control Plane/candidate slices. |
| RC-3 RedactGuard SDK adaptation | DONE | RC-2 + published SDK | RedactGuard discovers assigned use cases/presets, activates before Consumer API prepare/session/generate, deactivates/cleans up deterministically and remains consumer-only. |
| RC-4 Cross-repository physical proof | ACTIVE | RC-3 | Independently built same-signer Harness Host + RedactGuard APKs must pass activation, generation, cancellation, Host death/recovery and representative import -> analysis -> Review -> export/failure scenarios on real arm64 hardware with exact identities recorded. |
| RC-5 HCUT-1 legacy removal | BLOCKED | RC-4 | Remove in-repo OMBRA product source/aliases/duplicated product data/docs while retaining the generic Consumer fixture, public SDK/Binder/Host and host-owned `document-pii-detection` policy. |
| RC-6 Final reconciliation | BLOCKED | RC-5 | Durable shared-runtime/current-state/roadmap docs describe the final ownership boundary; superseded HCP branches/PRs and HCUT issue are reconciled after unique-commit audit. |

## Current physical-proof checkpoint

Repository-side preparation for RC-4 is complete; the remaining acceptance boundary is real-device execution.

Frozen physical source identities and tooling:

- Harness: `9699cb0ae9bd6b49f68c07fa49c004360e8d7d92`, `versionCode=28`, `versionName=1.0.0`;
- the exact-candidate same-signer release APK helper was integrated by PR #443 and is present in that frozen source;
- the frozen Harness source includes the process-scoped Control Plane store and Applications control-plane gateway, and its push `Repository health`, `Validate` and `Package Android Artifacts` workflows are green;
- RedactGuard: `8ca1f50f0ca07c04bd19dbc3a870366f77f06689`, `versionCode=9`, `versionName=0.1.4`; its same-signer release APK helper and canonical physical runner are present at that source;
- Consumer SDK: `io.github.daniele21.localllm:consumer-android:0.1.0-alpha.4`;
- clean Host Control Plane seed: `document-pii-detection` with default preset `qwen35-json` revision `3`.

Later documentation-only descendants do not replace these frozen APK source identities. Build both release APKs from clean detached checkouts at the revisions above using the shared upload signing identity. Run RedactGuard's canonical E2E script from the same frozen RedactGuard checkout with `--release`, Harness source revision `9699cb0ae9bd6b49f68c07fa49c004360e8d7d92` and preset revision `3`; record APK SHA-256 and signer in the generated evidence.

## RC-1 implementation scope

The old stacked HCP branches are evidence only, not merge lines. The required behavior has been replayed onto current `dev` and integrated without retaining one-shot CI trigger files.

RC-1 owns:

- backend-neutral `ConsumerControlPlaneClient` contracts;
- Binder protocol minor 1.2 feature gating and AIDL/wire mapping;
- Binder client discovery/activate/deactivate adapter;
- Host control-plane authorization and connection lifecycle;
- activation-residency acquisition/release and Binder-death cleanup;
- phone Host composition with persistent process-scoped control-plane store;
- explicit activation binding in the runtime model-profile registry;
- removal of external selected-model fallback while preserving internal Harness manual selection;
- immutable Consumer SDK alpha.4 and generic release fixture coverage.

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

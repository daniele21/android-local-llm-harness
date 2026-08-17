# RedactGuard cross-repository extraction

Status: active
Document type: feature-specification
Owner: shared-runtime
Canonical scope: shared-runtime.redactguard-cross-repo-extraction
Read when: coordinating Harness-owned SDK, identity, security, validation or cutover work for the external RedactGuard application
Last reviewed: 2026-08-17
Started: 2026-08-17

## Purpose

Track the Harness-owned side of moving the OMBRA product out of this repository into the independent `daniele21/redactguard-android` repository.

The canonical end-to-end migration/dependency plan is owned by RedactGuard at:

`docs/workstreams/ombra-to-redactguard-migration.md`

This document intentionally contains only Harness-owned responsibilities and cross-repository gates so the platform repository does not become a second owner of the product migration plan.

## Target boundary

Harness remains responsible for:

- the public Consumer API;
- Android Binder client/contracts and host service;
- exact caller authorization and compatibility;
- host-owned `document-pii-detection` model/preset policy;
- model/runtime/resource ownership;
- generic packaged consumer fixture and shared-runtime evidence.

RedactGuard becomes responsible for:

- PDF document lifecycle;
- PII definitions and product policy;
- analysis composition/validation above the public Consumer API;
- Review/redaction/export UX and logic;
- PII quality corpus and product-specific quality evidence.

Harness must not remain a build-time source dependency of RedactGuard after cutover.

## Active Harness tasks

### HSDK-1 — Publishable Consumer Android SDK

Issue: #310
State: TODO
Critical path: yes
Parallel with: HHOST-1 and all pre-integration RedactGuard migration work

Exit condition: an external Gradle project can consume the versioned SDK artifact without a Harness source checkout/composite build and the packaged consumer fixture proves bind/prepare/session/generate/cancel from the artifact.

### HHOST-1 — RedactGuard identity/authorization

Issue: #311
State: TODO
Critical path: yes
Parallel with: HSDK-1 and RedactGuard migration

Fixed target identity:

```text
ApplicationId: redactguard
UseCaseId: document-pii-detection
release package: io.github.daniele21.redactguard
debug package: io.github.daniele21.redactguard.debug
```

Exit condition: exact package/application/use-case/signer policy authorizes the intended same-publisher app and fails closed for invalid identities.

### HCUT-1 — Remove in-repo OMBRA product after external proof

State: BLOCKED
Depends on: cross-repository RedactGuard debug physical smoke; final release cleanup should also wait for release-like physical evidence

Remove only after external proof:

- `apps/local-llm-console` OMBRA product implementation;
- OMBRA PDF/product domain and UI dependencies;
- legacy OMBRA/Console package/application authorization entries;
- product-specific corpus/policy once RedactGuard is the canonical owner;
- obsolete docs describing OMBRA as an in-repo application.

Keep:

- Consumer API/SDK;
- Binder host/client/contracts;
- generic consumer fixture;
- host-owned `document-pii-detection` binding;
- shared-runtime security/version/evidence gates.

## Parallel execution

Immediate Harness fan-out:

```text
HSDK-1  -------------------------+
                                   +--> external RedactGuard SDK integration --> cross-repo E2E
HHOST-1 --------------------------+

existing runtime/memory/quality work may continue independently
```

Do not serialize SDK packaging behind RedactGuard PDF/UI/domain migration. Do not serialize RedactGuard domain/PDF/UI migration behind SDK packaging; those streams use fake/application ports until convergence.

## Cross-repository gate

Before this repository may delete the legacy OMBRA implementation, prove all of the following with two independently built repository artifacts:

1. Harness Host APK built from an exact Harness revision;
2. exact published Consumer SDK identity recorded;
3. RedactGuard APK built from an exact RedactGuard revision and resolved SDK artifact;
4. same-signer package authorization succeeds;
5. Binder capability discovery and `document-pii-detection` prepare succeed;
6. synthetic PDF analysis reaches validated Review findings;
7. accept/ignore decisions generate a new PDF;
8. independent output assertions succeed;
9. cancellation/disconnect/recovery paths required by the release gate remain intact;
10. no Harness source path/project/composite dependency is present in the RedactGuard build.

## Rollback rule

Until the external cross-repository smoke is green, the current OMBRA implementation remains the known-good behavioral reference. Contract defects discovered by the extraction are fixed and versioned in the Harness public SDK; RedactGuard-specific copies of transport/contracts are not an acceptable workaround.

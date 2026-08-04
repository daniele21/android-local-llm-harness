# Model catalog and secure download progress

**Canonical plan:** [`model-catalog-download-plan.md`](model-catalog-download-plan.md)  
**Base:** `main` at `dfba2a05ed8166ef79a12261089078e13fd3902e`  
**Branch:** `agent/model-catalog-download-implementation`  
**Last updated:** 2026-08-04

## Status legend

- `[ ]` not started
- `[-]` in progress or awaiting validation
- `[x]` implemented and validated
- `[!]` blocked or awaiting an architectural/product decision

## Overall progress

| Workstream | Status | Current evidence |
|---|---:|---|
| Main audit and branch control | `[x]` | Fresh branch created from current `main`; historical branches excluded as dependencies |
| Architecture decision | `[-]` | ADR 0005 added with Proposed status; CI/review pending |
| Catalog domain contracts | `[-]` | `models/model-catalog` and public domain types added; CI pending |
| Catalog validation | `[-]` | Fail-closed document and release validation added; CI pending |
| Target filtering | `[-]` | Exact application/use-case filtering added; CI pending |
| Compatibility evaluation | `[-]` | API, ABI, backend, profile, RAM and double-staging storage checks added; CI pending |
| Catalog parsing/codec | `[ ]` | No JSON or signed-manifest codec yet |
| Catalog persistence and refresh | `[ ]` | Not started |
| Secure model download | `[ ]` | Not started |
| GGUF installation orchestration | `[ ]` | Not started |
| Installed release metadata | `[ ]` | Not started |
| Phone-app integration | `[ ]` | Not started |
| Update/deprecation/revocation | `[ ]` | Not started |
| Health and repair | `[ ]` | Not started |
| Manifest signing | `[ ]` | Not started |
| Physical-device evidence | `[ ]` | Not started |

## Phase 0 — main audit and ownership

- [x] use current `main` as the only implementation base
- [x] audit current roadmap and merged pull requests
- [x] audit `ModelStore` and `FileSystemModelStore`
- [x] identify `apps/local-llm-phone-test` as the first connected surface
- [x] identify PR #34 and PR #40 as unmerged overlap only
- [x] create `agent/model-catalog-download-implementation`
- [x] rewrite the implementation plan against `main`
- [-] add ADR 0005 and obtain validation/review

## Phase 1 — catalog domain and policy

- [-] register `:models:model-catalog`
- [-] define catalog, release, artifact, target, license and availability models
- [-] define application-owned profile resolver boundary
- [-] validate schema, IDs, time window, entry count and release uniqueness
- [-] validate SHA-256, positive size, HTTPS URI and safe GGUF file name
- [-] validate compatibility, targets and license links
- [-] detect conflicting metadata for the same digest
- [-] filter releases by exact application and use case
- [-] evaluate availability, API, ABI, backend, Harness version and profile support
- [-] evaluate minimum/recommended RAM
- [-] calculate storage using download/import copies plus safety margin
- [-] add deterministic validator and compatibility tests
- [ ] validate with repository CI
- [ ] document the public API after CI stabilizes

## Commits

| Commit | Change | Validation |
|---|---|---|
| `639a9d4f99da` | Main-based implementation plan | Source review complete |
| `a906b5b3dde4` | Add model-catalog module | CI pending |
| `15b7121e6730` | Register module in settings | CI pending |
| `0a69d294315f` | Add catalog domain contracts | CI pending |
| `74173e1c275e` | Add fail-closed catalog validation | CI pending |
| `9e8fe0b6fa26` | Add target filtering and compatibility evaluation | CI pending |
| `4db85c723cfb` | Add test fixtures | CI pending |
| `df61f7fba777` | Add validator tests | CI pending |
| `ff00a710166d` | Add compatibility tests | CI pending |
| `e336989d3eea` | Add ADR 0005 | CI pending |
| `2fa827594cbc` | Index ADR 0005 | CI pending |

## Next implementation slice

After the current module is green:

1. add a bounded catalog codec contract and deterministic JSON implementation;
2. add app-private atomic catalog persistence;
3. implement revision, expiry and stale-cache behavior;
4. add a fake catalog source and synchronization service;
5. only then introduce the HTTPS download module.

## Validation gate

The current slice is not complete until all relevant checks pass:

- [ ] repository navigation and artifact guards
- [ ] Spotless and ktlint
- [ ] Detekt
- [ ] `:models:model-catalog:testDebugUnitTest`
- [ ] Android Lint for `model-catalog`
- [ ] downstream application compilation
- [ ] aggregate required `Repository validation` check

No task marked `[-]` may be changed to `[x]` solely because code exists.

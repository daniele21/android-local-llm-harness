# Model catalog and secure download progress

**Canonical plan:** [`model-catalog-download-plan.md`](model-catalog-download-plan.md)

**Base:** `main` at `dfba2a05ed8166ef79a12261089078e13fd3902e`

**Branch:** `agent/model-catalog-download-implementation`

**Pull request:** #41

**Last updated:** 2026-08-04

## Status legend

- `[ ]` not started
- `[-]` in progress or awaiting validation
- `[x]` implemented and validated
- `[!]` blocked or awaiting an architectural or product decision

## Overall progress

| Workstream | Status | Current evidence |
|---|---:|---|
| Main audit and branch control | `[x]` | Fresh branch created from current `main`; historical branches excluded as dependencies |
| Architecture decision | `[-]` | ADR 0005 added with Proposed status; CI and review pending |
| Catalog domain contracts | `[-]` | `models/model-catalog` and public domain types added; CI pending |
| Catalog validation | `[-]` | Fail-closed validation implemented and decomposed to satisfy complexity limits; CI pending |
| Target filtering | `[-]` | Exact application/use-case filtering added; CI pending |
| Compatibility evaluation | `[-]` | API, ABI, backend, profile, RAM and double-staging storage checks added; CI pending |
| Catalog parsing and codec | `[ ]` | No JSON or signed-manifest codec yet |
| Catalog persistence and refresh | `[ ]` | Not started |
| Secure model download | `[ ]` | Not started |
| GGUF installation orchestration | `[ ]` | Not started |
| Installed release metadata | `[ ]` | Not started |
| Phone-app integration | `[ ]` | Not started |
| Update, deprecation and revocation | `[ ]` | Not started |
| Health and repair | `[ ]` | Not started |
| Manifest signing | `[ ]` | Not started |
| Physical-device evidence | `[ ]` | Not started |

## Phase 0 — Main audit and ownership

- [x] use current `main` as the only implementation base
- [x] audit current roadmap and merged pull requests
- [x] audit `ModelStore` and `FileSystemModelStore`
- [x] identify `apps/local-llm-phone-test` as the first connected surface
- [x] identify PR #34 and PR #40 as unmerged overlap only
- [x] create `agent/model-catalog-download-implementation`
- [x] rewrite the implementation plan against `main`
- [-] add ADR 0005 and obtain validation and review

## Phase 1 — Catalog domain and policy

- [-] register `:models:model-catalog`
- [-] define catalog, release, artifact, target, license and availability models
- [-] define application-owned profile resolver boundary
- [-] validate schema, IDs, time window, entry count and release uniqueness
- [-] validate SHA-256, positive size, HTTPS URI and safe GGUF file name
- [-] validate compatibility, targets and license links
- [-] detect conflicting metadata for the same digest
- [-] filter releases by exact application and use case
- [-] evaluate availability, API, ABI, backend, Harness version and profile support
- [-] evaluate minimum and recommended RAM
- [-] calculate storage using download and import copies plus safety margin
- [-] add deterministic validator and compatibility tests
- [ ] validate with repository CI
- [ ] document the public API after CI stabilizes

## Validation history

| Run | Result | Finding or evidence |
|---|---:|---|
| `30952159115` | failed | Agent navigation required the new module in `AGENTS.md` |
| `30952241203` | failed | Kotlin formatting issues detected |
| `30953021399` | failed | CI matrix updated to execute catalog tests and lint; remaining formatting issues found |
| `30953242237` | diagnostic | Exact Spotless patch captured; mixed RAM condition identified |
| `30953487818` | diagnostic | Exact formatter output captured after condition fix |
| `30953791509` | failed | Kotlin passed; remaining failure limited to Markdown trailing whitespace |
| `30954312737` | failed | Markdown and native checks passed; Detekt found one long test fixture and two compatibility-validation complexity findings |
| `30954629742` | failed | Detekt refactor submitted; Spotless required one single-line fixture signature before analysis could continue |
| current | running | Exact fixture format applied and tracker updated to trigger validation on the latest branch state |

## Key commits

| Commit | Change | Validation |
|---|---|---|
| `639a9d4f99da` | Main-based implementation plan | Replaced by consolidated plan |
| `a906b5b3dde4` | Add model-catalog module | CI pending |
| `15b7121e6730` | Register module in settings | CI pending |
| `0a69d294315f` | Add catalog domain contracts | CI pending |
| `74173e1c275e` | Add fail-closed catalog validation | Refactored by later commits |
| `9e8fe0b6fa26` | Add target filtering and compatibility evaluation | CI pending |
| `4db85c723cfb` | Add test fixtures | Refactored by later commits |
| `df61f7fba777` | Add validator tests | CI pending |
| `ff00a710166d` | Add compatibility tests | CI pending |
| `e336989d3eea` | Add ADR 0005 | CI pending |
| `2fa827594cbc` | Index ADR 0005 | CI pending |
| `0fce18d1cfec` | Map module in `AGENTS.md` | Navigation guard passed |
| `6273639690b0` | Include catalog module in CI matrix | Scope confirmed by later runs |
| `488f17c06fc3` | Restore final CI workflow after formatting diagnostics | Native tests passed; Markdown cleanup required |
| `ddef21a1de1e` | Consolidate and normalize main-based plan | Markdown guard passed in later run |
| `0388727346aa` | Decompose compatibility validation into bounded checks | Detekt pending |
| `3267ecac0306` | Replace long-parameter fixture with transform-based fixture | Detekt pending |
| `d0dfbc3ac58f` | Adapt compatibility tests to transformed fixtures | Spotless identified one fixture signature only |
| `4aac5a2ff32a` | Apply exact Spotless fixture format | Validation trigger pending |

## Next implementation slice

After the current module is green:

1. add a bounded catalog codec contract and deterministic JSON implementation;
2. add app-private atomic catalog persistence;
3. implement revision, expiry and stale-cache behavior;
4. add a fake catalog source and synchronization service;
5. only then introduce the HTTPS download module.

## Validation gate

The current slice is not complete until all relevant checks pass:

- [x] repository navigation and script guards
- [-] Spotless and ktlint
- [-] Detekt
- [-] `:models:model-catalog:testDebugUnitTest`
- [-] Android Lint for `model-catalog`
- [-] downstream application compilation
- [x] native host tests
- [ ] aggregate required `Repository validation` check

No task marked `[-]` becomes `[x]` solely because code exists.

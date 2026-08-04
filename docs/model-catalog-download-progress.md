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
| Architecture decision | `[-]` | ADR 0005 is implemented and CI-validated but remains Proposed pending review |
| Catalog domain contracts | `[x]` | Public domain types passed repository validation run `30954976390` |
| Catalog validation | `[x]` | Fail-closed validation and deterministic tests passed run `30954976390` |
| Target filtering | `[x]` | Exact application/use-case filtering passed run `30954976390` |
| Compatibility evaluation | `[x]` | API, ABI, backend, profile, RAM and storage policy passed run `30954976390` |
| Public module documentation | `[x]` | `models/model-catalog/README.md` passed repository validation run `30955361481` |
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
- [-] move ADR 0005 from Proposed to Accepted after review

## Phase 1 — Catalog domain and policy

- [x] register `:models:model-catalog`
- [x] define catalog, release, artifact, target, license and availability models
- [x] define application-owned profile resolver boundary
- [x] validate schema, IDs, time window, entry count and release uniqueness
- [x] validate SHA-256, positive size, HTTPS URI and safe GGUF file name
- [x] validate compatibility, targets and license links
- [x] detect conflicting metadata for the same digest
- [x] filter releases by exact application and use case
- [x] evaluate availability, API, ABI, backend, Harness version and profile support
- [x] evaluate minimum and recommended RAM
- [x] calculate storage using download and import copies plus safety margin
- [x] add deterministic validator and compatibility tests
- [x] validate with repository CI
- [x] document and validate the public module API

**Phase status:** `[x]`

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
| `30954976390` | passed | Repository guards, Spotless, Detekt, catalog tests, Android Lint, downstream compilation, native tests and packaging all passed |
| `30955361481` | passed | Public module README, final progress state and the full repository validation matrix passed |

## Key commits

| Commit | Change | Validation |
|---|---|---|
| `639a9d4f99da` | Main-based implementation plan | Replaced by consolidated plan |
| `a906b5b3dde4` | Add model-catalog module | Passed in run `30954976390` |
| `15b7121e6730` | Register module in settings | Passed in run `30954976390` |
| `0a69d294315f` | Add catalog domain contracts | Passed in run `30954976390` |
| `74173e1c275e` | Add fail-closed catalog validation | Refactored and passed in run `30954976390` |
| `9e8fe0b6fa26` | Add target filtering and compatibility evaluation | Passed in run `30954976390` |
| `4db85c723cfb` | Add test fixtures | Refactored and passed in run `30954976390` |
| `df61f7fba777` | Add validator tests | Passed in run `30954976390` |
| `ff00a710166d` | Add compatibility tests | Passed in run `30954976390` |
| `e336989d3eea` | Add ADR 0005 | Passed in run `30954976390`; review pending |
| `2fa827594cbc` | Index ADR 0005 | Passed in run `30954976390` |
| `0fce18d1cfec` | Map module in `AGENTS.md` | Passed in run `30954976390` |
| `6273639690b0` | Include catalog module in CI matrix | Passed in run `30954976390` |
| `0388727346aa` | Decompose compatibility validation into bounded checks | Passed Detekt in run `30954976390` |
| `3267ecac0306` | Replace long-parameter fixture with transform-based fixture | Passed Detekt in run `30954976390` |
| `4aac5a2ff32a` | Apply exact Spotless fixture format | Passed Spotless in run `30954976390` |
| `cfcb4e0bc533` | Add public module README | Passed in run `30955361481` |

## Next implementation slice

1. add a bounded catalog codec contract and deterministic JSON implementation;
2. add app-private atomic catalog persistence;
3. implement revision, expiry and stale-cache behavior;
4. add a fake catalog source and synchronization service;
5. only then introduce the HTTPS download module.

## Validation gate for the catalog foundation

- [x] repository navigation and script guards
- [x] Spotless and ktlint
- [x] Detekt
- [x] `:models:model-catalog:testDebugUnitTest`
- [x] Android Lint for `model-catalog`
- [x] downstream application compilation
- [x] native host tests
- [x] Android native packaging
- [x] aggregate required `Repository validation` check
- [x] public documentation validation

No task becomes `[x]` solely because code exists.

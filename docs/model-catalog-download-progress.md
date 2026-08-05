# Model catalog and secure download progress

**Canonical plan:** [`model-catalog-download-plan.md`](model-catalog-download-plan.md)

**Base:** `main` at `808b74911c5ce5539c7659dca1b875fee9c2467e`

**Branch:** `agent/model-catalog-persistence`

**Pull request:** [#42](https://github.com/daniele21/android-local-llm-harness/pull/42)

**Last updated:** 2026-08-05

## Status legend

- `[ ]` not started
- `[-]` in progress or awaiting repository validation
- `[x]` implemented and validated
- `[!]` blocked or awaiting an architectural or product decision

## Overall progress

| Workstream | Status | Current evidence |
|---|---:|---|
| Main audit and branch control | `[x]` | PR #41 merged into `main`; persistence slice starts from merge `808b74911c5c` |
| Architecture decision | `[x]` | ADR 0005 accepted after the validated foundation merged |
| Catalog domain contracts | `[x]` | Merged through PR #41 |
| Catalog validation | `[x]` | Merged through PR #41 |
| Target filtering | `[x]` | Merged through PR #41 |
| Compatibility evaluation | `[x]` | Merged through PR #41 |
| Public module documentation | `[-]` | Expanded for codec, persistence and refresh; repository validation pending |
| Catalog parsing and codec | `[-]` | Strict bounded JSON codec compiled and passed deterministic local checks; repository validation pending |
| Catalog persistence and refresh | `[-]` | Atomic app-private repository and synchronizer compiled and passed deterministic local checks; repository validation pending |
| Secure model download | `[ ]` | Starts only after this slice merges |
| GGUF installation orchestration | `[ ]` | Not started |
| Installed release metadata | `[ ]` | Not started |
| Phone-app integration | `[ ]` | Not started |
| Update, deprecation and revocation | `[ ]` | Not started |
| Health and repair | `[ ]` | Not started |
| Manifest signing | `[ ]` | Not started |
| Physical-device evidence | `[ ]` | Not started |

## Phase 0 — Main audit and ownership

- [x] use current `main` as the only implementation base
- [x] audit `ModelStore` and the phone-test integration boundary
- [x] merge the validated catalog foundation through PR #41
- [x] create `agent/model-catalog-persistence` from the resulting `main`
- [x] accept ADR 0005 after the architectural boundary merged

## Phase 1 — Catalog domain and policy

- [x] define catalog, release, artifact, target, license and availability models
- [x] validate schema, identity, lifecycle, artifact and compatibility fields
- [x] filter releases by exact application and use case
- [x] evaluate API, ABI, backend, profile, RAM and storage compatibility
- [x] add deterministic tests and public module documentation
- [x] validate and merge through PR #41

**Phase status:** `[x]`

## Phase 2 — Codec, persistence and synchronization

- [-] add a deterministic schema-versioned JSON codec
- [-] enforce byte, depth, node and string limits before domain validation
- [-] reject duplicate keys, duplicate set values, unknown fields and missing fields
- [-] reject malformed UTF-8, invalid Unicode, invalid numbers, enums, types and URIs
- [-] encode fields and set-backed values deterministically
- [-] add app-private state persistence with temporary staging, sync and atomic replacement
- [-] recover without replacing the last good snapshot after invalid input or refresh failure
- [-] reject catalog identity changes, revision rollback and same-revision conflicts
- [-] expose `EMPTY`, `FRESH`, `STALE` and `EXPIRED` states
- [-] authorize new downloads only from a fresh snapshot
- [-] preserve stale and expired documents for diagnostics
- [-] add conditional ETag, Last-Modified and revision request metadata
- [-] treat local clock time as authoritative for fetch timestamps
- [-] handle `NotModified` without extending document expiry
- [-] normalize unexpected source exceptions into typed failures
- [-] add deterministic JUnit tests for codec, repository and synchronizer behavior
- [-] update module and agent documentation
- [ ] pass Spotless, Detekt, unit tests, Android Lint and aggregate repository validation

**Phase status:** `[-]` — implementation and strongest available local checks complete; clean-checkout CI pending.

## Local pre-push evidence

The execution environment cannot resolve GitHub or Maven hosts from the local shell and therefore cannot run the repository Gradle wrapper or Android SDK gate. Before the single implementation push, the strongest available equivalent checks were completed:

- Kotlin/JVM production sources compiled with `kotlinc`;
- all new JUnit source files compiled against the public test signatures;
- a standalone deterministic runner executed 20 codec, persistence and synchronization scenarios successfully;
- line length, trailing whitespace, imports, public boundaries and the complete diff were reviewed manually;
- no Android, network, model-store or runtime dependency was added;
- no GGUF artifact, credential, URL secret or private path was added;
- after run `30958031076` identified formatting-only differences, all six affected files were rewritten conservatively;
- production compilation, test-source compilation, the 20-scenario runner, the invalid-Unicode runner and source-hygiene checks passed again before the corrective commit.

Repository CI remains the final clean-checkout confirmation and is not counted as passed in this tracker until it completes.

## Foundation validation history

| Run | Result | Finding or evidence |
|---|---:|---|
| `30952159115` | failed | Agent navigation required the new module in `AGENTS.md` |
| `30952241203` | failed | Kotlin formatting issues detected during the initial foundation work |
| `30953021399` | failed | CI matrix began executing catalog tests and lint; formatting remained |
| `30954312737` | failed | Detekt found fixture and compatibility-validation complexity issues |
| `30954976390` | passed | Foundation guards, Spotless, Detekt, tests, lint, downstream compilation, native tests and packaging passed |
| `30955361481` | passed | Public module README and full validation matrix passed |
| `30955750296` | passed | Final foundation head passed repository validation before PR #41 merge |
| `30958031076` | failed | Persistence slice reached the Android gate but stopped only at ktlint formatting; no functional validation was claimed |

## Next implementation slice

After Phase 2 is repository-validated and merged:

1. define the secure download job contracts and typed states;
2. add HTTPS transport policy with allowlisted hosts and redirect validation;
3. stream to app-private `.part` files with cancellation and progress;
4. verify byte count and SHA-256 before invoking `ModelStore.import()`;
5. keep installation, selection and runtime loading as separate explicit operations.

No task becomes `[x]` solely because code exists.

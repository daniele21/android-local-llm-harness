# Background/process lifecycle hardening

Status: active
Owner: shared-runtime/runtime lifecycle
Read when: coordinating detached inference jobs, Binder reconnect, Host background execution, process recovery or downstream lifecycle evidence
Last reviewed: 2026-09-02

Repository priority and integrated/blocker truth remain owned by [`../current-state.md`](../current-state.md). This workstream coordinates only the bounded implementation sequence.

## Goal

Make explicitly durable local inference independent from Activity/Compose and transient Binder lifetime while preserving same-signer isolation, Harnex-owned runtime/model authority, exact accepted execution identity, explicit cancellation, bounded resources and privacy-safe recovery. Ordinary connection-scoped Consumer inference keeps ADR 0012 lifecycle semantics.

Canonical architectural decision: [ADR 0016](../adr/0016-detached-shared-runtime-jobs.md).

## Non-goals

- Do not make passive setup/readiness create durable work or residency demand.
- Do not persist prompt/document/generated content to manufacture process-death recovery.
- Do not treat Binder reconnect as permission to re-resolve an accepted job against newer configuration.
- Do not claim native work survives Host process death.
- Do not infer physical ARM64/JNI/GGUF/OEM/model-memory behavior from emulator evidence.
- Do not patch Harnex model-store/setup resolver/runtime in response to RedactGuard's generic `INCOMPATIBLE` state until the downstream typed `ConsumerControlPlaneErrorCode` is preserved and routed to a canonical owner.

## Invariants

- UI lifecycle, consumer workflow lifecycle, Binder lifecycle and runtime/model lifecycle are separate.
- Durable jobs are explicit/capability-negotiated; passive setup/readiness and ordinary legacy inference never create one implicitly.
- Binder death/unbind removes transport observers; it does not semantically cancel an accepted durable job.
- Logical job identity is stable across reconnect and idempotent resubmit.
- Accepted jobs pin the exact `ConsumerExecutionIdentity` returned by preparation; reconnect never silently re-resolves the same job against newer configuration.
- Harnex owns `ConsumerInferenceJobId`, execution/session ownership, Android service lifetime and model/runtime authority. RedactGuard owns its product `AnalysisJobId` and product recovery semantics.
- Callback delivery is advisory. Revisioned `query/result` state is authoritative after reconnect; generated content is not turned into a durable callback log.
- Existing connection-scoped prepare/session/generate behavior remains backward-compatible.
- Host process death is an interruption boundary, never reported as continued RUNNING.
- Prompt/document/generated content stays out of durable storage, normal telemetry and shared evidence.
- Known Consumer failure identity must survive versioned Binder/SDK boundaries; free-form error messages are not a product-policy contract.
- Real-device background/model behavior is not inferred from emulator/CI evidence.

## Resource ownership

| Resource | Semantic owner | Transport loss | Terminal/cleanup owner |
| --- | --- | --- | --- |
| Binder registration/death link/callback dispatcher | connection | removed | connection cleanup |
| durable logical inference job | Host logical-job registry/coordinator | survives | job terminal/policy |
| session/generation handle retained by durable work | logical job/runtime | survives | job terminal/cancel/runtime policy |
| started/foreground service demand | active durable-job demand | survives | final active durable job terminal |
| idle client session | authenticated connection/session policy | bounded cleanup allowed | session policy |
| loaded model | existing runtime/residency policy | unaffected by observer loss | explicit unload/idle/pressure/shutdown |

## Current integration checkpoint

Harnex `dev` is `f86b53ad29d2396660f095d5eaadd41c19bda8c7`.

Integrated milestones relevant to this workstream:

- PR #502 established caller/use-case-scoped durable `ConsumerInferenceJobId`, idempotent submit, authoritative query/result, explicit cancel, exact prepared execution-identity pinning, detached session/generation ownership and started/foreground service demand.
- Consumer logical jobs use protocol minor 6 and shipped in Consumer SDK `0.1.0-alpha.8`.
- PR #510 added the signature-protected emulator fault gate used by downstream Binder disconnect/rebind lifecycle tests.
- PR #511 fixed concrete `BinderConsumerLocalLlmClient.resolveSetup` forwarding through the Binder control plane.
- Consumer SDK `0.1.0-alpha.9` was published from the integrated #511 line and independently resolved by publication automation.
- RedactGuard has consumed the logical-job boundary and alpha.9 on its LAS candidate and contains dedicated app-switch/ViewModel/Binder lifecycle journeys.

The latest downstream exact RedactGuard Two-APK run `33594812860` reached protocol minor 6, negotiated `consumer-setup-resolution-v1`, validated one assigned use case and one published preset, then failed before lifecycle continuation with generic RedactGuard `INCOMPATIBLE`. No setup-resolution diagnostic event was emitted. Current RedactGuard source analysis indicates its diagnostics layer can mask the original typed setup rejection.

Therefore this downstream failure does **not** currently establish a Harnex model-store/resolver/runtime defect. RedactGuard must first preserve and expose the typed Consumer setup-resolution outcome. Harnex corrective work is conditional on that owner-routing evidence.

## Work graph

| ID | Work | Owns/writes | Depends on | Parallel | State |
| --- | --- | --- | --- | --- | --- |
| HBG-00 | Accept ADR separating Binder/client lifetime from explicit durable jobs/runtime lifetime | ADR 0016 | — | yes | DONE |
| HBG-01 | Keep current-state/workstream/shared-runtime owners reconciled as downstream evidence lands | Harnex docs only | HBG-00 | yes | ACTIVE |
| HBG-10 | Freeze connection/job/service/session/model resource ownership matrix | ADR/workstream/runtime contract | HBG-00 | yes | DONE |
| HBG-20 | Add Host logical job identity/state/revision/attempt/runtime-session primitives | runtime/shared-runtime contracts/tests | HBG-10 | no | DONE |
| HBG-21 | Add deterministic transition/idempotency/stale-revision tests | logical-job tests | HBG-20 | yes | DONE |
| HBG-22 | Add bounded process-local job registry independent from Binder callbacks | Host logical-job registry/tests | HBG-20 | no | DONE |
| HBG-23 | Add authenticated application/use-case-scoped idempotency lookup | Host registry/control-plane/tests | HBG-22 | no | DONE |
| HBG-24 | Pin exact prepared `ConsumerExecutionIdentity` into submit/snapshot and reject mismatches | contracts/Host/SDK/tests | HBG-23 | no | DONE |
| HBG-30 | Separate connection cleanup from semantic durable-job cancellation | Binder Host/service tests | HBG-24 | no | DONE |
| HBG-31 | Keep durable execution independent from Binder callback delivery | Host logical-job execution/tests | HBG-30 | no | DONE |
| HBG-32 | Transfer durable session/generation ownership out of connection ledger | Host/runtime tests | HBG-31 | no | DONE |
| HBG-33 | Publish minor-6 submit/query/result/cancel Consumer boundary | Binder contract/client/SDK/docs | HBG-32 | no | DONE |
| HBG-34 | Preserve ADR 0012 cleanup for non-durable clients/requests | Binder Host/tests | HBG-30 | yes | DONE |
| HBG-40 | Add started-service execution ownership while durable demand exists | Android Host service/Manifest/tests | HBG-32 | no | DONE |
| HBG-41 | Add foreground/user-visible execution adapter and notification path | Android Host service/UI policy/tests | HBG-40 | no | DONE |
| HBG-42 | Reconcile stale non-terminal metadata after actual Host process restart; never restore zombie RUNNING | logical-job persistence/recovery boundary/tests/docs | HBG-41 | yes | READY |
| HBG-43 | Add retry-attempt semantics only when required input remains safely available; otherwise fail closed | logical-job retry/recovery contracts/tests | HBG-42 | no | BLOCKED |
| HBG-50 | Prove durable demand composes with existing single-owner residency/memory policy | runtime/residency tests + deterministic/physical evidence | HBG-41, HBG-61 | no | BLOCKED |
| HBG-55 | RedactGuard consumes stable logical jobs/reattaches one product job without duplicate generation | downstream RedactGuard integration; no Harnex writes | HBG-33 | yes | DONE |
| HBG-56 | Route current downstream setup-resolution failure to a typed canonical owner before any Harnex functional patch | cross-repo integration evidence only | RedactGuard LAS diagnostics hardening | no | BLOCKED |
| HBG-60 | Complete disconnect/reconnect/cancel lifecycle matrix on exact downstream candidate | Harnex tests + downstream integration evidence | HBG-55, HBG-56 | no | BLOCKED |
| HBG-61 | Prove dedicated two-APK app-switch/Binder reconnect/process-loss/fault-injection journeys | downstream Two-APK workflow/evidence | HBG-60 | no | BLOCKED |
| HBG-62 | Capture bounded privacy-safe lifecycle state artifact plus screenshots/video | downstream evidence workflow | HBG-61 | no | BLOCKED |
| HBG-63 | Run repository-owned STRONG remote preflight on final exact Harnex head/base after remaining Harnex code slices | Harnex CI/evidence | HBG-42, HBG-43, HBG-50 | no | BLOCKED |
| HBG-64 | Record representative ARM64 same-signer two-APK + real-GGUF/model-residency/OEM evidence | physical evidence only | HBG-61, HBG-62, HBG-63 | no | BLOCKED |

`HBG-42` is the only independent Harnex implementation slice currently executable. `HBG-56` is deliberately blocked on RedactGuard preserving the typed setup-resolution result; it is an owner-routing gate, not a placeholder authorization for speculative Harnex changes.

## Current executable slice

`HBG-42`

Acceptance:

- after real Host process restart, any persisted/recoverable non-terminal logical-job metadata whose native execution cannot still exist becomes an explicit interrupted/process-lost outcome rather than RUNNING;
- reconciliation is idempotent and bounded;
- no prompt/document/generated payload is persisted merely to support the transition;
- caller/use-case authorization remains enforced when querying reconciled state;
- connection-scoped legacy inference semantics remain unchanged;
- recovery metadata is sufficient for safe product explanation/retry routing without pretending native RAM survived.

Validation:

- use `python3 scripts/detect_ci_scope.py` with `profile=auto`; expected depth is `STRONG` because runtime/logical-job recovery and cross-process lifecycle are shared behavior;
- run owning unit/contract tests and direct Binder/Host consumers, then repository-owned remote automation for unavailable deterministic Android gates;
- physical process-policy/model-memory claims remain separate `REAL_ENVIRONMENT` evidence.

## Conditional setup-resolution owner gate

No Harnex setup-resolution corrective slice is READY yet. Once RedactGuard's LAS rerun preserves a typed outcome, route it as follows:

| Typed outcome | Harnex owner to inspect if contract evidence points here |
| --- | --- |
| `MODEL_UNAVAILABLE` / `MODEL_CONFLICT` | setup resolver, model store, binding/residency policy |
| `CONFIGURATION_REQUIRED`, `USE_CASE_NOT_ASSIGNED`, `PRESET_NOT_EXPOSED`, `STALE_REVISION` | application/use-case/preset control-plane state and revision reconciliation |
| `TRANSPORT_FAILURE` | Binder callback/epoch/timeout transport after downstream request validity is proven |
| `FEATURE_UNAVAILABLE` | protocol negotiation, SDK artifact identity and concrete client delegation |
| `RUNTIME_FAILURE` | Host control-plane/runtime implementation after transport/request validity is proven |
| `INVALID_REQUEST` | inspect downstream request construction first; Harnex mapping only if the request is valid before transport |
| `Resolved` | Harnex setup resolution has succeeded; downstream mapping/readiness owns the next investigation unless later evidence contradicts it |

If Harnex is selected as canonical owner, create the smallest bounded implementation slice in this workstream or a focused child PR, inspect direct consumers/tests, and require fresh exact-head validation. Do not seed a different fixture model or weaken setup criteria merely to make the downstream E2E green.

## Fault matrix

| Event | Required semantic result |
| --- | --- |
| Host UI recreation | no inference/job change |
| Consumer UI recreation/navigation | no inference/job change; no duplicate job |
| Consumer app background | accepted durable job continues under valid Android execution policy |
| Binder callback death/unbind | transport observer removed; durable job survives |
| Consumer process death | Host job may survive while Host process survives; later authorized reconciliation must not expose another caller's work |
| Host process death | old native work is interrupted; never zombie RUNNING |
| Explicit cancel while detached | idempotent semantic cancellation with one terminal result |
| Authorization loss | explicit fail-closed outcome; no cross-caller reattachment |
| Model/config conflict | explicit fail-closed outcome; no silent substitution |
| Critical memory pressure | runtime policy may cancel/release for system health; impossible continuation is explicit interruption |
| Idle residency deadline | only genuinely idle/unprotected resources become unloadable |
| Persisted evidence inspection | no prompt/document/generated-output content present |

## Integration points

1. HBG-24/HBG-33 are the completed Consumer contract prerequisites consumed by RedactGuard.
2. PR #511 + alpha.9 is the completed concrete Binder setup-resolution forwarding prerequisite; downstream generic incompatibility is not evidence that this delegation regressed.
3. RedactGuard retains one stable product `AnalysisJobId`; Harnex retains each durable `ConsumerInferenceJobId` and exact execution identity.
4. HBG-40/41 keep the Host process eligible for user-visible durable work, but do not claim inference survives Host process death.
5. HBG-42/43 recovery may persist only privacy-safe metadata unless a separate privacy/security decision expands the boundary.
6. HBG-56 must resolve the current setup blocker before HBG-60/61 can produce meaningful lifecycle acceptance evidence.
7. Physical validation begins after deterministic two-APK lifecycle E2E and final exact-head automated preflight are green.

## Durable documentation destinations

- ADR 0016: durable job/Binder/process-lifetime architecture; update only if the decision changes.
- shared-runtime/Consumer SDK docs: public logical-job/setup contract changes only.
- `.engineering/e2e.json`: canonical journey/environment/fidelity truth.
- `docs/current-state.md`: integrated baseline, blockers and immediate work.
- tests/contracts: authoritative transition, authorization, setup failure identity and recovery behavior.
- downstream RedactGuard LAS workstream: product readiness/recovery semantics and cross-repo sequencing.

## Validation

Executable shared-runtime/Binder/service/recovery behavior is `STRONG` by blast radius. Documentation-only reconciliation uses the selector and should remain `LEAN` unless repository rules escalate it. `FULL` remains exceptional and is required only when selector/global build/toolchain/promotion scope justifies it.

Deterministic compile/unit/static/lint/Binder/emulator/packaging gates are `AGENT_LOCAL` only with an equivalent environment and otherwise `REMOTE_AUTOMATED`. Representative same-signer ARM64/JNI/GGUF model residency, OEM process policy, thermal/resource and protected-signing claims remain `REAL_ENVIRONMENT`.

## Completion

This workstream completes only when:

- Host process restart cannot leave impossible logical jobs reported as running;
- retry/recovery remains privacy-safe and explicit;
- disconnect/reconnect/cancel and app-switch journeys reach their lifecycle assertions on an exact downstream candidate without duplicate generation;
- setup-resolution failures retain typed identity and any Harnex-owned defect is fixed at its canonical owner rather than compensated downstream;
- deterministic exact-head Harnex validation passes at the selected profile;
- representative physical evidence separately confirms the residual ARM64/JNI/GGUF/model-residency/OEM claims.

Then transfer durable truth to canonical docs/current-state and delete this workstream by default.
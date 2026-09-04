# Harness Host Control Plane roadmap

Status: active
Document type: roadmap
Owner: shared-runtime-control-plane
Canonical scope: shared-runtime.control-plane.roadmap
Read when: selecting the next Host Control Plane task, checking dependencies, or defining parallel implementation slices
Last reviewed: 2026-08-18

This roadmap owns HCP implementation order and state. Durable ownership is ADR 0015. Detailed task behavior is split by workstream so agents read only the lane they are changing.

## Outcome

Harness becomes the configurable owner of applications, use cases, suggested/custom published presets, exact model/execution binding, activation/residency, decision/notification state and persistent internal/external inference history. Consumers select only host-assigned use cases and host-published safe preset metadata.

## Workstream routing

| Concern | Detailed owner |
| --- | --- |
| Applications, use cases, suggested/custom presets, binding, persistence, resolver, admin UI | [`workstreams/configuration.md`](workstreams/configuration.md) |
| Structured decisions, dedupe, Android notifications, persistent Decision Center | [`workstreams/decisions.md`](workstreams/decisions.md) |
| Session/run identity, Room history, runtime instrumentation, Sessions UI | [`workstreams/observability.md`](workstreams/observability.md) |
| Activation leases, residency, consumer discovery/Binder evolution, final cutover | [`workstreams/activation-consumer.md`](workstreams/activation-consumer.md) |

## Dependency graph

```text
HCP-0 architecture freeze
   |
   |----------------------+----------------------+-------------------|
   v                      v                      v                   v
HCP-1/2 configuration   HCP-10 decisions      HCP-14 sessions     HCP-17 lease contract
   |                      |                      |                   |
HCP-3/4 presets         HCP-11 rules          |----+----|            |
   |                      |                   HCP-15 HCP-16           |
HCP-5 revision          HCP-12/13             Room    runtime        |
   |                      |                      |      |             |
HCP-6 binding           HCP-25 UI              +--+---+             |
   |                                                |                |
HCP-7 exposure                                   HCP-26 UI           |
   |
HCP-8 store
   |
HCP-9 resolver ---------------------------------------> HCP-17 integration
   |                                                       |
   |---------------------> HCP-19/20 discovery             v
                           |                              HCP-18 residency
                           +---------------+---------------+
                                           v
                                         HCP-21 Binder/API
                                           |
                                           v
                                         HCP-27 cutover
```

Harness administration UI can begin against fakes once its domain contracts exist. RedactGuard multi-preset tolerance can proceed against the current Consumer API; assigned-use-case discovery and activation wait for HCP-19/HCP-21.

## Task state

| Task | State | Dependency | Deliverable |
| --- | --- | --- | --- |
| HCP-0 | IN PROGRESS | — | ADR 0015 + canonical plan integrated/green |
| HCP-1 | IN PROGRESS | HCP-0 | registered-application domain |
| HCP-2 | IN PROGRESS | HCP-0 | Harness-managed use-case domain |
| HCP-3 | PLANNED | HCP-2 | deterministic suggested preset service |
| HCP-4 | IN PROGRESS | HCP-2 | custom/published preset domain |
| HCP-5 | IN PROGRESS | HCP-3/4 | immutable preset revision/publishing semantics |
| HCP-6 | IN PROGRESS | HCP-1/2 | application/use-case binding |
| HCP-7 | IN PROGRESS | HCP-4/5/6 | per-application preset exposure |
| HCP-8 | PLANNED | HCP-1..7 | persistent control-plane store |
| HCP-9 | PLANNED | HCP-8 | deterministic execution resolver |
| HCP-10 | IN PROGRESS | HCP-0 | structured decision-event contract |
| HCP-11 | PLANNED | HCP-10 | decision rules/deduplication |
| HCP-12 | PLANNED | HCP-10/11 | Android notification projection |
| HCP-13 | PLANNED | HCP-10/11 | persistent Decision Center |
| HCP-14 | IN PROGRESS | HCP-0 | session/run observability identity |
| HCP-15 | IN PROGRESS | HCP-14 | Room session/run history + migration |
| HCP-16 | PLANNED | HCP-14 | runtime-first unified instrumentation |
| HCP-17 | IN PROGRESS | HCP-0; HCP-9 for final wiring | activation lease contract/ownership |
| HCP-18 | PLANNED | HCP-9/17 | lease-aware residency/warm retention |
| HCP-19 | PLANNED | HCP-6/7/9 | assigned-use-case discovery |
| HCP-20 | PLANNED | HCP-7/9 | published/custom preset discovery |
| HCP-21 | PLANNED | HCP-17..20 | additive Consumer/Binder activation lifecycle |
| HCP-22 | PLANNED | HCP-1/6 | Applications UI |
| HCP-23 | PLANNED | HCP-2/3/5 | Use Case Builder UI |
| HCP-24 | PLANNED | HCP-4/5/7/9 | custom Preset Editor UI |
| HCP-25 | PLANNED | HCP-12/13 | Decision Center/notification UI |
| HCP-26 | PLANNED | HCP-15/16 | unified Sessions/Inference UI |
| HCP-27 | PLANNED | all cutover prerequisites | remove hardcoded consumer binding/global-model dependency |

`IN PROGRESS` reflects active branches/PRs only; no behavior is assumed integrated until merged into `dev` with its exit gate green.

## Parallel lanes now

The current independent implementation lanes are:

- **Configuration:** HCP-1/2/4/5/6/7 domain foundation, then HCP-3 and HCP-8/9.
- **Decisions:** HCP-10 contract, then HCP-11 rules independently of Android notification wiring.
- **Observability:** HCP-14 contract; HCP-15 Room persistence and HCP-16 runtime instrumentation can develop in parallel once the contract is stable.
- **Activation:** HCP-17 pure ownership contract now; final resolver/residency integration waits for HCP-9.
- **Consumer:** RedactGuard can remove the false single-preset assumption now; HCP-19/20/21 gate later discovery/activation work.

## Integration order

Merge/retarget stacked work only after its parent is green. Recommended convergence:

```text
HCP-0
  -> foundational contracts (HCP-1/2/4/10/14/17)
  -> persistence/rules/instrumentation (HCP-8/11/15/16)
  -> resolver + residency (HCP-9/18)
  -> discovery + Binder activation (HCP-19/20/21)
  -> Harness admin/decision/history UI
  -> RedactGuard final consumer cutover
  -> HCP-27 removal + physical evidence
```

## Final exit gate

HCP is complete only when:

- no supported consumer-to-model/preset binding is hardcoded in runtime/UI;
- custom Harness presets can be published/exposed and discovered safely by a compatible consumer;
- an active consumer activation protects residency across session gaps;
- all internal and external sessions/runs are persistently visible in Harness without prompt/output persistence;
- actionable configuration/security/resource conditions appear in the Decision Center and selected ones reach Android notifications with exact deep links;
- revision changes, model removal, conflict, Binder/process death, Harness restart and critical memory pressure fail/recover deterministically;
- representative physical two-APK evidence records exact host/client/runtime/model/preset/device identity.

## PR slicing

| Slice | Scope |
| --- | --- |
| HCP-A | HCP-1/2/4/5/6/7 foundational configuration domain |
| HCP-B | HCP-3 suggestion service |
| HCP-C | HCP-8 persistent control-plane store |
| HCP-D | HCP-9 resolver |
| HCP-E/F | HCP-10/11 then HCP-12/13 decisions/notifications |
| HCP-G/H/I | HCP-14 contract, HCP-15 Room, HCP-16 runtime instrumentation |
| HCP-J | HCP-17 then HCP-18 activation/residency |
| HCP-K/L | HCP-19/20 discovery then HCP-21 Binder/API |
| HCP-M/N/O | HCP-22..26 Harness UI lanes |
| HCP-P | HCP-27 cross-repo cutover and evidence |

Physical/device evidence remains pending unless executed for the exact candidate. Compilation/JVM/emulator checks never substitute for it.

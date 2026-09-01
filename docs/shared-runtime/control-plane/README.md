# Harness Host Control Plane

Status: active
Document type: feature-index
Owner: shared-runtime-control-plane
Canonical scope: shared-runtime.control-plane.routing
Read when: designing or implementing dynamic application/use-case bindings, presets, residency ownership, Harness decisions/notifications, or unified cross-app inference observability
Last reviewed: 2026-08-31

Harness is the owner of local-AI execution policy for both its own UI and authorized consumer applications. Consumers declare intent through host-advertised use cases and presets; they never own exact GGUF/artifact selection, residency, backend tuning or host diagnostics.

This workstream closes the gap between the accepted public Consumer API boundary and the current proof-host implementation, where several consumer bindings and warm-idle decisions are still composed statically in the phone host.

## Target boundary

```text
consumer app
  -> authenticated connection
  -> discover assigned use cases
  -> discover published presets
  -> resolve selected setup read-only
  -> activate one use case + preset
  -> create sessions / generate / close sessions
  -> deactivate

Harness control plane
  -> register/authorize application identity
  -> define use cases in Harness
  -> suggest and manage presets in Harness
  -> allow user-created presets and per-application exposure
  -> resolve preset revision to exact model + execution profile
  -> project consumer-safe effective setup without acquiring residency
  -> acquire/release model residency leases only at explicit activation
  -> surface decisions through an in-app decision center and Android notifications

Harness runtime + observability
  -> execute every internal and external request through one runtime path
  -> persist every session and generation with application/use-case/preset/binding identity
  -> retain prompts and generated content only in bounded process memory
```

## Non-negotiable rules

- Use cases and consumer-visible presets are Harness-owned persistent configuration, not application-specific `when` branches.
- Suggested presets such as Fast, Balanced or Quality are templates, not protocol constants. Users may create custom presets and choose which presets are published to each consumer application.
- Discovery exposes safe preset metadata. The additive setup-resolution projection may also expose the resolved public model-profile ID, context tokens and effective consumer-safe generation values required to explain compatibility/readiness. Artifact digest/path, GGUF location, backend-private tuning, prompts and residency policy remain Harness-only.
- Setup resolution is observational. Opening or refreshing a consumer setup surface must not activate, prepare, verify/hash or load a model, open a session, start inference or acquire a residency lease.
- Setup resolution revalidates the exact use-case, binding and preset revisions supplied by the consumer and fails closed when the observed configuration became stale.
- A published configuration change creates a new revision. Existing activations/sessions retain the revision they started with.
- Model residency is protected by explicit activation leases. Normal warm-idle unload cannot evict a model with an active lease.
- Every inference executed by the Harness runtime is observable through the same host-owned telemetry path, regardless of whether it originated in Harness, device validation or an external Binder consumer.
- Production Harness telemetry is persistent and bounded; prompt text, generated output, document content and private paths stay out of normal telemetry.
- Actionable operational states are represented once as structured decision events. The in-app decision center is the source of truth; Android notifications are a delivery surface for selected actionable events, not a parallel state store.
- Unknown/missing bindings, unavailable models, incompatible presets, conflicts and evictions fail explicitly with stable codes and evidence. No silent model substitution or consumer-specific fallback is allowed.
- The current default remains one resident model and one active decode until representative evidence justifies a different policy.

## Consumer setup resolution

Binder protocol minor 5 adds `consumer-setup-resolution-v1` as an append-only Consumer Control Plane feature. A consumer sends the exact `(useCaseId, useCaseRevision, bindingRevision, presetRef)` it already discovered. Harness resolves that identity through the canonical `HostExecutionResolver` and returns only the consumer-safe projection:

- exact use-case/binding/preset revisions;
- public model-profile ID;
- effective context size;
- effective generation controls after published preset overrides: output-token limit, sampling values, repetition values, thinking mode and seed policy type.

A random/fixed seed value is not projected as setup state: a random effective seed is chosen only for a concrete generation request, while fixed seed values remain host execution detail. Request-time fields such as prompt token count and selected chat-template implementation are likewise not setup metadata.

Minor-4 and older clients remain compatible and do not negotiate this feature. Consumer SDK implementations that do not implement the new method fail explicitly with `FEATURE_UNAVAILABLE` rather than synthesizing setup state.

## Ownership

| Concern | Owner |
| --- | --- |
| Application registration, use-case definitions, presets, bindings and revisions | model/control-plane domain in Harness |
| Persistent control-plane configuration | dedicated Harness persistence adapter |
| Exact model resolution and compatibility | Harness model/runtime domain |
| Consumer-safe setup projection | Harness Control Plane + Consumer SDK/Binder adapter |
| Activation and residency leases | `core/runtime-core` |
| Consumer discovery/activation protocol | Consumer API + Binder transport |
| Session/run telemetry contracts | `observability/contracts` |
| Persistent runtime history | `observability/room-store` |
| Decision-event contract and policy | Harness control-plane domain |
| Android notifications and deep links | `apps/local-llm-phone-test` adapter/UI |
| Consumer preset selector | consumer application, using only host-published metadata |

## Read only the active lane

| Need | Read |
| --- | --- |
| Implementation order, states and cross-lane dependencies | [`roadmap.md`](roadmap.md) |
| Applications, use cases, suggested/custom presets, bindings, persistence, resolver and admin UI | [`workstreams/configuration.md`](workstreams/configuration.md) |
| Decisions, dedupe, system notifications and Decision Center | [`workstreams/decisions.md`](workstreams/decisions.md) |
| Session/run telemetry, Room history, runtime instrumentation and Sessions UI | [`workstreams/observability.md`](workstreams/observability.md) |
| Activation/residency, consumer discovery/Binder evolution and final cutover | [`workstreams/activation-consumer.md`](workstreams/activation-consumer.md) |
| Durable ownership decision | [`../../adr/0015-harness-managed-control-plane.md`](../../adr/0015-harness-managed-control-plane.md) |
| Existing public consumer boundary | [`../consumer-api/README.md`](../consumer-api/README.md) |
| Shared-runtime release/evidence gates | [`../roadmap.md`](../roadmap.md) |

## Cross-repository consumer work

RedactGuard remains a pure consumer. Its repository owns only the app-side adaptation needed to discover host-published presets, inspect the source-backed setup, let the user choose among published presets, preserve the selected preset as product state and consume activation/runtime semantics. Harness remains the canonical owner of use-case definition, preset creation, model assignment, execution resolution, residency and telemetry.

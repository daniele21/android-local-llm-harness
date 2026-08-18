# ADR 0015 — Harness-managed application/use-case control plane

Status: Accepted
Date: 2026-08-18

## Context

The shared-runtime and Consumer API decisions already establish that Harness owns exact model/artifact execution while external applications consume constrained host-governed capabilities. The phone proof host, however, still contains application/use-case-specific composition, a global selected-model dependency for consumer resolution, a fixed consumer preset policy and Binder-demand warm-idle behavior. Harness telemetry also records generation runs without a first-class persistent session history and the connected phone composition still uses process-only telemetry.

This creates several product and correctness gaps: a consumer may depend on a model selected manually in Harness; app/use-case/model binding cannot be administered from Harness UI; consumers cannot receive user-created host presets; product-level consumer activity is not represented as an explicit residency owner; and the host cannot present one persistent history of all internal and external inference sessions.

The repository standard requires one owner for important mutable facts, explicit resource lifetime, observable failure reasons, progressive disclosure and actionable recovery. These concerns therefore need one Harness-owned control plane rather than additional consumer-specific patches.

## Decision

Harness owns a persistent local control plane with these responsibilities:

1. **Applications.** Authenticated caller identities are represented by a Harness application registry. Android Binder identity/signature checks remain the trust boundary; registry state governs whether an authorized identity is configured and enabled for inference.
2. **Use cases.** Use-case definitions are created and managed inside Harness. Consumers may discover assigned use cases but cannot define or mutate them.
3. **Presets.** Harness may suggest presets such as Fast, Balanced or Quality, but those names are templates rather than fixed protocol values. Users may create custom presets, version them and choose which published presets are exposed to each consumer application.
4. **Execution binding.** Consumer-visible preset metadata is separated from Harness-only execution configuration. Exact model/profile, quantization/artifact identity, context, generation profile, cache and residency settings remain host-owned. Resolution is deterministic from authenticated application + use case + published preset revision and never silently substitutes a different target.
5. **Revision pinning.** Published use-case/preset/binding revisions are immutable execution identity for already-started activations and sessions. Configuration edits affect new activation only.
6. **Activation and residency.** Product-level use of local AI is represented by an explicit host-owned activation lease distinct from a runtime session. Normal idle/warm-unload cannot evict the resident model while an activation lease owns it. After the final lease releases, configured warm retention applies. Critical pressure follows a separate explicit, observable policy.
7. **Unified observability.** Every inference executed through the shared Harness runtime, whether initiated by Harness UI, device validation or an external Binder consumer, uses one runtime telemetry path. Sessions and generation runs carry application/use-case/preset/binding/model identity and are persisted with bounded retention. Prompts, generated output, document content and private paths are excluded from normal telemetry.
8. **Decisions and notifications.** Actionable host states are represented as structured decision events with stable cause/action identity. Harness contains the persistent decision center. Android notifications are a selective delivery mechanism for actionable/warning events and deep-link to the relevant Harness decision/configuration surface; notification dismissal does not erase the underlying state.
9. **Consumer simplicity.** Ordinary consumers may choose only host-assigned use cases and host-published presets. They do not receive exact model/artifact identity, unrestricted runtime tuning, residency controls or host-wide diagnostics.
10. **Resource invariant.** The default remains one resident model and one active decode. If a different model is requested while an incompatible active lease protects the resident model, Harness fails explicitly rather than silently preempting the active consumer.

## Consequences

### Positive

- Application/use-case/model behavior becomes data-driven, persistent, inspectable and editable from Harness UI.
- New custom presets can be exposed to compatible consumers without requiring the consumer to understand or release code for a concrete model.
- Residency ownership matches product intent rather than being inferred from Binder connection or individual session lifetime.
- Internal and external inference share one host history and diagnostic model.
- Notification behavior is grounded in one durable decision source instead of ad-hoc error popups or duplicated notification state.
- RedactGuard and future consumers remain thin, host-governed clients.

### Costs

- The host gains persistent control-plane schema, revisions, migrations and UI.
- The Consumer API/Binder protocol needs an additive evolution for assigned-use-case discovery and activation lifecycle.
- Existing hardcoded RedactGuard/OMBRA bindings require migration and a compatibility window.
- Persistent session telemetry requires schema migration and restart reconciliation.

These costs are accepted because they replace existing parallel/hardcoded policy rather than layering another source of truth over it.

## Compatibility and migration

- Existing same-signer authorization remains unchanged and is not weakened by application registration.
- Consumer API evolution must be additive/version-negotiated. Supported v1.1 consumers remain compatible during the documented migration window.
- Existing `document-pii-detection` behavior is migrated into seeded/persisted control-plane data before hardcoded branches are removed.
- Existing installed model bytes are not renamed, deleted or rebound silently.
- Existing active telemetry schema is migrated non-destructively; historical rows may have null session/binding revision fields when that information did not exist.

## Alternatives considered

### Keep one hardcoded binding per consumer

Rejected. It makes Harness UI unable to administer behavior, requires code releases for model/preset changes and creates multiple sources of policy truth.

### Let consumers select concrete models

Rejected. It violates the accepted host-governed Consumer API boundary, leaks model lifecycle concerns and prevents Harness from enforcing compatibility/resource policy centrally.

### Treat Binder connection or session existence as model residency ownership

Rejected. Product-level activity can span multiple sessions and idle gaps; Binder presence is a transport fact, not a durable resource ownership contract.

### Send notifications directly from individual failures

Rejected. This would create notification spam and a second state model. Structured decision events remain the source of truth and determine whether a system notification is warranted.

## Validation

Implementation is governed by [`../shared-runtime/control-plane/roadmap.md`](../shared-runtime/control-plane/roadmap.md). Completion requires deterministic contract/persistence/lifecycle tests plus representative physical two-APK evidence for activation, residency, process death/reconnect, model conflict and persistent cross-app session history.

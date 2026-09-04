# Host Control Plane activation and consumer cutover

Status: active
Document type: feature-specification
Owner: shared-runtime-control-plane
Canonical scope: shared-runtime.control-plane.activation-consumer
Read when: implementing activation leases, lease-aware model residency, assigned-use-case/preset discovery, Binder evolution or final hardcoded-binding cutover
Last reviewed: 2026-08-18

This workstream owns HCP-17 through HCP-21 and HCP-27. It extends the accepted Consumer API without exposing exact model identity or weakening the same-signer Binder trust boundary.

## HCP-17 — Use-case activation lease

Dependencies: HCP-9 for final resolver integration; pure contract/test work may begin after HCP-0.

Represent product-level local-AI activity with an activation identity distinct from runtime session identity. An activation pins authenticated application, use case, preset revision, binding revision and resolved model identity. One activation may own multiple stateless sessions over time.

Explicit deactivate and Binder/client process death release owned activations idempotently. Ownership is isolated per authenticated connection/application.

Exit gate: "Local AI active" has an explicit host resource owner rather than being inferred from Binder presence or one open session.

## HCP-18 — Lease-aware model residency

Dependencies: HCP-17 and the deterministic resolved target from HCP-9.

Normal unload is forbidden while a compatible active lease protects the resident model. After the final lease releases, the resolved Harness warm-retention policy starts. Replace the phone service's single hardcoded Binder-demand TTL with resolved policy semantics.

Multiple leases may share the same resident model. While the one-resident-model invariant applies, an activation requesting a different model fails explicitly when the current model has protected leases; there is no silent preemption. Critical memory pressure may revoke/evict only under explicit policy and must emit typed runtime failure, telemetry and decision evidence.

Exit gate: a consumer can remain active across sessions/idle gaps without losing its model to normal warm-idle logic.

## HCP-19 — Assigned use-case discovery

Dependencies: HCP-6/HCP-7/HCP-9.

Expose only use cases assigned to the authenticated consumer. Safe metadata contains host-owned ID/display metadata/default marker/revision needed by the client; another application's bindings are never enumerable. Maintain supported v1.1 behavior during the compatibility window.

Exit gate: a consumer can discover its Harness-owned assignment rather than requiring a compiled application-specific binding.

## HCP-20 — Published preset discovery

Dependencies: HCP-7/HCP-9.

Consumer capability projection supports one or many published/exposed preset revisions, including user-created custom presets. Safe metadata contains stable preset identity, display name/description/intent as required, default marker and revision; exact model/artifact/runtime settings remain host-only.

Publication/withdrawal requires deterministic capability revision refresh. A stale selection fails with typed refresh guidance rather than being mapped to another model or preset silently.

Exit gate: publishing a custom Harness preset can make it appear in a compatible consumer without a consumer release or model knowledge.

## HCP-21 — Consumer activation protocol / Binder minor evolution

Dependencies: HCP-17/HCP-18/HCP-19/HCP-20.

Add version-negotiated Consumer API activation/deactivation. Activation binds the selected published preset revision before sessions are created. Define idempotent explicit close plus Binder/client-death cleanup. Keep AIDL DTOs at the transport edge and maintain supported old/new compatibility fixtures and packaged AAR compilation.

Exit gate: external consumers can own product-level activation without receiving concrete model identity or residency controls.

## HCP-27 — Hardcoded-binding removal and full cutover

Dependencies: HCP-9, HCP-18, HCP-20/HCP-21, Harness administration surfaces and consumer adaptation.

Migrate existing RedactGuard `document-pii-detection` behavior into persistent seeded/migrated control-plane data before deleting application-specific branches. Remove consumer dependence on phone-global `selectedModel`, fixed single-preset policy and RedactGuard-specific model/preset resolution.

Validate removal/switch of a bound model, preset breakage/repair, signer change, reconnect/process restart and configuration revision changes while an activation is live. Transfer durable behavior into architecture/API/runbooks and retire completed temporary planning material according to documentation policy.

Exit gate: all supported consumer execution is control-plane resolved and no runtime/UI branch hardcodes a consumer-to-model/preset binding.

## Cross-repository RedactGuard dependency

RedactGuard owns only its Consumer SDK adaptation: tolerate one/many published presets, choose safe host metadata, later discover assigned use case(s), adopt activation lifecycle and map host-owned failures to actions that send the user to Harness when Harness owns the fix. It must not recreate use-case/preset/model/residency configuration locally.

## Required final matrix

| Scenario | Required host result |
| --- | --- |
| one published preset | default works; consumer selector may be unnecessary |
| multiple suggested/custom presets | only exposed published safe metadata is visible |
| custom preset withdrawn | stale selection refreshes/fails explicitly |
| no app/use-case binding | actionable configuration failure |
| activation active, zero sessions | protected model stays resident |
| last activation released | configured warm retention starts |
| warm retention expires | unload with explicit reason |
| two apps, same model | compatible leases coexist |
| different-model request while protected | explicit conflict; no silent preemption |
| Binder/process death | requests/sessions/activation clean idempotently |
| critical memory pressure | explicit revoke/eviction policy and evidence |
| preset edited while active | current activation remains pinned; next activation sees new revision |
| Harness restart | consumer reconnects, rediscovers and reactivates |
| prompt/output privacy | no sensitive content in telemetry/decision/notification evidence |

Representative physical two-APK evidence is required for activation/residency/process-death claims and must bind exact host/client/runtime/model/preset/device identity.

# Host Control Plane decisions and notifications

Status: active
Document type: feature-specification
Owner: shared-runtime-control-plane
Canonical scope: shared-runtime.control-plane.decisions
Read when: implementing actionable Harness decision state, deduplication, Android notifications or Decision Center UI
Last reviewed: 2026-08-18

This workstream owns HCP-10 through HCP-13 and HCP-25. Android notifications are a delivery adapter; persistent structured decision state is the source of truth.

## HCP-10 — Decision event contract

Dependencies: HCP-0.

Define a bounded privacy-safe decision event with stable ID/code, category, optional application/use-case/preset/binding identity, timestamps, resolution state, dedupe key, recovery action and bounded evidence. Categories include action-required, warning, information and completed.

Decision events are distinct from raw structured logs and from Android `Notification` objects. Prompts, generated output, document content and private paths are never decision evidence.

Exit gate: actionable operational state has one typed domain representation.

## HCP-11 — Decision rules and deduplication

Dependencies: HCP-10.

Generate/resolve structured decisions for at least:

- newly detected or pending consumer application;
- application missing configuration;
- binding/use case/preset unavailable;
- bound model missing or incompatible;
- preset broken after model removal/change;
- protected resident-model conflict;
- critical memory-pressure eviction/revocation;
- signer change/security re-authorization.

Equivalent unresolved conditions dedupe by stable key. Resolution occurs only when the underlying condition is fixed or explicitly acknowledged where acknowledgement is meaningful. A normal single inference failure belongs in session/run history and does not create notification spam by default.

Exit gate: deterministic tests prove creation, dedupe, resolution and recurrence/re-open semantics.

## HCP-12 — Android notification adapter

Dependencies: HCP-10/HCP-11.

Project only selected action-required/warning decisions into Android notifications. Create appropriate notification channel(s), handle notification permission/state explicitly on supported Android versions, use bounded deterministic notification identities and deep-link taps to the exact Harness decision/configuration screen.

Notification dismissal must not resolve the underlying decision. Notification text contains only consumer-safe operational information; details remain in Harness.

Default projection policy:

| Event | System notification |
| --- | --- |
| New/pending consumer | yes |
| Binding/use-case/preset configuration required | yes |
| Bound model missing/broken preset | yes |
| Protected model conflict | yes |
| Critical memory eviction/revocation | yes |
| Signer change/security action | yes |
| Single generation failure | no by default |
| Model loaded/unloaded normally | no |
| Warm TTL expired | no |
| Successful inference | no |

Exit gate: actionable decisions reach the user without creating a parallel notification-state model.

## HCP-13 — Persistent Decision Center

Dependencies: HCP-10/HCP-11 plus persistent decision repository.

Persist unresolved/resolved decision history with bounded retention and queries by category, application, use case and resolution status. Actions include configuring an app/use case, repairing a preset, inspecting a model conflict, reviewing security or memory pressure, and acknowledging informational items where allowed.

Decision state survives process death and is never reconstructed from `NotificationManager` state.

Exit gate: Harness has an operational inbox that explains what needs attention, the evidence behind it and the correct next action.

## HCP-25 — Decision Center / notification UI

Dependencies: HCP-12/HCP-13.

Overview shows a compact "needs attention" summary. The Decision Center provides loading/empty/populated/error states, severity and application filters, decision detail, evidence, source code and exact action. Notification deep links land on this same state.

Notification settings control delivery preferences without hiding unresolved in-app decisions. UI must remain accessible, adaptive and source-backed.

Exit gate: users can resolve host-owned configuration/security/resource decisions without reading raw logs or visiting the consumer app for a host-owned fix.

## Focused validation

Use pure JVM tests for event invariants and decision rules, persistence tests for retention/restart behavior, and Android tests for notification permission/channel/deep-link behavior. Verify that notification/decision sentinel tests contain no prompt/output/document content. UI changes follow the phone-test product-ui/accessibility gates.

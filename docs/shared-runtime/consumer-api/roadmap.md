# Public Consumer API roadmap

Status: active
Document type: roadmap
Owner: shared-runtime-consumer-api
Canonical scope: shared-runtime.consumer-api.roadmap
Read when: selecting the next consumer-API milestone or exit gate
Last reviewed: 2026-08-13

This roadmap owns implementation order. Detailed behavior stays in the focused consumer-API specifications; shared-runtime security and physical release gates remain in the SR roadmap.

## Sequence

```text
CA-0 Boundary decisions
 -> CA-1 Capability policy
 -> CA-2 Public surface
 -> CA-3 Results and metrics
 -> CA-4 Binder integration
 -> CA-5 OMBRA reference consumer
 -> CA-6 Validation
 -> CA-7 Release readiness
```

SR-5/SR-6 and applicable Qwen3.5 physical evidence remain external dependencies for distribution.

## CA-0 — Boundary decisions

State: **DONE**

Goal: accept the durable responsibility split in [`target.md`](target.md).

Accepted decisions are recorded in ADR 0013: the consumer selects an authenticated use case rather than a model identifier; Harness owns exact model/artifact resolution; consumer tuning is preset/policy constrained; surfaced reasoning, public metric tiers, capability discovery and deterministic prepared-session semantics remain host-governed.

Exit gate: all target decisions are accepted or revised.

## CA-1 — Capability policy

State: **IN PROGRESS**

Owner: [`capabilities-and-policy.md`](capabilities-and-policy.md)

Tasks:

- define use-case readiness and capability revision without exposing model selectors or artifact identity;
- define per-app/use-case allowlists, defaults and preset compatibility;
- define reasoning/output/session capabilities and consumer limits;
- implement one host policy registry reusing existing application/use-case resolution;
- add default, allowlist, unavailable, stale-capability and privacy tests.

Exit gate: an authenticated use case resolves a deterministic, privacy-safe capability set and every advertised choice is revalidated before preparation.

## CA-2 — Public surface

State: **PLANNED**

Owner: [`public-surface-v1.md`](public-surface-v1.md)

Tasks:

- choose additive facade versus compatible `LocalLlmClient` evolution;
- add capability discovery and constrained selection/prepare types;
- bind effective selection deterministically to session/request lifecycle;
- keep manual context and raw runtime tuning out of ordinary consumer options;
- define typed public configuration/availability failures.

Exit gate: a fake/in-process consumer completes discover -> prepare -> session -> generate -> close using only public consumer types.

## CA-3 — Results and metrics

State: **PLANNED**

Owner: [`results-and-metrics.md`](results-and-metrics.md)

Tasks: freeze reasoning/answer events, terminal result identity, Tier 1 and Tier 2 metrics, timing anchors and token accounting; project from internal metrics without diagnostic leakage.

Exit gate: public content and metric semantics are deterministic and backend-neutral.

## CA-4 — Binder integration

State: **PLANNED**

Tasks:

- classify additions as SDK-only, optional protocol feature or incompatible change;
- map capability discovery, constrained selection, results and metrics across Binder;
- preserve ordering, terminal uniqueness, cancellation and transaction bounds;
- extend compatibility fixtures and packaged SDK validation.

Exit gate: packaged client SDK round-trips accepted semantics with explicit old/new compatibility behavior.

## CA-5 — OMBRA reference consumer

State: **PLANNED**

Goal: turn `apps/local-llm-console` into the pure OMBRA PDF/PII reference consumer defined by [`pii-redactor/`](pii-redactor/).

Tasks:

- remove local model/runtime/control-plane responsibilities and raw inference controls;
- use host capabilities and deterministic defaults for `document-pii-detection`;
- prove bounded text input, fixed `JSON_SCHEMA` output and typed terminal behavior in a real document workflow;
- implement the OMBRA milestones without introducing application-specific SDK or AIDL contracts;
- handle unavailable, denied, incompatible, disconnected and cancelled states.

Exit gate: OMBRA has no LLM runtime/model-management implementation, uses the packaged public client boundary and meets the OMB-7 exit gate in [`pii-redactor/roadmap.md`](pii-redactor/roadmap.md). OMB-8 validation/release work remains mapped to CA-6/CA-7.

## CA-6 — Validation

State: **PLANNED**

Owner: [`validation-and-rollout.md`](validation-and-rollout.md)

Tasks: complete policy/security/privacy tests, old/new compatibility, packaged-AAR surface checks and two-APK physical scenarios for discovery, selection, reasoning, cancellation and invalid requests.

Exit gate: deterministic gates are green and representative physical evidence supports the exact claim.

## CA-7 — Release readiness

State: **PLANNED**

Tasks: publish API reference, document SDK/protocol/capability/preset version relationships, validate shrinker/artifact metadata, add API compatibility checks when needed and complete public API/security/versioning review.

Exit gate: packaged SDK and reference consumer satisfy shared-runtime release policy.

## Recommended PR slices

| Slice | Deliverable |
| --- | --- |
| CA-0 | ADR/target decisions only |
| CA-1 | Capability domain, host policy and tests |
| CA-2 | Consumer facade and session semantics |
| CA-3 | Result/reasoning/public metrics |
| CA-4 | Binder mapping and packaged SDK |
| CA-5 | OMBRA pure-consumer migration and product UX |
| CA-6 | Security, compatibility and device evidence |
| CA-7 | API reference/version/release |

## State rule

`PLANNED` means consumers must not assume the behavior. Mark `IN PROGRESS` only after implementation begins and `DONE` only after the exit gate is integrated and tested. Physical evidence remains pending unless executed for the exact recorded identity.

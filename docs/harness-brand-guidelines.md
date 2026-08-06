# Harness brand and product-language guidance

Status: active
Document type: design-guideline
Owner: ui/design-system
Canonical scope: brand.guidelines
Read when: changing product naming, visual identity, shared UI styling, user-facing language or accessibility presentation
Last reviewed: 2026-08-06

This document defines durable brand intent and routes exact visual values to code-owned tokens and repository assets. It does not duplicate every component value or screen specification.

## Identity

- Product name: **Harness** for the runtime and design identity.
- Connected application label: **LLM Console**.
- Positioning: a precise, local-first engineering surface for explicit on-device model execution and diagnosis.
- Attributes: technical, calm, trustworthy, measurable and privacy-conscious.
- Avoid claims such as production-ready, universally compatible or private-by-default when the applicable evidence is incomplete.

## Source-of-truth order

1. Shared Compose tokens and components in `ui/design-system`.
2. Master vector assets under [`assets/brand/master/`](assets/brand/master/).
3. Android generation and packaging rules in [`android-brand-assets.md`](android-brand-assets.md).
4. Durable principles in this document.
5. Historical visual exploration in [`archive/design/2026-08-brand-guidelines.md`](archive/design/2026-08-brand-guidelines.md).

When prose and implemented tokens disagree, correct the owning code or explicitly approve a token change; do not create a second palette in documentation or a screen.

## Visual system

Harness uses a dark-first but complete light/dark Material 3 system. Purple is the primary action and identity color; teal is secondary; status colors communicate success, warning and error without relying on color alone.

Exact colors, contrast pairs and theme mapping are owned by `HarnessColors.kt` and `HarnessTheme.kt`. Exact spacing, shapes, typography aliases and minimum touch targets are owned by their corresponding files in `ui/design-system` and summarized in [`design-system.md`](design-system.md).

Screens must:

- consume `MaterialTheme` and shared Harness components instead of declaring local palette or spacing constants;
- preserve a minimum 48 dp interactive target;
- pair status color with text, iconography or another non-color cue;
- support system dark/light selection unless a test or preview explicitly forces a mode;
- use the shared offline font aliases and never download fonts at runtime;
- keep hierarchy clear through typography and spacing rather than ornamental containers.

## Logo and launcher assets

The canonical symbol, wordmark and lockup are the outlined SVG masters under `docs/assets/brand/master`. Android icons are generated deterministically; generated application resources must not be edited by hand.

The symbol needs sufficient safe area for circular, rounded-square and squircle masks. Monochrome Android variants retain recognizable geometry without encoding meaning only through the purple/teal palette.

## Components and interaction

- Primary actions are reserved for the main safe next step.
- Destructive actions require explicit wording and confirmation where reversal is difficult.
- Stop and cancel actions remain available during long-running inference or transfer operations.
- Cards group related information; they are not the default wrapper for every line of content.
- Status badges use the shared semantic tone system.
- Metrics use stable labels, units and comparable precision.
- Charts provide accessible labels and a textual interpretation of the important result.
- Motion explains state transition, progress or hierarchy; it must remain restrained and respect reduced-motion behavior where available.

## Product language

Use direct, specific language:

- name the operation and object: “Download model”, “Load into memory”, “Stop generation”;
- distinguish downloaded, installed, selected and loaded states;
- describe what recovery action is available after a failure;
- avoid anthropomorphic or promotional wording in diagnostics;
- never imply that emulator validation is physical-device evidence;
- never expose signed URLs, private paths, prompts or generated output in diagnostics copy or examples.

Error messages should identify the failed operation, give a safe concise reason and offer the next valid action. Internal exception text, native pointers and backend structures are not user-facing copy.

## Screen application

- **Overview:** summarize readiness and the next relevant action without duplicating Diagnostics.
- **Playground:** prioritize prompt entry, explicit generation controls, stop behavior and safe output presentation.
- **Models:** make catalog, download, installation, selection and residency visibly distinct.
- **Diagnostics:** present logs, health, resources and benchmarks with provenance and evidence level.
- **Settings:** expose durable preferences and links to detail screens without becoming a status ledger.

Detailed screen acceptance criteria belong in [`harness-ux-ui-implementation-plan.md`](harness-ux-ui-implementation-plan.md). Application architecture belongs in [`features/phone-app-architecture.md`](features/phone-app-architecture.md).

## Change rule

A brand change is complete only when the owning token or master asset, dark/light behavior, accessibility checks, previews, generated resources and relevant documentation agree. New one-off screen constants or duplicated brand tables are rejected.

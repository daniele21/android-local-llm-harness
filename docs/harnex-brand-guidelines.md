# Harnex brand and product-language guidance

Status: active
Document type: design-guideline
Owner: ui/design-system
Canonical scope: brand.guidelines
Read when: changing product naming, visual identity, shared UI styling, user-facing language or accessibility presentation
Last reviewed: 2026-09-01

This document defines durable Harnex brand intent and routes exact visual values to code-owned tokens and repository assets. It does not duplicate every component value or screen specification.

## Identity

- Product name: **Harnex**.
- Payoff: **Your local AI harness for Android.**
- Name rationale: Harnex combines the idea of a local AI *harness* with a *nexus* connecting Android consumer applications to local models and runtime capabilities.
- Positioning: a precise, local-first Android harness for explicit on-device AI execution, model lifecycle, connection and diagnosis.
- Attributes: technical, calm, trustworthy, measurable and privacy-conscious.
- Avoid claims such as production-ready, universally compatible or private-by-default when the applicable evidence is incomplete.

The repository name `android-local-llm-harness` and implementation identifiers prefixed with `Harness` are compatibility/engineering identifiers. They do not override the public product name **Harnex**.

## H Bridge Core invariant

The approved symbol is **H Bridge Core**. It must not be redrawn, simplified through approximation or regenerated from the retired SVG geometry.

The mark is intentionally an `H`:

- the purple side represents one side of the application/runtime boundary;
- the teal side represents the complementary local-AI side;
- the three horizontal bridge lines make connection the primary visual idea;
- the nodes express controlled exchange across that boundary;
- Android is a secondary platform cue, not the dominant symbol.

The approved PNG symbol, its geometry, proportions, bridge structure, nodes and color treatment are brand invariants.

## Source-of-truth order

1. Shared Compose tokens and components in `ui/design-system` for implemented theme/component values.
2. Canonical Harnex PNG masters under [`assets/brand/master/`](assets/brand/master/).
3. Machine-readable product identity in [`../design/brand-kit.json`](../design/brand-kit.json).
4. Android generation and packaging rules in [`android-brand-assets.md`](android-brand-assets.md).
5. Durable principles in this document.
6. Provenance under [`assets/brand/reference/harnex/`](assets/brand/reference/harnex/) and [`assets/brand/reference/hbridge-core/`](assets/brand/reference/hbridge-core/).
7. Historical visual exploration in [`archive/design/2026-08-brand-guidelines.md`](archive/design/2026-08-brand-guidelines.md).

When prose and implemented tokens disagree, correct the owning code or explicitly approve a token change; do not create a second palette in documentation or a screen.

## Logo system

Canonical variants are:

- `harnex-symbol.png` — standalone H Bridge Core mark;
- `harnex-lockup-light.png` / `harnex-lockup-dark.png` — primary lockups with payoff;
- `harnex-lockup-compact-light.png` / `harnex-lockup-compact-dark.png` — compact lockups without payoff;
- `harnex-wordmark-light.png` / `harnex-wordmark-dark.png` — wordmark-only variants;
- `harnex-app-icon-light.png` / `harnex-app-icon-dark.png` — app-icon treatments using the unchanged mark.

Use the primary lockup when enough horizontal space exists and the brand needs explanation. Use the compact lockup when the context already makes the product purpose clear. Use the symbol alone for launcher, favicon and constrained icon surfaces.

Older `harness-*` PNG/SVG assets are compatibility/history only and must not be used for new product-facing work.

## Visual system

Harnex uses the existing dark-first but complete light/dark Material 3 system. Purple remains the primary action/identity family; teal remains the secondary/local-connection family; status colors communicate success, warning and error without relying on color alone.

Exact colors, contrast pairs and theme mapping remain owned by the current `HarnessColors.kt` and `HarnessTheme.kt` implementation identifiers until a separately scoped internal rename is justified. Exact spacing, shapes, typography aliases and minimum touch targets are owned by the corresponding files in `ui/design-system` and summarized in [`design-system.md`](design-system.md).

Screens must:

- consume `MaterialTheme` and shared design-system components instead of declaring local palette or spacing constants;
- preserve a minimum 48 dp interactive target;
- pair status color with text, iconography or another non-color cue;
- support system dark/light selection unless a test or preview explicitly forces a mode;
- use shared offline font aliases and never download fonts at runtime;
- keep hierarchy clear through typography and spacing rather than ornamental containers.

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

Detailed screen acceptance criteria remain in [`harness-ux-ui-implementation-plan.md`](harness-ux-ui-implementation-plan.md). Application architecture remains in [`features/phone-app-architecture.md`](features/phone-app-architecture.md).

## Change rule

A repository-brand change is complete only when the canonical assets, light/dark presentation copies, brand contract, generators and documentation agree. A product-surface brand migration additionally requires the relevant Compose/UI copy, accessibility presentation, previews, screenshots and E2E evidence to agree on the same exact head.

New one-off screen constants, recreated logo geometry or duplicated brand tables are rejected.

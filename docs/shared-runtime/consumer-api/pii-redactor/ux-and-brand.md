# OMBRA UX and brand implementation

Status: active
Document type: design-guideline
Owner: apps/local-llm-console
Canonical scope: shared-runtime.consumer-api.pii-redactor.ux-brand
Read when: implementing OMBRA identity, Compose theme, components, navigation, screen states or visual regression fixtures
Last reviewed: 2026-08-13

## Purpose and reference hierarchy

This document translates the generated OMBRA visual direction into an implementable Android contract. It does not replace behavioral rules in [`target.md`](target.md) or the pipeline in [`detection-and-redaction.md`](detection-and-redaction.md).

Source-of-truth order during implementation:

1. implemented OMBRA Compose tokens, components and reviewed vector masters;
2. this screen and accessibility contract;
3. the generated [UX flow](../assets/ombra-consumer-app-ux-flow.png) and [brand kit](../assets/ombra-brand-kit.png);
4. incidental pixel details or illustrative copy in a raster board.

The boards are design references, not proof of working behavior. Runtime values, filenames, page counts and detected PII always come from real state.

## Brand boundary

**OMBRA** is the consumer application. **Harness** is the separate local-inference host. OMBRA uses the phrase “Harness connesso” only as a secondary runtime status and never inherits the host's purple engineering-console identity.

Brand attributes:

- calm and private;
- local and transparent;
- human-reviewed;
- precise without appearing forensic or threatening.

Avoid shields, padlocks as hero imagery, brains, robots, sparkles, hacker motifs, fear-based copy and unqualified compliance claims.

## Token contract

The reference light palette is:

| Token | Value | Role |
| --- | --- | --- |
| `OmbraInk` | `#15201D` | primary text, dark mark and high-emphasis content |
| `LocalMoss` | `#315C4F` | primary brand/action foundation |
| `SignalMint` | `#65D6A6` | interactive accent and positive progress |
| `Paper` | `#F6F4EE` | application background |
| `SoftSage` | `#DDE9E2` | quiet surfaces and selected containers |
| `ReviewAmber` | `#E6A94A` | review/incomplete attention state |
| `ErrorCoral` | `#D8655B` | error/destructive state |

These values are initial reference inputs, not permission to hardcode colors in screens. Implementation adds a separate `OmbraTheme`/`OmbraColorScheme` namespace in `ui/design-system`, maps all Material roles explicitly and verifies text/icon/container contrast.

The final dark palette must be derived and contrast-tested before release. Do not invert the light colors mechanically. Until dark tokens and screenshots pass, OMBRA may be light-only as an experimental slice but cannot claim repository design-system completion.

## Typography

The visual direction uses:

- Manrope, semibold/bold, for display and primary headings;
- Inter for interface labels and body content;
- a monospaced/system-safe style only for placeholders when it materially improves scanning.

The application remains offline-first. Implementation must either:

1. bundle reviewed font files with documented redistribution licenses, glyph coverage, APK impact and fallback behavior; or
2. map OMBRA typography to deterministic Android system families while preserving scale and weight.

No runtime font download is allowed. Until bundled-font review is accepted, screenshots should use the deterministic fallback rather than claiming exact Manrope/Inter rendering.

Recommended semantic scale:

| Role | Target use |
| --- | --- |
| display/headline | task title and completion state |
| title | section and bottom-sheet headings |
| body | definitions, helper text and document preview |
| label | buttons, chips, counters and metadata |

Use Material semantic roles; do not set arbitrary `sp` values throughout screen code. Large font sizes up to at least 200% must reflow without hiding the main action or review controls.

## Shape, spacing and iconography

- Base spacing rhythm: 4, 8, 12, 16, 24, 32 and 48 dp.
- Interactive targets: at least 48 dp in both dimensions.
- Small controls/chips: approximately 8 dp corner radius.
- Fields, rows and buttons: approximately 12 dp.
- Prominent sheet/surface: approximately 16 dp.
- Cards group genuine concepts; they are not a wrapper for every row.
- Elevation is restrained; hierarchy relies on whitespace, type and surface tone.

Use Material Symbols or repository-owned outline vector icons with consistent optical weight. Every icon-only action has a localized content description. Status is always expressed with icon/label in addition to color.

## Logo and Android assets

The reference mark combines a folded document corner, an opaque redaction bar and a mint reveal slit. Production work must recreate it as simple reviewed vectors rather than auto-tracing the PNG board.

Planned canonical masters:

```text
docs/assets/consumer/ombra/master/ombra-symbol.svg
docs/assets/consumer/ombra/master/ombra-wordmark.svg
docs/assets/consumer/ombra/master/ombra-lockup.svg
```

Implementation must then generate and verify:

- adaptive foreground/background launcher icon;
- Android 13 monochrome icon;
- app-bar/wordmark vector where needed;
- light/dark lockups;
- Play listing preview assets only after product naming is accepted.

The launcher generator/packaging check must be deterministic. Do not hand-edit generated mipmap/drawable outputs and do not reuse Harness vector masters under a different color.

## Redaction visual language

Three visually and semantically separate states are required:

- **candidate hidden:** opaque moss block with placeholder label such as `[EMAIL_1]`;
- **candidate revealed:** original value on a review-attention container, announced as sensitive content;
- **ignored:** normal document text with an explicit ignored state in the finding inspector.

The preview is a representation of normalized export content, not a pixel-perfect renderer of the source PDF. Hidden values must not remain in Compose semantics, accessibility descriptions or clipboard data. Reveal content enters semantics only while shown and is removed again when hidden.

Animations may explain hide/reveal and progress, but they must respect reduced-motion preferences and never flash the original value during transition.

## Navigation model

OMBRA is one linear task without bottom navigation:

```text
Import
 -> Definitions
 -> Custom definition sheet? -> Definitions
 -> Analysis
 -> Review
 -> Export success
```

Back behavior:

- Definitions -> Import with confirmation only when discarding extracted work is material.
- Custom sheet -> Definitions without losing selection.
- Analysis -> cancellation confirmation while work is active.
- Review -> Definitions/reanalysis confirmation because findings will be discarded.
- Export success -> new analysis/reset or system exit.

Routes do not carry document text, URI, PII values or serialized findings. The ViewModel graph owns in-memory task state.

## Screen 1 — Import

Primary content:

- OMBRA app bar/mark;
- “Proteggi un documento”;
- concise local-processing explanation;
- one PDF selection surface;
- primary `Importa PDF` action;
- truthful local readiness/connection summary.

States:

- Harness availability checking;
- ready;
- host missing/denied/incompatible/disconnected;
- picker cancelled;
- PDF reading;
- invalid, encrypted, image-only or unsupported PDF.

The user may select a PDF before Harness becomes ready, but analysis remains unavailable with a specific recovery action. No recent-document list, content history or background import is shown.

## Screen 2 — Definitions

Show the selected document descriptor followed by built-in definition rows. Each row contains a label, concise definition and accessible selection control. The primary `Analizza documento` action is enabled only when extraction is complete, Harness capability is ready and at least one valid definition is selected.

`+ Aggiungi PII personalizzato` opens the sheet. Definitions are not inference presets and the screen contains no model or sampler selector.

Required states include loading extraction metadata, usable selection, definition-limit reached, invalid custom definition and document/capability limit that prevents analysis.

## Screen 3 — Custom definition sheet

Fields:

- `Nome`;
- `Definizione`;
- optional `Esempio`.

Use inline validation and remaining-length/count feedback where useful. `Aggiungi` remains disabled until all required validation passes. The sheet warns that the definition guides detection but does not guarantee a match. Cancel returns without mutating the active set.

Keyboard navigation, IME actions, focus order and error announcements must be deterministic. The sheet expands or scrolls above the software keyboard.

## Screen 4 — Analysis

Show real phase and chunk/page progress, not an indeterminate “AI magic” loop:

```text
Testo estratto      complete
PII in analisi      active, chunk/page n of total
Preparazione anteprima pending/complete
```

`Harness connesso` is secondary status. `Annulla` remains visible and usable throughout extraction/inference/merge. The document motif may animate a restrained scan/redaction transition without exposing text.

Required terminal branches:

- completed and navigates to review;
- cancelled;
- host disconnected/model unavailable;
- invalid structured result;
- partial/incomplete chunk analysis;
- source document read failure.

No prompt, raw JSON or chain of thought is shown.

## Screen 5 — Review

Primary hierarchy:

- candidate count and incomplete/conflict warnings;
- `Oscurati`/`Rivela` view control;
- normalized document preview with placeholders;
- focused finding inspector with category and current original value only when revealed;
- `Accetta`/`Ignora` decisions;
- sequence position and previous/next controls;
- warning to verify results;
- `Continua` after all blocking conflicts are resolved.

The preview scroll position and focused occurrence are stable while toggling reveal. Findings can be navigated without precision tapping on text. TalkBack announces type, occurrence position, hidden/revealed state and decision without reading hidden PII.

Incomplete chunks, invalid outputs and overlap conflicts cannot be disguised as a normal complete result. Export stays blocked until the user retries or takes an explicitly defined safe exit.

## Screen 6 — Export

Before destination selection show a summary of accepted, ignored and page counts. The system create-document flow owns destination and final naming. After successful write show:

- `Documento pronto`;
- safe summary counts;
- output display name when safe;
- `Apri documento` or share action only when the returned URI grant supports it;
- `Nuova analisi` which clears sensitive task state;
- truthful privacy footer.

`Scarica PDF anonimizzato` in the mockup maps to the Android create-document/export action; avoid implying a network download. Exporting, success, destination denied, write failure and cleanup failure each have explicit states.

A secondary `Dettagli analisi` action may expose host Tier 1 metrics and approved request details after completion; it stays outside the primary six-screen hierarchy and contains no prompt, JSON, document excerpt or Harness-wide diagnostics.

## Component inventory

Add only components with repeated semantic ownership:

- `OmbraScaffold` and task app bar;
- primary/secondary/destructive action styles;
- document picker surface;
- PII definition selection row;
- definition editor sheet;
- task progress step;
- Harness connection badge;
- redacted text/placeholder span;
- finding inspector and decision control;
- review warning/banner;
- export summary.

One-off screen arrangements remain local. Shared primitives must consume OMBRA/Material tokens and provide semantics/loading/disabled behavior centrally.

## Adaptive and accessibility requirements

- Compact portrait matches the six-screen board's hierarchy.
- Landscape and expanded widths constrain readable content rather than stretching document text edge to edge.
- Insets, cutouts and IME are handled explicitly.
- All actions meet 48 dp minimum targets and WCAG AA contrast.
- Focus order follows the task and remains stable after state updates.
- Dynamic type reflows; sticky actions must not cover content.
- Screen readers never announce hidden sensitive values.
- Error, progress and completion changes use polite announcements where appropriate.
- Color-blind operation is supported through labels/icons/pattern-independent status.
- Motion is optional and reduced-motion safe.

## Screenshot and preview matrix

Code-owned previews and regression screenshots should cover:

- all six happy-path screens at compact portrait;
- import host-missing and PDF-unsupported errors;
- definition selection with custom sheet and validation error;
- analysis active/cancelling/partial failure;
- review hidden, revealed, ignored and overlap conflict;
- export writing/success/failure;
- compact large-font and landscape/expanded representative states;
- light theme and final accepted dark theme.

Fixtures use synthetic names, emails and financial identifiers clearly labeled as test data. They do not reuse real user documents or production output.

## Completion criteria

- Implemented screens are recognizably aligned with both reference boards in hierarchy, palette and redaction language.
- Exact visual constants live in OMBRA design-system tokens rather than screen code.
- Production vector/logo assets replace raster extraction.
- Every loading, empty, unavailable, error, cancellation, partial, review and export state is designed and tested.
- Accessibility and adaptive-layout gates pass with hidden PII excluded from semantics.
- Mockups remain documentation references and are never shipped as UI or represented as device evidence.

---
name: design-product-experience
description: Design or reshape meaningful product UX/UI in the correct decision order before implementation. Use for new screens, navigation, workflows, onboarding, major settings/dashboard changes, interaction redesigns, adaptive behavior, motion systems or other user-facing changes whose semantics matter. Keep visual-only changes proportional; do not force a full UX exercise for a local token/style edit.
---

# Design Product Experience

## Goal

Turn a user-facing product change into an implementable experience decision without jumping directly to layout, components, animation or visual polish.

The governing order is:

```text
USER OUTCOME
-> TASK MODEL
-> INFORMATION ARCHITECTURE / CRITICAL JOURNEY
-> INFORMATION + ACTION HIERARCHY
-> PROGRESSIVE DISCLOSURE / DEFAULTS
-> INTERACTIONS + STATES + FEEDBACK + RECOVERY
-> ADAPTIVE / PLATFORM BEHAVIOR
-> ACCESSIBILITY
-> DESIGN SYSTEM / COMPONENTS
-> MOTION
-> VISUAL POLISH / GRAPHICS
-> VALIDATION
```

Later layers may refine earlier decisions, but they must not silently substitute for unresolved earlier ones.

## First classify the change

Choose the smallest justified depth.

### Structural UX

Examples: new screen/surface, navigation model, onboarding, new workflow, major dashboard/settings redesign, changed information architecture.

Use the full sequence from user outcome through validation.

### Interaction

Examples: dialog/sheet behavior, selection, drag/drop, progress, error recovery, state transition, gesture or interaction model.

Start from the owning user task/journey, then reason through interaction, states, feedback, accessibility, adaptive behavior, component ownership and motion. Re-open IA/hierarchy only if the interaction changes them.

### Visual-only

Examples: typography refinement, spacing, semantic color application, radius/elevation, icon treatment or an implementation-neutral visual cleanup that does not change task flow or interaction semantics.

Start from the existing design-system/brand owner and validate hierarchy/accessibility. Do not invent a new flow, component semantic role or motion system merely because visual code is being touched.

**Depth of UX reasoning must match the semantic impact of the change.**

## Workflow

### 1. Establish user outcome

Identify:

- who is using the surface;
- the job/decision they are trying to complete;
- the successful outcome;
- constraints that materially shape the experience.

Do not start with "we need a card/modal/sidebar" unless the component itself is the already-settled requirement.

### 2. Model the task, not the implementation

List the concepts/actions the user must understand. Keep backend/service/runtime concepts hidden unless they create real user value or the product is explicitly an expert tool that needs them.

Ask: **what does the user think they are doing?**

### 3. Define IA and the critical journey

For structural changes, define the smallest coherent path:

```text
entry -> decision -> action -> feedback -> outcome -> next step/recovery
```

Name the primary surface(s), navigation relationship and where context must persist.

### 4. Establish information and action hierarchy

For each affected surface identify:

- primary information;
- primary action;
- secondary/contextual information/actions;
- destructive actions;
- what can remain out of the default view.

A screen should not make multiple unrelated actions look equally dominant without a deliberate reason.

### 5. Apply progressive disclosure and defaults

Classify complexity as:

```text
essential -> contextual -> advanced -> expert/diagnostics
```

Normal use should work with strong defaults before requiring deep configuration. Expert capability stays discoverable without dominating the common path.

### 6. Design interaction states, feedback and recovery

For critical actions/components cover reachable states such as:

```text
default / hover / focus / pressed / selected / disabled
loading / empty / success / warning / error
partial / offline / permission-denied
```

For every meaningful action define:

```text
action -> acknowledgement -> progress/status -> outcome -> next action/recovery
```

Prefer prevention and recoverability over generic failure messages or unnecessary confirmation dialogs.

### 7. Map to platform and available space

Define relevant window/device/input contexts. Preserve content priority rather than shrinking a desktop composition until it fits.

Use platform-native interaction expectations for touch, mouse, keyboard, navigation, gestures, focus and window resizing. Cross-platform consistency means consistent product semantics, not identical interaction mechanics everywhere.

### 8. Design accessibility as behavior

Check applicable keyboard/focus order, semantic labels, text scaling, target size, contrast, non-color meaning, screen-reader/status announcements and reduced-motion behavior before visual polish is considered complete.

### 9. Reuse the design system

Find the canonical semantic component/token owner before creating a new component or raw style value.

Create a new semantic component only when the existing system cannot express the required role without harming usability or maintainability.

### 10. Add motion only with purpose

Every meaningful animation should serve at least one product purpose:

- feedback;
- continuity;
- spatial relationship;
- state transition;
- progress;
- attention;
- hierarchy;
- meaningful completion.

Frequent interactions should use restrained motion. Gesture-driven motion should track the input directly. Large navigation changes should preserve spatial/semantic continuity where it helps orientation. Prefer smooth/simple motion over decorative complexity, and respect reduced-motion settings.

Do not use animation to hide an unclear hierarchy, weak flow or missing feedback model.

### 11. Add visual polish and graphics last

Use typography, spacing, semantic color, elevation, iconography, imagery and graphics to reinforce already-decided hierarchy and meaning.

Decorative imagery must not be required to understand or operate functional UI. Graphics are strongest when they explain, orient, support onboarding/empty states/status, communicate data or express brand without competing with the task.

### 12. Define evidence before completion

Match evidence to the claim:

- component/state tests for deterministic local behavior;
- integration for boundaries;
- E2E for critical complete journeys lower levels cannot prove;
- accessibility checks/manual evidence where relevant;
- representative adaptive contexts;
- visual regression for stable high-risk surfaces;
- usability evidence for important/high-risk workflow claims.

A polished screenshot does not prove interaction quality, recovery, accessibility or usability.

## Output discipline

For a meaningful UX/UI task, leave an implementation-ready decision that is concise enough to act on. Capture only durable project truth in `design/ux-contract.json`, `design/brand-kit.json`, the canonical design source or owning feature docs. Do not create a permanent UX plan for every change.

If the request is explicitly visual-only and the existing task/flow is sound, preserve it and stay local.

## Stop conditions

Surface the conflict instead of improvising when the requested design would:

- contradict the declared user task/critical journey;
- expose internal architecture without user value;
- bypass required accessibility or recovery behavior;
- create a second design/token/component source of truth;
- use motion/graphics to compensate for unresolved structure;
- break platform expectations without a documented product reason.

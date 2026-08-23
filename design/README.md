# Harness product design contract

This directory is the repo-template-sw `product-ui` contract/routing layer for Harness. It does not replace the code-first design system or duplicate its exact tokens.

Canonical files:

- `ux-contract.json` — machine-readable product-experience context, decision model, critical journeys, motion/graphics semantics and validation expectations;
- `brand-kit.json` — semantic routing to the real visual/token owners plus Harness motion language;
- `reference/README.md` — bounded pointers to key product views and state specifications.

Meaningful UX/UI work follows `skills/design-product-experience/SKILL.md` with proportional depth. The governing order is user outcome -> task model -> information architecture/critical journey -> hierarchy -> progressive disclosure/defaults -> interactions/states/feedback/recovery -> adaptive/platform behavior -> accessibility -> design system/components -> motion -> visual polish/graphics -> validation.

Canonical design ownership remains code-first in `ui/design-system`, with durable product-language guidance in `docs/harness-brand-guidelines.md`, screen behavior in `docs/harness-ux-ui-implementation-plan.md`, application architecture in `docs/features/phone-app-architecture.md`, and master vector assets in `docs/assets/brand/master/`.

Motion and graphics must reinforce resolved structure rather than compensate for it. Where Harness has no dedicated numeric motion token, `brand-kit.json` explicitly retains Compose/Material platform defaults instead of inventing a parallel token source.

Do not add generated screenshots, regression history, duplicate palettes, copied component tables or alternate mockup revisions here. CI/device visual evidence remains bounded evidence/artifacts with build/source identity.

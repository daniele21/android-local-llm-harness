# Harnex brand assets

Status: active
Document type: asset-index
Owner: ui/design-system
Canonical scope: brand.assets
Read when: locating source or rendered brand assets
Last reviewed: 2026-09-01

**Harnex** uses the approved **H Bridge Core** identity with the payoff **“Your local AI harness for Android.”** The symbol geometry is unchanged from the approved H Bridge Core mark. Canonical sources are PNG masters under `docs/assets/brand/master/`; no SVG conversion or recreation is required.

## Canonical PNG masters

- `master/harnex-symbol.png` — compact H Bridge Core symbol with transparent background.
- `master/harnex-lockup-light.png` — primary horizontal lockup with payoff for light/transparent surfaces.
- `master/harnex-lockup-dark.png` — primary horizontal lockup with payoff for dark surfaces.
- `master/harnex-lockup-compact-light.png` — compact lockup for light surfaces.
- `master/harnex-lockup-compact-dark.png` — compact lockup for dark surfaces.
- `master/harnex-wordmark-light.png` — Harnex wordmark for light surfaces.
- `master/harnex-wordmark-dark.png` — Harnex wordmark for dark surfaces.
- `master/harnex-app-icon-light.png` — light launcher/icon treatment.
- `master/harnex-app-icon-dark.png` — dark launcher/icon treatment.

The approved Harnex naming outputs are preserved in [Harnex source references](reference/harnex/README.md). The earlier H Bridge Core source material remains in [H Bridge Core source references](reference/hbridge-core/README.md).

## Synced presentation assets

Dark mode:

- `dark/logo-lockup.png`
- `dark/symbol.png`
- `dark/app-icon.png`
- `dark/favicon.png`

Light mode:

- `light/logo-lockup.png`
- `light/symbol.png`
- `light/app-icon.png`
- `light/favicon.png`

Run `python3 scripts/generate_brand_assets.py` to sync these presentation copies from the canonical Harnex PNG masters.

Older `harness-*` PNG aliases and SVG files under `master/` are retained only as legacy compatibility artifacts. They are not the source of truth for current Harnex branding or Android launcher generation.

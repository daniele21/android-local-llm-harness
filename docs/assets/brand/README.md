# Harness brand assets

Status: active
Document type: asset-index
Owner: ui/design-system
Canonical scope: brand.assets
Read when: locating source or rendered brand assets
Last reviewed: 2026-08-30

Harness uses the approved **H Bridge Core** identity. The canonical sources are PNG masters under `docs/assets/brand/master/`; no SVG conversion is required.

## Canonical PNG masters

- `master/harness-symbol.png` — compact H Bridge Core symbol with transparent background.
- `master/harness-lockup-light.png` — horizontal lockup for light/transparent surfaces.
- `master/harness-lockup-dark.png` — horizontal lockup for dark surfaces.
- `master/harness-app-icon-light.png` — light launcher/icon treatment.
- `master/harness-app-icon-dark.png` — dark launcher/icon treatment.

The original approved outputs are preserved in the [H Bridge Core source references](reference/hbridge-core/README.md) for provenance.

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

Run `python3 scripts/generate_brand_assets.py` to sync these presentation copies from the canonical PNG masters.

The older SVG files under `master/` are retained only as legacy compatibility artifacts. They are not the source of truth for the current brand or Android launcher.

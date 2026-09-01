# Harnex PNG masters

Status: active
Document type: asset-index
Owner: ui/design-system
Canonical scope: brand.assets.masters
Read when: editing or consuming the canonical Harnex identity
Last reviewed: 2026-09-01

The product brand is **Harnex** with the payoff **“Your local AI harness for Android.”** The approved mark remains **H Bridge Core** and is intentionally unchanged by the naming transition.

The mark is an `H`: the two sides represent the consumer application and the local LLM/runtime boundary, while the three horizontal bridge lines make their connection the primary visual idea. Android is a secondary platform cue rather than the dominant symbol.

## Canonical files

- `harnex-symbol.png` — compact transparent H Bridge Core mark; geometry is invariant.
- `harnex-lockup-light.png` — primary horizontal lockup with payoff for light/transparent surfaces.
- `harnex-lockup-dark.png` — primary horizontal lockup with payoff for dark surfaces.
- `harnex-lockup-compact-light.png` — compact light lockup without payoff.
- `harnex-lockup-compact-dark.png` — compact dark lockup without payoff.
- `harnex-wordmark-light.png` — wordmark for light surfaces.
- `harnex-wordmark-dark.png` — wordmark for dark surfaces.
- `harnex-app-icon-light.png` — light app-icon treatment using the unchanged H Bridge Core mark.
- `harnex-app-icon-dark.png` — dark app-icon treatment using the unchanged H Bridge Core mark.

The original uploaded Harnex outputs are preserved under `../reference/harnex/`. The earlier H Bridge Core provenance remains under `../reference/hbridge-core/`.

Existing `harness-*.png` aliases and historical `harness-*.svg` files are retained only for compatibility with older links and repository history. New product references must use the `harnex-*` PNG masters above. Internal Kotlin/design-system identifiers such as `HarnessTheme` remain implementation names and are not alternative public branding.

Run:

```bash
python3 scripts/generate_brand_assets.py
python3 scripts/generate_android_brand_assets.py
python3 scripts/generate_android_brand_assets.py --check
```

The Android check validates the canonical PNG identity, deterministic launcher XML and manifest linkage.

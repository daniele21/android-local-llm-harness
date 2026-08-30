# Harness PNG masters

Status: active
Document type: asset-index
Owner: ui/design-system
Canonical scope: brand.assets.masters
Read when: editing or consuming the canonical Harness identity
Last reviewed: 2026-08-30

The approved Harness identity is **H Bridge Core**. Its repository-owned source of truth is the PNG set in this directory.

The mark is intentionally an `H`: the two sides represent the consumer application and the local LLM/runtime boundary, while the three bridge lines make their connection the primary visual idea. Android is a secondary platform cue rather than the dominant symbol.

Canonical files:

- `harness-symbol.png` — compact transparent H Bridge Core mark.
- `harness-lockup-light.png` — primary horizontal lockup for light/transparent surfaces.
- `harness-lockup-dark.png` — horizontal lockup for dark surfaces.
- `harness-app-icon-light.png` — light app-icon treatment.
- `harness-app-icon-dark.png` — dark app-icon treatment.

The historical `harness-symbol.svg`, `harness-wordmark.svg`, and `harness-lockup.svg` files are retained only for compatibility with old links/history. New references must use the PNG masters above.

Run:

```bash
python3 scripts/generate_brand_assets.py
python3 scripts/generate_android_brand_assets.py
python3 scripts/generate_android_brand_assets.py --check
```

The Android check validates PNG identity, deterministic launcher XML and manifest linkage.

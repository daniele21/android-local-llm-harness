# Harness vector masters

Status: active
Document type: asset-index
Owner: ui/design-system
Canonical scope: brand.assets.masters
Read when: editing or regenerating the canonical symbol, wordmark or lockup vectors
Last reviewed: 2026-08-06

These SVG files are the repository-owned vector masters for the Harness identity.

- `harness-symbol.svg` is the canonical colored symbol and the only source used to generate Android launcher resources.
- `harness-wordmark.svg` stores the outlined wordmark.
- `harness-lockup.svg` combines the canonical symbol and outlined wordmark.

The outlined lettering matches the existing approved PNG generator. No font binary is stored in the repository.

Run:

```bash
python3 scripts/generate_android_brand_assets.py
python3 scripts/generate_android_brand_assets.py --check
```

The check validates path order, approved colors, vector-only masters, adaptive-icon safe-zone bounds, generated Android XML and manifest linkage.

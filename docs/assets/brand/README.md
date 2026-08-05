# Harness brand assets

Repository-owned assets for the Harness visual identity.

## Vector masters

- `master/harness-symbol.svg`
- `master/harness-wordmark.svg`
- `master/harness-lockup.svg`

The symbol master is the canonical runtime source for Android launcher assets. Wordmark and lockup use outlined paths so they do not depend on a font installed on the build machine.

## PNG references

### Dark mode

- `dark/logo-lockup.png`
- `dark/symbol.png`
- `dark/app-icon.png`
- `dark/favicon.png`
- `dark/component-sheet.png`

### Light mode

- `light/logo-lockup.png`
- `light/symbol.png`
- `light/app-icon.png`
- `light/favicon.png`
- `light/component-sheet.png`

The PNG files remain visual references and documentation assets; they are not the only runtime source.

## Regeneration

```bash
python3 scripts/generate_brand_assets.py
python3 scripts/generate_android_brand_assets.py
python3 scripts/generate_android_brand_assets.py --check
```

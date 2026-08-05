# Harness brand assets

Raster assets for the Harness product identity.

## Structure

```text
brand/
├── dark/
│   ├── logo-lockup.png
│   ├── symbol.png
│   ├── app-icon.png
│   ├── favicon.png
│   └── component-sheet.png
└── light/
    ├── logo-lockup.png
    ├── symbol.png
    ├── app-icon.png
    ├── favicon.png
    └── component-sheet.png
```

## Intended use

- `logo-lockup.png`: full logo with wordmark, descriptor and tagline.
- `symbol.png`: standalone geometric H mark.
- `app-icon.png`: rounded-square product icon.
- `favicon.png`: simplified compact icon for small digital contexts.
- `component-sheet.png`: visual reference for buttons, badges, telemetry chips, navigation, cards and line icons.

The dark and light folders contain theme-specific variants. The source identity and usage rules remain documented in `docs/harness-brand-guidelines.md`.

These are raster reference assets. Android production resources should derive the required density-specific outputs from the approved app icon and symbol rather than scaling them at runtime.

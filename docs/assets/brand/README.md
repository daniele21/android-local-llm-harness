# Harness brand assets

The graphical assets in this directory are generated from the approved Harness brand direction.

The source generator creates both dark-mode and light-mode PNG assets:

- logo lockup;
- standalone symbol;
- Android-style app icon;
- favicon;
- UI component sheet.

Regenerate locally with:

```bash
python -m pip install Pillow==11.3.0
python scripts/generate_brand_assets.py
```

Generated output:

```text
docs/assets/brand/
├── dark/
│   ├── app-icon.png
│   ├── component-sheet.png
│   ├── favicon.png
│   ├── logo-lockup.png
│   └── symbol.png
└── light/
    ├── app-icon.png
    ├── component-sheet.png
    ├── favicon.png
    ├── logo-lockup.png
    └── symbol.png
```

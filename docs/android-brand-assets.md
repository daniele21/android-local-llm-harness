# Android brand assets

Status: active
Document type: asset-specification
Owner: apps/local-llm-phone-test
Canonical scope: brand.android-assets
Read when: changing launcher identity, canonical brand PNGs, generated Android resources or packaging checks
Last reviewed: 2026-09-01

The Android application uses the repository-owned **Harnex / H Bridge Core** identity. The product rename does not change the launcher symbol geometry.

## Source of truth

Public brand masters are:

- `docs/assets/brand/master/harnex-symbol.png` — canonical H Bridge Core symbol;
- `docs/assets/brand/master/harnex-app-icon-light.png` — light icon treatment;
- `docs/assets/brand/master/harnex-app-icon-dark.png` — dark icon treatment.

The H Bridge Core mark is invariant: do not recreate it from the historical SVG geometry. Existing `harness-symbol.png` and `harness-app-icon-*.png` files are byte-compatible legacy aliases retained for older repository consumers and the current internal Android resource pipeline.

The generated presentation copies under `docs/assets/brand/dark` and `docs/assets/brand/light` are not editable masters.

## Generated Android resources

`python3 scripts/generate_android_brand_assets.py` derives the Android launcher resources from the approved PNG identity and writes the colored, monochrome, adaptive and legacy-compatible resource forms required by the application.

The application manifest points to `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.

Internal resource identifiers such as `harness_launcher_*` are compatibility implementation names. They do not define the public product brand and may remain until a separately scoped internal/resource rename is justified.

The project minimum SDK is 26, so adaptive icons and the generated resource strategy remain compatible with the supported Android range. Android 13+ themed icons use the generated monochrome treatment.

## Safe zone

The generator owns the projection of the approved PNG mark into Android launcher masks and validates the expected source identity and generated resource structure. Preserve enough safe area for circular, rounded-square and squircle masks; do not manually crop or redraw the Harnex symbol to fit a launcher mask.

## Verification

```bash
python3 scripts/generate_brand_assets.py
python3 scripts/generate_android_brand_assets.py --check
./gradlew :apps:local-llm-phone-test:lintDebug \
  :apps:local-llm-phone-test:assembleDebug
python3 scripts/verify-android-packaging.py
```

CI checks deterministic Android generation and packaging. Packaging verification requires the phone APK resource table to contain the launcher background, colored foreground, monochrome foreground, standard icon and round icon. When the release AAB exists, the same resource contract is checked in `base/resources.pb`.

# Android brand assets

The phone-test application uses repository-owned vector assets for its runtime identity.

## Source of truth

`docs/assets/brand/master/harness-symbol.svg` is the canonical launcher symbol. Its path order and approved colors are validated before Android resources are generated. The wordmark and complete lockup are stored beside it as outlined SVG masters.

The PNG files under `docs/assets/brand/dark` and `docs/assets/brand/light` remain reference renders only.

## Generated Android resources

`python3 scripts/generate_android_brand_assets.py` writes:

- colored and monochrome `VectorDrawable` resources;
- a dark launcher background color;
- deterministic vector fallback icons;
- adaptive launcher icons under `mipmap-anydpi-v26`;
- Android 13+ themed icons under `mipmap-anydpi-v33`.

The application manifest points to `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.

The project minimum SDK is 26, where adaptive icons and vector drawables are supported. Raster launcher fallbacks are therefore unnecessary; the base vector resources remain available for tooling and defensive resource resolution.

## Safe zone

The colored symbol is mapped into a 108 × 108 adaptive-icon viewport. Its visible geometry occupies approximately `23.76..84.24`, remaining inside the conservative `21..87` safe zone used by the repository check. This preserves the brackets and bridge under circular, rounded-square and squircle masks.

## Verification

```bash
python3 scripts/generate_android_brand_assets.py --check
./gradlew :apps:local-llm-phone-test:lintDebug \
  :apps:local-llm-phone-test:assembleDebug
python3 scripts/verify-android-packaging.py
```

CI regenerates the Android resources in check mode. Packaging verification checks that the phone APK resource table contains the launcher background, colored foreground, monochrome foreground, standard icon and round icon. When the release AAB exists, the same names are checked in `base/resources.pb`.

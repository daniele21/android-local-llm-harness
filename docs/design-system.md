# Harness Android design system

Status: active
Document type: feature-specification
Owner: ui/design-system
Canonical scope: ui.design-system
Read when: changing shared Compose tokens, components, theme behavior or accessibility contracts
Last reviewed: 2026-08-06

The shared Compose design system lives in `ui/design-system` and is the only place where application-wide visual tokens and reusable primitives are defined.

## Structure

- `HarnessColors.kt`: dark/light semantic palettes and status tones;
- `HarnessTypography.kt`: typography scale and font-family policy;
- `HarnessShapes.kt`: shape tokens;
- `HarnessSpacing.kt`: spacing and minimum touch target;
- `HarnessTheme.kt`: dark, light and system-theme selection;
- `HarnessSurfaces.kt`: cards, metrics and status badges;
- `HarnessActions.kt`: primary/secondary actions and confirmation dialogs;
- `HarnessFeedback.kt`: app bar and loading, empty and error states;
- `HarnessNavigation.kt`: shared navigation container and item;
- `HarnessAccessibility.kt`: deterministic WCAG contrast verification;
- `HarnessPreviews.kt`: dark and light component previews.

Screens should consume these components and `MaterialTheme` semantic values rather than introducing local palette constants, duplicated spacing or one-off shapes.

## Theme behavior

`HarnessTheme()` follows the device setting by default through `isSystemInDarkTheme()`. Tests and previews can force dark or light explicitly. Both palettes provide WCAG AA contrast for normal text on primary surfaces and interactive fills.

Interactive components enforce a minimum height of 48 dp. Semantic status colors are exposed through `HarnessStatusTone` and are tested as foreground/container pairs.

## Offline font policy

Harness remains local-first and must not download fonts at runtime.

Inter and JetBrains Mono are the target brand families. The current implementation intentionally maps them to Android's offline system `sans-serif` and `monospace` families through `HarnessFontFamilies`. This provides deterministic no-network behavior without committing font binaries.

Bundled Inter or JetBrains Mono files may be introduced only in a separate reviewed change that verifies redistribution licensing, APK/AAB size impact, glyph coverage and fallback behavior. Until then, screens must use the design-system aliases and must not load remote fonts.

## Validation

The module includes JVM tests for dark/light palette contrast, semantic status contrast and the 48 dp touch-target contract. Compose previews cover the shared components in both themes. Repository CI runs the affected module tests, Android Lint, Spotless and Detekt before merge.

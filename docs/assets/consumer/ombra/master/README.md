# OMBRA vector identity candidates

Status: review-required
Document type: asset-index
Owner: OMBRA design system
Canonical scope: shared-runtime.consumer-api.pii-redactor.identity
Read when: reviewing or generating OMBRA product identity assets
Last reviewed: 2026-08-15

This directory contains manually recreated vector candidates derived from the approved OMBRA design contract. It intentionally does **not** auto-trace the raster brand board.

## Current candidate

| Asset | Status | Source contract |
| --- | --- | --- |
| [`ombra-symbol.svg`](ombra-symbol.svg) | **REVIEW REQUIRED** | folded document + opaque redaction bar + mint reveal slit |

The candidate uses only the reviewed OMBRA palette tokens from `ux-and-brand.md`. Geometry is deliberately simple and hand-authored so it can be reviewed at vector level and translated deterministically to Android drawables.

## Not yet approved or generated

The following remain blocked on visual review of the symbol and final typography/wordmark decision:

- `ombra-wordmark.svg`;
- `ombra-lockup.svg`;
- light/dark lockup variants;
- adaptive launcher foreground/background;
- Android 13 monochrome icon;
- app-bar vector;
- generated mipmap/drawable packaging output.

Do not use this directory to claim OMB-6 identity completion until the symbol geometry is explicitly reviewed, the remaining masters exist, generation is deterministic and packaging checks pass.

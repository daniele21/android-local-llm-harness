# Harness detail navigation

This document records the first typed detail-navigation slice for the connected Android phone application.

## Scope

The slice adds Navigation Compose destinations for:

```text
settings/privacy
settings/storage
settings/build
settings/developer-tools
settings/developer-tools/physical-validation
runs/{requestId}
```

It does not change model loading, inference, streaming, cancellation, telemetry retention, or runtime ownership.

## Route contract

`HarnessRoutes` and `HarnessSettingsDetail` are the UI-independent route contract. They derive the shell destination, detail title, subtitle, and top-level navigation visibility without reading Android state.

Request identifiers are encoded as URL-safe Base64 without padding before they enter the route. The decoded identifier exists only long enough to load the privacy-safe correlated timeline. Prompt text, generated output, filesystem paths, and backend messages are not navigation arguments.

Unknown top-level routes fall back to Overview. Blank request identifiers are rejected before navigation and malformed route arguments do not produce a timeline query.

## Shell and Back behavior

Top-level destinations continue to use compact bottom navigation or the expanded navigation rail. Detail destinations replace the ordinary top bar with a detail-aware bar and hide top-level bottom navigation.

Opening Settings pushes it onto the current top-level destination rather than rebuilding the top-level stack. Back therefore returns to the previously selected destination. Opening a Settings detail or request timeline pushes another destination and Back returns to its parent.

Navigation does not cancel active generation. Runtime and controller lifecycles remain governed by the existing Activity and `HarnessViewModel` boundaries.

## Connected details

- Privacy explains the local inference, telemetry-content, and app-private model boundaries.
- Storage shows the real selected model and links to Models without exposing a private path.
- Build reads version metadata through Android `PackageManager` and states the in-process runtime boundary.
- Developer tools links to Health, Logs, and physical validation.
- Physical validation reuses the existing real-device coordinator and privacy-safe report actions.
- Request timeline renders the existing allowlisted correlated events on a dedicated destination.

## Validation

The focused feature workflow validates:

```text
spotlessApply
:apps:local-llm-phone-test:testDebugUnitTest
:apps:local-llm-phone-test:compileDebugKotlin
```

Pure JVM tests cover top-level routes, all Settings details, shell visibility, fallback behavior, opaque request-ID round trips, blank identifiers, and malformed arguments.

## Remaining work

This slice does not complete navigation readiness. The remaining gates are:

- model detail routing;
- process-death and state-restoration behavior;
- compact and expanded emulator Back-stack evidence;
- landscape, font-scale, and TalkBack validation;
- continued migration of Activity-owned Models, Diagnostics, Overview, and Settings state to ViewModel/UDF;
- representative physical-device GGUF evidence.

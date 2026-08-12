# Android Binder client guide

## Scope

This guide applies to `transports/android-binder-client/**` and supplements the repository-wide `AGENTS.md`. The module owns the Android client side of the shared-runtime Binder boundary defined by `docs/shared-runtime/workstreams/client-sdk.md`.

## Responsibilities

- exact host package/service configuration;
- explicit bind/unbind lifecycle;
- Binder protocol negotiation and typed connection state;
- client registration and high-level `LocalLlmClient` adaptation;
- callback ordering, reconstruction, cancellation and disconnect handling;
- internal AIDL proxy/stub plumbing that must not leak into consuming apps.

## Invariants

- Never scan installed packages or bind an implicit intent; the host component comes from trusted application configuration.
- Never depend on `integrations/android-service-host`, `core/runtime-core`, backends, model-store or phone-app code.
- Blocking lifecycle adaptation must reject Android main-thread callers and use explicit bounded waits.
- Generation is never automatically replayed after disconnect or reconnect.
- Connection, close, cancellation and session cleanup must remain idempotent under races.
- AIDL callbacks must not invoke consumer listeners directly on Binder-managed threads once streaming support is added.
- Prompt and generated output stay bounded and must not be persisted by this transport.

## Validation

Run focused checks with:

```bash
./gradlew :transports:android-binder-client:testDebugUnitTest \
  :transports:android-binder-client:lintDebug \
  :transports:android-binder-client:assembleDebug
```

Shared contract or build changes also require the repository-wide validation gate.

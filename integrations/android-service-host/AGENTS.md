# Android service host integration guide

Scope: `integrations/android-service-host`.

Read `docs/shared-runtime/workstreams/host-service.md` and ADR 0012 before editing this module.

## Ownership

This module owns reusable Android shared-runtime host integration: caller authorization, immutable caller context, client/session/request ownership, Binder lifecycle handling and delegation from the Binder contract to a supplied `LocalLlmClient`.

It must not instantiate `RuntimeOrchestrator`, choose/download/install models, own host UI, or log caller signing material, prompts, reasoning or generated output.

## Security invariants

- Capture Binder UID/PID before switching threads.
- Verify the signature permission, exact package mapping and accepted signing certificate lineage on every entry point.
- Never trust caller-supplied package, UID, application ID, certificate or model identity.
- Scope every client token, session and request to one authenticated caller.
- Fail closed for ambiguous UIDs, unknown packages, signer mismatch, unauthorized use cases, quota exhaustion and closing connections.
- Keep control and callback queues bounded; Binder threads never load models or run generation.

## Validation

Use the module-local unit/lint/compile loop while iterating, then run repository-wide validation for security-boundary, Gradle, manifest or packaging changes.

# Play Internal physical evidence — 2026-09-07

Status: recorded
Evidence scope: Play-delivered install order, authorization and connectivity
Device class: representative physical Android hardware

## Functional result

The Play Internal Testing builds were exercised with the Consumer installed first:

- RedactGuard installed before Harnex;
- Harnex installed later without reinstalling RedactGuard;
- RedactGuard appeared as `PENDING` in Harnex;
- explicit Harnex authorization succeeded;
- Connect / Disconnect / Reconnect succeeded;
- real consumer inference completed successfully.

## Play App Signing identities

Google Play Console reported the following SHA-256 app-signing certificate digests:

- Harnex: `D6:2D:3C:C8:51:D5:72:05:C3:42:C1:7F:86:26:40:58:E3:FE:29:6A:AE:1B:0E:43:FD:AC:58:82:24:44:1A:BD`
- RedactGuard: `D6:2D:3C:C8:51:D5:72:05:C3:42:C1:7F:86:26:40:58:E3:FE:29:6A:AE:1B:0E:43:FD:AC:58:82:24:44:1A:BD`

The current Play-delivered Harnex and RedactGuard builds therefore use the same Play App Signing identity. This physical run proves Consumer-first install-order reachability, Harnex-owned authorization and product connectivity for the actual Play-delivered builds, but it does **not** constitute physical Play evidence of distinct Host/Consumer signing identities.

Distinct-signer authorization and replacement-signer denial remain covered by the deterministic independent-signer E2E path. A future Play-distributed consumer with a distinct App Signing identity is required to claim that topology from real Play distribution evidence.

## Scope boundary

This record does not close Harness 0.5 SR-6 real-runtime/device evidence, JNI/GGUF lifecycle evidence, memory/thermal evidence, Q35 physical tuning or broader representative-device claims.

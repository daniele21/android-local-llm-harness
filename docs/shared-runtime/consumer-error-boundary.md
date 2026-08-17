# Consumer authorization error boundary

A Consumer SDK connection must never expose `CONNECTED` until the configured caller has crossed the Host Consumer API authorization boundary.

## Required behavior

- Host missing -> `HOST_NOT_INSTALLED`.
- Explicit bind denied -> `PERMISSION_DENIED`.
- Protocol or required feature mismatch -> `INCOMPATIBLE`.
- Client registration denied -> `PERMISSION_DENIED`.
- Consumer API access throws `SecurityException` -> `PERMISSION_DENIED`.
- Binder transport loss -> `CONNECTION_LOST`.
- No expected remote authorization or compatibility failure may escape an Android Binder callback as an uncaught process-level exception.

The Consumer API access check happens only after protocol/feature negotiation and successful client registration. This preserves compatibility with older hosts while ensuring that package/signing authorization is proven before a usable endpoint is published.

Consumers should project these typed states into product-owned UI and provide a retry/update path instead of surfacing Binder exception text directly to end users.

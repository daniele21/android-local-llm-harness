# Security Policy

## Supported versions

The project is pre-1.0. Security fixes are applied to the latest development line only.

## Reporting a vulnerability

Do not open a public issue for vulnerabilities involving model import, file access, native memory safety, diagnostics permissions, prompt disclosure or Binder access control.

Report the issue privately to the repository owner through GitHub's private vulnerability reporting feature when enabled. Include:

- affected commit or release;
- Android version and device architecture;
- reproduction steps;
- expected and observed behavior;
- whether sensitive local content can be exposed;
- whether the problem crosses an application boundary.

## Security defaults

- Sensitive inference input, effective prompt, output and reasoning may persist only in the bounded app-private Activity audit store, encrypted before Room persistence with an app-scoped Android Keystore key.
- Normal telemetry, structured logs and diagnostics export remain content-free and must not carry decrypted Activity content.
- Audit storage or encryption failure never falls back to plaintext or silently permits unaudited inference.
- GGUF artifacts must be integrity-checked before loading.
- Model binaries are not committed to this repository.
- Native handles remain private to the backend module.
- Shared-runtime inference binding uses the `BIND_LOCAL_LLM` normal permission only as an explicit capability opt-in; possessing it does not grant runtime access.
- Shared-runtime Consumer authorization is fail-closed on Binder calling UID -> exact installed package -> signing certificate -> Harnex Control Plane authorization -> enabled use case. Caller-supplied identity fields never grant authority.
- Known independently signed consumers are source-observed as `PENDING`; signing identity replacement becomes `SIGNATURE_CHANGED`; both require explicit Harnex authorization before live access.
- Emulator-only fault controls and broader diagnostics/control surfaces remain separate from inference binding and retain their own stricter authorization boundary.

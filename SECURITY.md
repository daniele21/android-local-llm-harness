# Security Policy

## Supported versions

The project is pre-1.0. Security fixes are applied to the latest development line only.

## Reporting a vulnerability

Do not open a public issue for vulnerabilities involving model import, file access, native memory safety, diagnostics permissions, prompt disclosure or future Binder access control.

Report the issue privately to the repository owner through GitHub's private vulnerability reporting feature when enabled. Include:

- affected commit or release;
- Android version and device architecture;
- reproduction steps;
- expected and observed behavior;
- whether sensitive local content can be exposed;
- whether the problem crosses an application boundary.

## Security defaults

- Prompt and output persistence is disabled by default.
- GGUF artifacts must be integrity-checked before loading.
- Model binaries are not committed to this repository.
- Native handles remain private to the backend module.
- Diagnostics access will be protected by signature permission.
- The future shared runtime will isolate sessions and caches by calling application.

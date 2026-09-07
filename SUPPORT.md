# Harnex support

Use the channel that matches the kind of help you need.

## Questions and design discussions

Use [GitHub Discussions](https://github.com/daniele21/harnex/discussions) for:

- integration questions;
- architecture/design questions;
- model/runtime usage questions;
- ideas that are not yet actionable bug reports;
- help choosing the right Harnex boundary for an Android use case.

When asking for integration help, include the Harnex/Consumer SDK version, Android version and the relevant public error/state. Do not post prompts, generated content, private documents, credentials, signing material or host-private paths.

## Bugs

Use [GitHub Issues](https://github.com/daniele21/harnex/issues) for reproducible defects.

A useful report includes:

- Harnex git/version identity;
- Consumer SDK version when applicable;
- Android device/emulator and API level;
- model family/quantization and digest when relevant;
- expected vs actual behavior;
- minimal reproduction steps;
- privacy-safe logs or evidence.

Please distinguish emulator evidence from physical-device evidence.

## Security issues

Do **not** open a public issue for a vulnerability or for material containing secrets/sensitive data. Follow [`SECURITY.md`](SECURITY.md).

## Current limitations and release status

Before reporting a known release limitation, check:

- [`docs/current-state.md`](docs/current-state.md) for integrated state and active blockers;
- [`docs/roadmap.md`](docs/roadmap.md) for planned capability work;
- [`docs/releases/harness-0.5.md`](docs/releases/harness-0.5.md) for current release gates.

Harnex is pre-stable. Missing physical evidence for a device/runtime claim is not treated as proof that the claim passes or fails; the repository records those gaps explicitly.

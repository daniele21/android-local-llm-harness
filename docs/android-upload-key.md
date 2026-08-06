# Android upload key runbook

Status: active
Document type: runbook
Owner: apps/local-llm-phone-test
Canonical scope: release.android-upload-key
Read when: creating, storing, recovering or using the phone-test Google Play upload key
Last reviewed: 2026-08-06

This runbook covers creation, storage and use of the upload key for the Local LLM Phone Test application:

```text
application ID: io.github.daniele21.localllm.phonetest
keystore:       ~/.keystore/local-llm-phone-test-upload.jks
format:         PKCS12
alias:          local-llm-phone-test-upload
```

Google Play App Signing manages the app-signing key used for distributed APKs. This developer-held upload key signs AAB files before they are uploaded to Play Console.

## Create the keystore once

Run outside the repository:

```bash
mkdir -p ~/.keystore

keytool -genkeypair -v \
  -storetype PKCS12 \
  -keystore ~/.keystore/local-llm-phone-test-upload.jks \
  -alias local-llm-phone-test-upload \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Use the same strong password for the keystore and key. Store it in a password manager and never place it in Git, `.env`, Gradle properties, command arguments or shell history.

For the distinguished name, use stable project or maintainer information. Example:

```text
Name and surname (CN):       Local LLM Phone Test Upload
Organizational unit (OU):   Android Release
Organization (O):           Daniele21
City or locality (L):       .
State or province (ST):     .
Two-letter country code (C): IT
```

A single dot leaves an optional field empty. Do not accept `Unknown` defaults. Confirm the resulting distinguished name when prompted.

## Save the password in macOS Keychain

```bash
bash scripts/build-phone-test-release.sh setup
```

Keychain prompts for the password without displaying it or adding it to shell history. This command records the password only; it does not create, replace or modify the keystore.

## Build or sign an AAB

The build helper resolves the Android SDK in this order:

1. `ANDROID_HOME`;
2. `ANDROID_SDK_ROOT`;
3. `sdk.dir` in the untracked root `local.properties`;
4. `~/Library/Android/sdk`;
5. the Homebrew path `/opt/homebrew/share/android-commandlinetools`.

The selected SDK must contain both `platforms/` and `build-tools/`. If the SDK is elsewhere, set `ANDROID_HOME` before running the build.

Build a signed AAB from the current source:

```bash
bash scripts/build-phone-test-release.sh build
```

Sign the unsigned CI artifact at the repository root:

```bash
bash scripts/build-phone-test-release.sh sign-ci-aab
```

The latter preserves the unsigned input and creates:

```text
local-llm-phone-test-release-signed.aab
```

The helper finishes by running `jarsigner -verify -verbose -certs`. The output must include:

```text
jar verified
```

Signing changes the AAB SHA-256 digest. Record separate checksums for the unsigned CI artifact and signed upload artifact.

## Custody and recovery

- Keep an encrypted backup of the PKCS12 file outside the development machine.
- Restrict the keystore and Play Console access to release maintainers.
- Record the alias and upload-certificate SHA-256 fingerprint in the private release record.
- Never commit the keystore, password or exported private key.
- If the key is lost or compromised, stop uploads and request an upload-key reset through Play Console.

The binding security decision and rotation policy are recorded in [`ADR 0004`](adr/0004-phone-test-upload-key-custody.md). The complete Play internal-testing procedure is in [`play-internal-phone-test.md`](play-internal-phone-test.md).

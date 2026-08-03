# ADR 0004: Phone-test upload-key custody

- Status: Accepted
- Date: 2026-08-03
- Owners: Local LLM Harness release maintainers

## Context

The Play-installable phone-test application needs signed Android App Bundles for internal testing. Signing credentials must remain outside source control, while CI must continue producing an unsigned bundle that can be reviewed, checksummed and downloaded without granting CI access to the developer-held upload key.

Google Play App Signing separates the upload key controlled by the release maintainers from the app-signing key administered by Google. The repository needs a repeatable local workflow that does not place keystore passwords in source files, Gradle properties, `.env` files, command arguments or shell history.

## Decision

- Use a dedicated PKCS12 upload keystore outside the repository, defaulting to `~/.keystore/local-llm-phone-test-upload.jks` with alias `local-llm-phone-test-upload`.
- Use the same strong password for the keystore and its key so macOS Keychain stores one release secret.
- Create the keystore manually. Repository automation must not generate or replace private keys.
- Store the password in the user's default macOS Keychain under service `io.github.daniele21.localllm.phonetest.android-upload` and account `local-llm-phone-test-upload`.
- Supply signing configuration to Gradle only through the four `LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_*` environment variables for the lifetime of the release helper.
- Reject partial signing configuration and reject unsigned release packaging by default.
- Allow unsigned release packaging only through the explicit `LOCAL_LLM_PHONE_TEST_ALLOW_UNSIGNED_RELEASE=true` CI exception. The resulting artifact is not eligible for Play Console upload.
- Keep the upload key distinct from the app-signing key managed through Google Play App Signing.
- Restrict keystore access to release maintainers, keep an encrypted backup outside the development machine and document custody ownership in the private release record.

## Recovery and rotation

The release owner must retain:

- an encrypted backup of the PKCS12 keystore;
- the password in an approved password manager or recoverable Keychain backup;
- the alias and upload-certificate SHA-256 fingerprint;
- access to the Play Console account authorized to request an upload-key reset.

If the upload key is lost or suspected compromised, stop uploads, request an upload-key reset through Play Console, generate a new dedicated key outside the repository, register its public certificate and update the private custody record. Never commit either the old or replacement key.

## Consequences

- Local signed builds are reproducible without storing signing secrets in Git.
- CI retains a secret-free unsigned artifact path.
- A maintainer must initialize Keychain on each authorized macOS account.
- Non-macOS release hosts must provide an equivalent secret-injection mechanism or use a reviewed extension of the helper.
- Signing an existing CI bundle changes its SHA-256 digest, so both unsigned and signed checksums must be recorded separately.

## Alternatives considered

- Store signing material in the repository: rejected because it exposes the private upload key and credentials.
- Give the upload key to the current CI pipeline: deferred because internal testing does not yet require unattended signing and broader secret access increases operational risk.
- Use the app-signing key as the upload key: rejected because Play App Signing supports separation and upload-key reset.
- Generate the keystore from the helper: rejected because accidental regeneration could silently create an unusable release identity.

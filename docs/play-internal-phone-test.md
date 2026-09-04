# Google Play internal phone test

Status: active
Document type: runbook
Owner: apps/local-llm-phone-test
Canonical scope: release.play-internal
Read when: preparing, uploading or validating the phone application through Google Play Internal Testing
Last reviewed: 2026-09-02

`apps/local-llm-phone-test` is a standalone Android application for validating the real local-LLM runtime on a physical device when developer mode, USB debugging or ADB is unavailable.

It is intentionally separate from the developer console and from the instrumentation-only device test runner.

## Scope

The app provides an on-device flow for:

1. selecting a GGUF through Android's Storage Access Framework;
2. copying it into a private staging area while computing SHA-256;
3. importing it into the content-addressed `FileSystemModelStore`;
4. verifying the stored model before runtime use;
5. loading the CPU-only `llama.cpp` backend;
6. running generation and collecting TTFT, total duration and decode throughput;
7. cancelling an active generation after the first streamed delta;
8. running five load/generate/unload cycles and checking PSS growth;
9. recording device, Android, ABI, RAM and thermal-status evidence;
10. copying or sharing a privacy-safe PASS/FAIL report.

Prompts, generated output, document URIs, private storage paths and model bytes are not included in the shared report.

## Current test profile

This profile records the implemented pre-migration Qwen3 validation path. It is legacy preflight, not Qwen3.5 compatibility or certification evidence. Q35-1 will retire its product eligibility; Q35-7 defines the replacement physical-device matrix.

The UI defaults to the emulator-proven profile:

| Setting | Value |
| --- | --- |
| Architecture | `qwen3` |
| Quantization | `Q4_K_M` |
| Context | `512` |
| Batch | `128` |
| Micro-batch | `64` |
| CPU threads | up to `4` |
| GPU layers | `0` |
| Generation output | `32` tokens |
| Cancellation output limit | `256` tokens |
| Memory cycles | `5` |
| PSS growth budget | `131072` KB |
| Operation timeout | `180` seconds |

Architecture and quantization are editable before importing a model. The remaining values are fixed for the first physical-device gate so the result stays comparable with the ARM64 emulator preflight.

## Storage requirements

The GGUF is not committed, bundled in the AAB or downloaded by the application.

The selected document is first copied into a temporary private file and then imported into the content-addressed store. During first import, keep enough free storage for roughly two copies of the model plus the installed application. The temporary copy is deleted after import.

The durable model is stored below the application's `noBackupFilesDir`, keyed by its SHA-256 digest. Removing the model from the UI deletes the content-addressed object and its saved metadata.

## Configure the upload key

The concise creation, Keychain, signing and recovery procedure is available in [`android-upload-key.md`](android-upload-key.md). This section records the same configuration in the context of the complete Play internal-testing flow.

The application ID is:

```text
io.github.daniele21.localllm.phonetest
```

The upload key is a PKCS12 keystore stored outside the repository and separate from the app-signing key administered by Google Play App Signing. The default local configuration is:

```text
keystore: ~/.keystore/local-llm-phone-test-upload.jks
alias: local-llm-phone-test-upload
```

The `.jks` suffix is only a filename convention; the store format must be `PKCS12`. Keep an encrypted backup of the keystore outside the development machine and record who owns recovery and Play Console upload-key reset operations.

On macOS, save the existing keystore password in the user's default Keychain:

```bash
bash scripts/build-phone-test-release.sh setup
```

The setup command does not create or modify the keystore. It only stores the password in Keychain without printing it.

## Build a signed Android App Bundle locally

Create a signed bundle from the current source with:

```bash
bash scripts/build-android-aab.sh build
```

This routes to `scripts/build-phone-test-release.sh build`. The helper resolves the Android SDK, checks the external upload keystore, supplies the signing environment only to the build and runs:

```text
:apps:local-llm-phone-test:bundleRelease
```

Gradle accepts release signing only through:

```text
LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_FILE
LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_PASSWORD
LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_ALIAS
LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_PASSWORD
```

All four values are required by Gradle. A partial configuration fails explicitly. Locally, the helper can source the password from macOS Keychain and use it for both store and key; CI can inject separate store/key passwords when required.

Packaging a release without signing fails unless `LOCAL_LLM_PHONE_TEST_ALLOW_UNSIGNED_RELEASE=true` is set explicitly; that exception remains reserved for intentional non-distributable CI evidence.

The default signed output is:

```text
apps/local-llm-phone-test/build/outputs/bundle/release/local-llm-phone-test-release.aab
```

For an ordinary local signed-bundle build, the helper advances `apps/local-llm-phone-test/version.properties` as one paired release identity: it increments `versionCode` and derives `versionName` from the existing major/minor train plus that new code as the patch component. For example, `versionCode=33` and `versionName=1.0.0` advance to `versionCode=34` and `versionName=1.0.34`.

Protected Play CI does not mutate `version.properties`. It supplies both `PLAY_VERSION_CODE` and `PLAY_VERSION_NAME`; the two overrides are mandatory as a pair. Gradle and the canonical release helper fail closed if only one is supplied, if either format is invalid, or if the version name does not match the repository major/minor train and exact version code.

## Sign the unsigned CI bundle manually

To preserve and sign an unsigned CI bundle already downloaded at the repository root:

```bash
bash scripts/build-phone-test-release.sh sign-ci-aab
```

The defaults are:

```text
input:  local-llm-phone-test-release-unsigned.aab
output: local-llm-phone-test-release-signed.aab
```

The command signs a separate copy with `jarsigner` and verifies the result. This remains useful for inspection/recovery, but normal Internal Testing publication should use the automated release workflow below.

## Automated GitHub -> Play Internal Testing

`.github/workflows/play-internal.yml` is the canonical automatic publishing path for the phone-test application.

Automatic publication is disabled until repository variable `PLAY_INTERNAL_ENABLED=true` is configured. After activation, an app/runtime-relevant push to `dev` starts the workflow. Before accessing signing material or Google Play, the job checks out the exact candidate SHA and waits for the existing `Validate` workflow for that same SHA and `push` event to complete successfully. Failed or cancelled validation blocks publication.

The workflow then:

1. authenticates to Google through GitHub OIDC and Workload Identity Federation;
2. creates a temporary Android Publisher edit and lists all current APK/AAB version codes;
3. selects `max(versionCode) + 1`, or `1` for an app with no uploaded artifacts;
4. derives the matching `versionName` through the canonical release helper, preserving the repository major/minor train and using the exact Play version code as patch;
5. reconstructs the PKCS12 upload keystore only inside the GitHub runner;
6. invokes the same canonical `bash scripts/build-android-aab.sh build` entrypoint with `PLAY_VERSION_CODE`, `PLAY_VERSION_NAME` and the protected signing variables;
7. verifies the signed AAB with `jarsigner`;
8. refreshes the short-lived Google access token after the potentially long native build;
9. uploads the AAB, updates the `internal` track to a completed release and commits the Play edit;
10. stores the exact released AAB as a seven-day GitHub Actions evidence artifact whose name records both version identities.

Publishing is serialized for the application, preventing concurrent release jobs from selecting the same next Play version code. If upload/track update fails before commit, the helper attempts to delete the uncommitted Play edit.

`workflow_dispatch` is also available, but the selected candidate must already have a successful `Validate` **push** run for the exact commit. Manual dispatch cannot bypass the validation gate.

### GitHub configuration

Create a GitHub Environment named:

```text
play-internal
```

Store these Environment secrets:

```text
ANDROID_UPLOAD_KEYSTORE_B64
ANDROID_UPLOAD_STORE_PASSWORD
ANDROID_UPLOAD_KEY_PASSWORD
```

If the PKCS12 key password is the same as the store password, store the same value in both password secrets.

Store these non-secret variables in the `play-internal` Environment:

```text
GCP_WORKLOAD_IDENTITY_PROVIDER
GCP_PLAY_SERVICE_ACCOUNT
ANDROID_UPLOAD_KEY_ALIAS
```

`ANDROID_UPLOAD_KEY_ALIAS` may be omitted when the existing default remains valid:

```text
local-llm-phone-test-upload
```

Only after Google Cloud and Play Console are configured, create this **repository variable**:

```text
PLAY_INTERNAL_ENABLED=true
```

The enable flag is repository-scoped because GitHub evaluates whether an automatic push job should start before Environment values are loaded.

## Google identity and Play permissions

The workflow deliberately stores no service-account JSON key. GitHub requests a short-lived OIDC identity, the configured Google Workload Identity Provider validates the repository identity, and the workflow impersonates the service account configured by `GCP_PLAY_SERVICE_ACCOUNT`.

The Google Cloud project must have the Google Play Android Developer API enabled. The Workload Identity provider must trust this repository and grant it permission to impersonate the service account. The same service-account email must be invited in Play Console with permission to release this application to testing tracks. Production-release permission is not required.

The Python helper `scripts/google-play-internal.py` talks directly to Android Publisher REST v3; it creates/deletes/commits edits, lists current bundles/APKs, uploads the signed AAB and updates the `internal` track. No third-party publishing action owns release semantics.

## Play Console bootstrap

For the first setup:

1. Create the Play application for package `io.github.daniele21.localllm.phonetest`.
2. Complete the minimum application/setup requirements requested by Play Console.
3. Enroll in Play App Signing and verify the expected upload certificate.
4. Configure **Testing -> Internal testing** and its tester list.
5. Invite the CI service account with testing-track release permission.
6. Verify the package has at least one valid Internal Testing configuration; the automated workflow can then own subsequent releases.

Google Play installs the app-signing-key-signed APK generated from the uploaded AAB. The developer-held upload key only authenticates the bundle upload.

## Run the physical-device validation

1. Install or update the app through the normal Internal Testing opt-in flow.
2. Download the configured legacy preflight GGUF to the phone or expose it through a Storage Access Framework provider.
3. Open **Local LLM Phone Test**.
4. Confirm the intended architecture/quantization for the model under test.
5. Tap **Select and import GGUF**.
6. Keep the application open while the model is copied, hashed and imported.
7. Tap **Run full validation**.
8. Keep the screen on and the application in the foreground until a PASS or FAIL report appears.
9. Copy/share the privacy-safe report into the evidence PR.

A PASS report confirms only the tested build, model digest and physical device completed the configured lifecycle. It does not establish compatibility or performance for other models, Android versions, SoCs or OEMs.

## Managed-device limitation

Google Play installation does not require developer mode or ADB. A company-managed device can still block the application through Managed Google Play, work-profile policy or an allowlist. In that case the organization administrator must approve or distribute the application; this repository cannot bypass device-management policy.

## Release and evidence discipline

- Every Play upload uses a strictly increasing `versionCode` resolved from current Play state and a matching incremented `versionName` on the repository major/minor train; neither identity may advance alone.
- Keep the package name stable after the Play Console application is created.
- Do not commit GGUFs, keystores, passwords, Google private keys or generated credentials.
- Restrict upload-keystore access, keep an encrypted external backup and test recovery before it is needed.
- Record app version, commit SHA, model SHA-256 and exact privacy-safe report in the evidence PR.
- Do not mark the runtime production-ready from emulator evidence or from a single physical device.

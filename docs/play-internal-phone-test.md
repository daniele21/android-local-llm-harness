# Google Play internal phone test

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

## Build a signed Android App Bundle

The application ID is:

```text
io.github.daniele21.localllm.phonetest
```

The repository does not contain a keystore, passwords or signing credentials. Create and retain an upload key outside the repository.

From Android Studio:

1. Open the repository.
2. Select **Build → Generate Signed Bundle / APK**.
3. Select **Android App Bundle**.
4. Select the `local-llm-phone-test` module.
5. Select or create the upload keystore.
6. Select the `release` build type.
7. Generate the `.aab`.

The unsigned CI bundle is useful only as a build artifact. The bundle uploaded to Google Play must be signed with the account's upload key. New Google Play applications use Play App Signing, which keeps the app-signing key separate from the upload key.

Official references:

- <https://developer.android.com/studio/publish/app-signing>
- <https://developer.android.com/studio/publish/upload-bundle>

## Publish to the internal testing track

In Play Console:

1. Create an application using the stable package name above.
2. Complete the minimum app setup requested by Play Console.
3. Enroll the application in Play App Signing when prompted.
4. Open **Test and release → Testing → Internal testing**.
5. Create a release and upload the signed `.aab`.
6. Add the Google account used by the Play Store on the target phone to the tester list.
7. Start the internal rollout.
8. Open the generated opt-in URL on the phone with the same Google account.
9. Join the test and install the application from Google Play.

Internal testing is intended for a small trusted group and currently supports up to 100 testers. Internal-test applications are installed through Google Play and are not normally discoverable through store search before broader publication.

Official references:

- <https://support.google.com/googleplay/android-developer/answer/9845334>
- <https://support.google.com/googleplay/android-developer/answer/9859348>

## Run the physical-device validation

1. Download the supported GGUF to the phone or make it available through a Storage Access Framework provider such as Google Drive.
2. Open **Local LLM Phone Test**.
3. Confirm `qwen3` and `Q4_K_M` for the current Qwen3 0.6B test model.
4. Tap **Select and import GGUF**.
5. Keep the application open while the model is copied, hashed and imported.
6. Tap **Run full validation**.
7. Keep the screen on and the application in the foreground until a PASS or FAIL report appears.
8. Tap **Copy report** or **Share report** and attach the exact text to the physical-device evidence PR.

A PASS report confirms the tested application build, model digest and physical device completed the configured lifecycle. It does not establish compatibility or performance for other models, Android versions, SoCs or OEMs.

## Managed-device limitation

Google Play installation does not require developer mode or ADB. A company-managed device can still block the application through Managed Google Play, work-profile policy or an allowlist. In that case the organization administrator must approve or distribute the application; this repository cannot bypass device-management policy.

## Release and evidence discipline

- Increment `versionCode` for every uploaded Play build.
- Keep the package name stable after the Play Console application is created.
- Do not commit the GGUF, keystore, service-account JSON or passwords.
- Record the app version, commit SHA, model SHA-256 and exact privacy-safe report in the evidence PR.
- Do not mark the runtime production-ready from emulator evidence or from a single physical device.

# Android application build and local run

Status: active
Document type: runbook
Owner: repository
Canonical scope: android.build-run
Read when: building, installing or launching an Android application from this repository
Last reviewed: 2026-08-06

This guide covers the two common workflows for `apps/local-llm-phone-test`:

1. creating a signed Android App Bundle (`.aab`) for Google Play Console;
2. building, installing and launching the debug application on a local emulator.

Run all commands from the repository root.

## Prerequisites

Both workflows require the repository build prerequisites listed in the
[`README`](../README.md#build-prerequisites), including JDK 17 and the configured Android SDK.

The emulator workflow also requires:

- Android SDK Platform Tools, including `adb`;
- a running ARM64 Android Virtual Device that reports `arm64-v8a`;
- the emulator to appear as `device`, rather than `offline`, in `adb devices`.

The Play release workflow additionally requires the external PKCS12 upload keystore and its
password in the macOS Keychain. Signing keys and passwords must never be stored in this
repository.

## Build the signed bundle for Google Play Console

### One-time signing setup

Create or obtain the dedicated upload keystore by following
[`android-upload-key.md`](android-upload-key.md). The default location and alias are:

```text
keystore: ~/.keystore/local-llm-phone-test-upload.jks
alias: local-llm-phone-test-upload
```

After the keystore exists, save its password in the macOS Keychain:

```bash
bash scripts/build-phone-test-release.sh setup
```

This command stores the password only. It does not create or modify the keystore.

### Build the release

```bash
bash scripts/build-phone-test-release.sh build
```

The command builds a release bundle signed with the configured upload key. The file to upload
to Google Play Console is:

```text
apps/local-llm-phone-test/build/outputs/bundle/release/local-llm-phone-test-release.aab
```

Before every new Play upload, make sure the application's `versionCode` is greater than the one
used by the previous upload. An unsigned bundle produced by the explicit CI exception is not
eligible for upload.

For the complete signing, internal-testing publication and recovery procedure, see
[`play-internal-phone-test.md`](play-internal-phone-test.md) and
[`android-upload-key.md`](android-upload-key.md).

## Run the debug application on a local emulator

### 1. Start an emulator

The repository runner does not create or boot an Android Virtual Device. Start an ARM64 AVD
from **Android Studio → Device Manager**, or list and start an existing AVD from the terminal:

```bash
"${ANDROID_HOME}/emulator/emulator" -list-avds
"${ANDROID_HOME}/emulator/emulator" -avd <AVD_NAME>
```

Keep that terminal open while the emulator is running. If `ANDROID_HOME` is not configured, use
the absolute path to the `emulator` binary from the installed Android SDK.

Wait for Android to finish booting, then verify the connection and ABI:

```bash
adb devices
adb shell getprop ro.product.cpu.abi
```

The device must be online and the ABI must begin with `arm64-v8a`. The current native artifacts
do not support an `x86_64` AVD.

### 2. Build, install and launch the phone app

```bash
bash scripts/run-emulator-debug.sh --app phone-test
```

The runner performs these operations in order:

1. selects the first online ADB device or emulator;
2. runs `:apps:local-llm-phone-test:installDebug`;
3. installs the debug APK;
4. launches `io.github.daniele21.localllm.phonetest.debug`.

The debug application ID is different from the release application ID, so the two variants can
coexist on the same device.

To follow application logs after launch:

```bash
bash scripts/run-emulator-debug.sh --app phone-test --logs
```

If more than one ADB device is connected, select one explicitly:

```bash
adb devices
bash scripts/run-emulator-debug.sh --app phone-test --device emulator-5554
```

If `adb` is not on `PATH`, pass its absolute location:

```bash
bash scripts/run-emulator-debug.sh \
  --app phone-test \
  --adb /absolute/path/to/android-sdk/platform-tools/adb
```

The runner also supports `--app console` and `--app device-test`. Omitting `--app` defaults to
the developer console, not the Play-installable phone application.

## Command summary

| Goal | Command | Result |
| --- | --- | --- |
| Configure the Play signing password once | `bash scripts/build-phone-test-release.sh setup` | Password stored in macOS Keychain |
| Build the Play upload artifact | `bash scripts/build-phone-test-release.sh build` | Signed release `.aab` |
| Run the phone app locally | `bash scripts/run-emulator-debug.sh --app phone-test` | Debug APK installed and launched on a running emulator |
| Run locally and follow logs | `bash scripts/run-emulator-debug.sh --app phone-test --logs` | Debug app launched with Logcat streaming |

Emulator execution is development and preflight evidence only. It does not replace the
representative physical-device validation required for production-readiness claims.

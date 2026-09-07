# Hello Harnex

A runnable standalone Android app that proves the public Harnex Consumer SDK path end to end:

```text
Hello Harnex app
  -> Consumer Android SDK
  -> explicit Binder host
  -> Harnex authorization + assigned use case
  -> shared local runtime
  -> llama.cpp / local GGUF
  -> structured result back to the app
```

The sample does **not** depend on Harnex source modules, JNI code, GGUF files or model-store internals. It resolves `consumer-android` from the public token-free Maven channel just like an external repository.

## What you need

- JDK 17 and Android SDK API 36;
- an emulator or Android device visible to `adb`;
- Harnex installed;
- a supported local model installed in Harnex for the `Document PII detection` use case.

The sample defaults to the source-build Harnex package `io.github.daniele21.localllm.phonetest.debug`. If you are using the Play/release package, use the override shown below.

## 1. Run Harnex

From the repository root, for the normal local-development path:

```bash
bash scripts/run-emulator-debug.sh --app phone-test
```

Install a supported model from **Models** if needed.

## 2. Install Hello Harnex

From the repository root:

```bash
./gradlew -p samples/hello-harnex :app:installDebug
```

For the Play/release Harnex package instead:

```bash
./gradlew -p samples/hello-harnex \
  -PharnexHostPackage=io.github.daniele21.localllm.phonetest \
  :app:installDebug
```

Open **Hello Harnex** on the device.

## 3. Authorize the exact app identity

The sample displays its current Android package and signing-certificate SHA-256 so no `keytool` command is required.

In Harnex open **Apps -> New app connection** and use:

| Field | Value |
| --- | --- |
| Display name | `Hello Harnex` |
| Harnex app ID | `hello-harnex` |
| Android package | `io.github.daniele21.harnex.hello` |
| Signer SHA-256 | copy it from the sample |
| Use case | `Document PII detection` |
| Initial preset | `Balanced` |

Harnex accepts the displayed colon-separated fingerprint and normalizes it to the canonical 64-character SHA-256 value. Create and enable the connection. Harnex remains the authority: the Maven dependency and Binder connection do not grant access by themselves.

## 4. Connect and run

Back in Hello Harnex:

1. tap **Connect to Harnex**;
2. wait for `CONNECTED`;
3. keep or edit the example text;
4. tap **Run on device**.

The default input contains one email address. A successful response is structured JSON similar to:

```json
{
  "schemaVersion": 1,
  "findings": [
    {
      "typeId": "email",
      "surface": "alice.rossi@example.com",
      "segmentId": "p0001-b0001"
    }
  ]
}
```

Exact model output can vary, but Harnex constrains this request with the host-owned JSON-schema use case. The sample also displays public TTFT, total latency, output-token count and decode throughput when available.

## Read the integration in one file

Start with [`HelloHarnexClient.kt`](app/src/main/java/io/github/daniele21/harnex/hello/HelloHarnexClient.kt). It shows the supported external lifecycle without repository internals:

```text
create exact host client
  -> connect
  -> discover assigned use case + published preset
  -> activate the host-owned capability
  -> prepare exact execution
  -> create session
  -> generate
  -> terminal event
  -> close session + deactivate
  -> close client
```

[`MainActivity.kt`](app/src/main/java/io/github/daniele21/harnex/hello/MainActivity.kt) is intentionally simple Android UI around that boundary. It computes the app's current signer fingerprint, explains authorization and renders result/status state.

## Privacy and lifecycle

- inference stays on the device through Harnex;
- the sample does not write prompt or generated output to files, logs or diagnostics;
- caller-declared package identity is not used as the trust anchor;
- cancellation uses the public generation handle;
- sessions and activations are released on terminal paths and the Consumer client is closed with the Activity;
- Harnex owns model identity, selection, residency and runtime policy.

## Why this first sample uses structured PII detection

The goal is to demonstrate a **real currently supported Consumer API path**, not invent a tutorial-only runtime contract. `Document PII detection` is the existing production-shaped externally assignable use case, so the sample exercises the same authorization and runtime boundary used by a real consumer. A generic text-assistant sample should be added only when Harnex exposes that as a deliberate public host-owned use case.

For the full Consumer SDK contract, see [`../../docs/shared-runtime/consumer-android-sdk.md`](../../docs/shared-runtime/consumer-android-sdk.md).

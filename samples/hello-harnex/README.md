# Hello Harnex

A runnable standalone Android app and the **golden reference for a new Harnex Consumer app**.

It proves the public path end to end:

```text
Hello Harnex app
  -> app-owned Consumer boundary
  -> Consumer Android SDK
  -> explicit Binder host
  -> Harnex authorization + assigned use case
  -> shared local runtime
  -> llama.cpp / local GGUF
  -> structured result back to the app
```

The sample does **not** depend on Harnex source modules, JNI code, GGUF files or model-store internals. It resolves `consumer-android` from the public token-free Maven channel just like an external repository.

## Reference architecture

Keep a new Consumer app small at the Harnex boundary:

```text
MainActivity / product UI
        |
        v
HelloHarnexViewModel
  product-visible state
  survives Activity recreation
        |
        v
HelloHarnexClient
  connection + assigned capability lifecycle
  activation / prepare / session / generation / cleanup
        |
        v
HelloHarnexRuntime
  tiny app-owned seam around the public SDK
        |
        v
consumer-android -> Binder -> Harnex
```

This is intentionally **not** a reusable framework hidden inside the sample. The important pattern is ownership:

- UI renders product state and sends user intents;
- a lifecycle owner keeps the Consumer client out of the Activity lifetime;
- one small app-owned boundary isolates the public SDK from product code and makes behavior testable;
- Harnex owns authorization, model identity, selection, residency and runtime policy;
- every accepted execution releases session and activation on terminal paths;
- `disconnect()` is reversible, while `close()` is terminal;
- prompts and generated output are not persisted or written to diagnostics by this sample.

Do not copy the sample's PII product workflow into another app. Copy the **boundary and lifecycle pattern**, then use the Harnex-assigned use case owned by that product.

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

The manual registration above is the current onboarding UX. It must not be interpreted as the trust anchor: at runtime Harnex still derives the caller from Android Binder identity and applies the persisted package/signer/use-case authorization policy.

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

## Read the integration in three files

Start in this order:

1. [`HelloHarnexRuntime.kt`](app/src/main/java/io/github/daniele21/harnex/hello/HelloHarnexRuntime.kt) — the tiny app-owned seam around the public SDK;
2. [`HelloHarnexClient.kt`](app/src/main/java/io/github/daniele21/harnex/hello/HelloHarnexClient.kt) — the complete supported lifecycle and cleanup boundary;
3. [`HelloHarnexViewModel.kt`](app/src/main/java/io/github/daniele21/harnex/hello/HelloHarnexViewModel.kt) — product-visible state that survives Activity recreation.

`MainActivity.kt` is intentionally only a small Android UI shell.

The client lifecycle is:

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
  -> close client when the lifecycle owner is cleared
```

Important failure classes are translated into product-actionable states such as authorization required, configuration required, model not ready, connection unavailable, cancellation and runtime failure. The underlying SDK error remains part of the diagnostic detail; the UI does not need to parse raw Binder failures.

## Validation

Run the sample's focused checks with:

```bash
./gradlew -p samples/hello-harnex :app:testDebugUnitTest :app:assembleDebug
```

The repository publication gate goes further:

```bash
bash scripts/verify-consumer-sdk-publication.sh
```

It publishes run-owned Consumer SDK artifacts, builds both external Consumer projects from Maven coordinates, runs the Hello Harnex lifecycle tests and rejects source/composite/project coupling.

The focused unit tests cover the application-owned lifecycle boundary, including concurrent-run rejection, terminal cleanup, close during active generation and actionable authorization failure mapping.

## Privacy and lifecycle

- inference stays on the device through Harnex;
- the sample does not write prompt or generated output to files, logs or diagnostics;
- caller-declared package identity is not used as the trust anchor;
- cancellation uses the public generation handle;
- sessions and activations are released on terminal paths;
- closing the ViewModel cancels active work, releases application-owned leases and closes the Consumer client exactly once;
- Harnex owns model identity, selection, residency and runtime policy.

For work that must intentionally outlive a transient UI/connection observer, do **not** keep an Activity-owned ordinary generation alive. Use the SDK's durable logical-job API and follow its recovery semantics instead.

## Why this first sample uses structured PII detection

The goal is to demonstrate a **real currently supported Consumer API path**, not invent a tutorial-only runtime contract. `Document PII detection` is the existing production-shaped externally assignable use case, so the sample exercises the same authorization and runtime boundary used by a real consumer. A generic text-assistant sample should be added only when Harnex exposes that as a deliberate public host-owned use case.

For the full Consumer SDK contract, see [`../../docs/shared-runtime/consumer-android-sdk.md`](../../docs/shared-runtime/consumer-android-sdk.md).

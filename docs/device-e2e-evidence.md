# Device validation evidence bundle

Use `scripts/capture-device-e2e-evidence.sh` for physical-device acceptance runs. It wraps `scripts/run-device-e2e.sh`, preserves the original exit status and produces a timestamped evidence directory plus a compressed archive.

## Standard command

```bash
bash scripts/capture-device-e2e-evidence.sh \
  --model /absolute/path/to/model.gguf \
  --architecture qwen2 \
  --quantization Q4_K_M \
  --memory-repeat 5 \
  --max-pss-growth-kb 131072
```

By default, evidence is written below:

```text
build/device-e2e-evidence/<UTC timestamp>/
build/device-e2e-evidence/<UTC timestamp>.tar.gz
```

Use `--evidence-dir <path>` to select a different output directory. All other arguments are forwarded to `run-device-e2e.sh`.

## Captured evidence

The bundle contains:

- `manifest.txt` with repository commit, branch, dirty state, model identity metadata, selected runtime arguments and non-serial device/build properties;
- `instrumentation.log` with the complete host-runner and Android instrumentation output;
- `metrics.txt` with privacy-safe `LOCAL_LLM_E2E` markers;
- `test-markers.txt` with Android/JUnit success or failure markers;
- `apk-sha256.txt` with the built test APK hashes;
- `apk-inventory.txt` with packaged native `.so` entries when `unzip` is available;
- `thermal-before.txt` and `thermal-after.txt` when the device exposes `dumpsys thermalservice`;
- `meminfo-before.txt` and `meminfo-after.txt` for the device-test package when available;
- `README.txt` describing privacy and interpretation constraints.

The archive intentionally excludes:

- GGUF model bytes;
- prompt and generated-content text;
- adb device serial numbers.

Only the model filename, byte size, SHA-256 digest and declared diagnostic profile metadata are recorded.

## Evidence review

A physical-device evidence record is acceptable only when:

1. `runner_exit_code=0` in `manifest.txt`;
2. `test-markers.txt` includes both a successful JUnit marker and `INSTRUMENTATION_CODE: -1`;
3. `metrics.txt` contains generation and cancellation markers, plus memory markers for memory-validation runs;
4. `apk-inventory.txt` confirms the expected JNI and llama.cpp shared libraries are packaged for `arm64-v8a`;
5. the repository commit matches the reviewed pull-request head or release-candidate commit and `repository_dirty=false`;
6. no native crash, instrumentation failure or unrecoverable runtime state appears in `instrumentation.log`;
7. repeated runs on the same matrix entry do not show unbounded PSS growth or severe unexplained thermal/performance regression.

## Device/model matrix

Record at least one primary supported matrix entry before the first production-ready release, before distributing the runtime to application consumers or before making device-performance claims:

| Field | Required value |
| --- | --- |
| Repository commit | Exact reviewed PR-head or release-candidate SHA |
| Device | Manufacturer, model and codename |
| Android | Release and SDK level |
| ABI | `arm64-v8a` |
| Model | Filename, architecture, quantization, byte size and SHA-256 |
| Runtime | CPU thread count, timeout and memory-cycle settings |
| Results | TTFT, total time, decode tokens/s, cancellation and PSS growth |

Evidence may be captured from a review branch or from the current `main`, provided the exact clean commit is recorded and is the commit being evaluated for release or support claims.

Additional representative devices should be added before claiming broad production support. A single device proves the acceptance path for that matrix entry, not universal compatibility.

## Failure handling

The wrapper preserves evidence even when the underlying runner fails. Attach the archive to the relevant issue, pull-request discussion or release record only after checking that the logs contain no private local paths or other environment-specific information that should not be shared.

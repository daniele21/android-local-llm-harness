# Qwen3.5 2B Q4_K_M physical acceptance — Samsung SM-A566B

Status: **PASS**  
Captured: `2026-09-07T12:08:25Z`  
Device: Samsung `SM-A566B`  
Android: `16` / API `36` / `arm64-v8a`  
Reported RAM: `7,777,300,480` bytes  
Model: Qwen3.5 2B `Q4_K_M`  
Model SHA-256: `aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223`  
Manifest SHA-256: `2e5e87749ea38a2bb9d309da43f9c112ee0bc93742c92b55e19b5de293095535`

## What this proves

The installed Harnex app executed its host-owned `PhoneTestController.runFullValidation()` path against the exact curated model already present in the private ModelStore. No Mac-side GGUF copy was used for the acceptance run.

The run passed:

- real local generation through the Harnex runtime/JNI/backend path;
- active cancellation with terminal `cancelled` state;
- five repeated load/generate/unload memory cycles;
- bounded PSS growth;
- physical thermal capture before and after validation;
- exact model digest, architecture and quantization identity checks.

## Result

| Metric | Value |
| --- | ---: |
| Input tokens | 32 |
| Output tokens | 1 |
| TTFT | 779 ms |
| Total generation time | 779 ms |
| Reported decode throughput | 9.7087 tok/s |
| PSS samples | `[251227, 239881, 251721, 252081, 252743]` KB |
| PSS growth | **1,516 KB** |
| Thermal | `0 -> 0` |
| Full validation duration | 25,303 ms |
| Cancellation | `cancelled` |

The PSS growth of `1,516 KB` is comfortably below the existing `131,072 KB` lifecycle acceptance budget.

The reported decode throughput is **not** treated as a performance baseline because the sanity generation terminated after only one output token. This evidence closes lifecycle / cancellation / bounded-memory / thermal sanity for this physical run, but a separate sustained-generation benchmark is required for representative decode throughput.

## Raw privacy-safe report

```text
LOCAL_LLM_PHONE_TEST result=PASS
device=samsung/SM-A566B android=16 api=36 abis=arm64-v8a ramBytes=7777300480 cpuThreads=8
modelDigest=aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223
modelBytes=1280835840
architecture=qwen35
quantization=Q4_K_M
inputTokens=32
outputTokens=1
ttftMs=779
totalMs=779
decodeTokensPerSecond=9.70873786407767
cancellation=cancelled
pssSamplesKb=[251227, 239881, 251721, 252081, 252743]
pssGrowthKb=1516
thermalStart=0
thermalEnd=0
validationDurationMs=25303
privacy=report-excludes-prompts-and-generated-output;local-inference-audit=enabled
```

## Evidence boundary

The local evidence bundle contains `manifest.json`, `report.txt`, `screenshot.png`, `ui.xml`, and device/runner provenance. Generated evidence artifacts remain local and are not committed. The manifest hash above binds the local bundle version supplied for this run.

This record does not claim a sustained-throughput benchmark and does not replace separate release-source identity, LOW_MEMORY, cross-model switching, or other release-critical real-environment gates.

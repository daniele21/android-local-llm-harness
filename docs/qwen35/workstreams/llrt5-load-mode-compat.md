# LLRT-5 candidate load-mode compatibility

Status: active
Owner: llama-cpp-runtime

## Purpose

LLRT-0 qualified candidate `60addddf3c567c43ec3caf70fc953fba3572d96f` and deferred it for Harness 0.5 because the candidate removed `llama_model_params.use_mmap/use_mlock` and replaced them with `llama_load_mode`.

LLRT-5 first preserves existing product semantics across that API change. It does not promote the candidate pin and does not enable `AUTO` loading or change the frozen Harness 0.5 CPU baseline.

## Legacy semantic mapping

The exact candidate defines:

```text
NONE        = 0
MMAP        = 1
MLOCK       = 2
MMAP_MLOCK  = 3
DIRECT_IO   = 4
AUTO        = -1
```

The compatibility adapter maps the current booleans deterministically:

| useMmap | useMlock | Candidate load mode |
| --- | --- | --- |
| false | false | NONE |
| true | false | MMAP |
| false | true | MLOCK |
| true | true | MMAP_MLOCK |

`AUTO` and `DIRECT_IO` are intentionally not selected by this compatibility slice.

## Qualification model

`scripts/qualify-llama-cpp-candidate.sh` keeps the repository pin unchanged. For a candidate that exposes `load_mode` and no legacy mmap/mlock fields, it applies a temporary source overlay that routes the JNI model-load request through `model_load_params_compat.h`, then runs the requested native/Android qualification lanes.

The runner restores both:

- the original `llama_jni.cpp` source;
- the production llama.cpp submodule revision;

on normal exit, failure, interruption or cancellation.

An unknown candidate load API fails closed rather than guessing a mapping.

## Validation

`model_load_params_compat_test` covers old-API and new-API parameter shapes independently of llama.cpp and verifies all four legacy combinations.

Normal repository CI proves that the compatibility adapter does not regress the pinned release API. Exact candidate qualification remains a separate explicit lane because it temporarily checks out an unpinned upstream SHA.

## Next LLRT-5 slice

After candidate host-native and Android qualification pass with preserved legacy semantics:

1. record the effective material load mode in backend execution identity;
2. decide whether the newer pin is still worth promoting based on compatibility and evidence cost;
3. only then evaluate `load_mode=AUTO` as a distinct policy experiment;
4. treat tri-state Flash Attention separately from load-mode compatibility and require Q35/MEM correctness evidence before any default changes.

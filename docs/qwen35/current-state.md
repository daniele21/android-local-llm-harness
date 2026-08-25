# Qwen3.5 workstream state

Status: active
Document type: workstream-state
Owner: qwen35
Canonical scope: qwen35.state
Read when: determining Qwen3.5-only product progress, blockers or the next implementation slice
Last reviewed: 2026-08-25

This ledger reports only Qwen3.5-only product progress. Repository-wide integrated state remains owned by [`../current-state.md`](../current-state.md).

## Product progress

| ID | Workstream | State | Exit condition |
| --- | --- | --- | --- |
| Q35-0 | Decision and progressive-disclosure plan | DONE | ADR 0011, target, architecture, roadmap and focused owners agree. |
| Q35-1 | Curated model baseline | DONE | Closed Qwen3.5-only product surface and applicable repository/package validation pass. |
| Q35-2 | Model/backend compatibility | DONE | Exact curated 0.8B/2B Q4_K_M artifacts pass identity, GGUF and pinned-backend smoke validation. |
| Q35-3 | Thinking/template/sampling | DONE | Neutral thinking intent, typed Jinja kwargs, tier-aware profiles and sampler fields resolve end-to-end. |
| Q35-4 | Generation guard | DONE | Runaway/repetitive thinking is bounded with typed stop reasons and deterministic runtime tests. |
| Q35-5 | Runtime/context/cache capability model | DONE | Context and reuse paths are backend-revision-bound and do not assume pure KV-cache semantics. |
| Q35-6 | Android runtime tuning | IN PROGRESS | Software matrix/evidence tooling is complete; representative 0.8B/2B physical qualification and measured-profile selection remain. |
| Q35-7 | Validation suite | PLANNED | Golden/integration/device gates pass for the supported matrix. |
| Q35-8 | Certification | PLANNED | Exact curated artifacts receive evidence-backed certification independently of catalog availability. |

## Completed baseline

### Q35-1

- seven reviewed Qwen3.5 dense 0.8B/2B releases only;
- no consumer manual GGUF import or arbitrary product extension path;
- catalog-anchored binding, persistence and inventory;
- unified Models presentation;
- non-destructive runtime unload separate from model removal;
- user manual smoke validation plus repository/Android/package gates green.

### Q35-2

- pinned llama.cpp revision `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3`;
- exact 0.8B/2B Q4_K_M SHA-256 and size identities;
- trusted GGUF structural fingerprints and fail-closed installer validation;
- privacy-safe compatibility evidence;
- final compatibility run #6 passed exact identity and `load -> tokenize -> minimal generate` for both reference artifacts.

### Q35-3

- neutral `ThinkingMode.ENABLED/DISABLED`;
- typed llama.cpp Jinja `enable_thinking`, with no `/think` or `/nothink` prompt hack;
- end-to-end `minP` and `presencePenalty` plus existing sampler controls;
- versioned tier-aware Fast, Quality, Thinking, Precise and JSON profiles;
- deterministic preset/default/request precedence;
- effective scalar generation telemetry and Room persistence;
- Playground controls plus unit/native/instrumentation coverage;
- `libllama-common.so` explicitly verified in Android packaging.

### Q35-4

- versioned bounded generation guard policy;
- thinking budget plus chunk-boundary-independent repetition/runaway detection;
- typed guard stop reasons distinct from user cancellation, max-token completion and backend failure;
- streaming is interrupted through the backend callback while the public terminal preserves the guard stop reason;
- privacy-safe stop telemetry and deterministic runtime tests cover guard behavior.

### Q35-5

- Qwen3.5 runtime capabilities are bound to the exact pinned llama.cpp revision;
- approved mobile context tiers are `1024`, `2048`, `4096` and `8192` tokens with a 256-token safety reserve;
- Auto context chooses the smallest approved tier that satisfies prompt + output + reserve;
- stateless context reuse, prefix snapshot, session restore and prefix reuse are fail-closed/disabled pending explicit proof;
- candidate 0.8B and 2B runtime profiles remain separate and carry `CANDIDATE` evidence status.

## Current slice: Q35-6 Android runtime tuning

The repository-side tuning harness is implemented:

1. exact benchmark execution identity is SHA-256 fingerprinted from context, preset/version, thinking, sampler, seed, template and output configuration;
2. baseline matching rejects incompatible execution identities;
3. Room schema v8 persists execution identity; legacy benchmark baselines are dropped rather than assigned invented identity during migration;
4. the physical-device matrix covers both curated Q4_K_M reference artifacts across 1K/2K/4K/8K contexts, 2/4 threads, 64/32 and 128/64 batch/ubatch, and thinking enabled/disabled;
5. each case records one true cold sample followed by at least three warm samples in the same loaded runtime;
6. evidence schema v2 records model/backend/harness/profile/device identity plus TTFT, prefill/decode throughput, memory and thermal snapshots;
7. the summarizer validates identity/sample completeness and marks only comparable cases as `eligibleForProfileSelection`;
8. no script automatically promotes a runtime profile from `CANDIDATE` to `MEASURED`.

### LLRT-6 canonical `2048 / 64` KV-cache findings

LLRT-6C ran on `SM-A566B` (Android 16 / SDK 36 / arm64), Harness `67ccc05474e3d99f024dfe53ac832dbefcb6cce9`, pinned llama.cpp, seed `42`, `b128/ub64`, thinking off, with 1 cold + 3 warm samples. All cases were eligible and thermal status stayed `0`. K-only/FA-off compares with `release-default`; quantized K+V compares with `f16/f16 + FA` because quantized V requires FA on this pin.

#### Qwen3.5 0.8B (`t2/bt4`)

| Case | Warm total median | Peak observed PSS | Decision |
| --- | ---: | ---: | --- |
| `release-default` | 82.110 s | 1,196,873 KB | control |
| `K=q8_0`, FA off | 82.019 s | 1,194,665 KB | REJECT: ~0.1% faster, ~2 MiB lower PSS, digest differs |
| `K=q4_0`, FA off | 82.110 s | 1,192,122 KB | REJECT: latency neutral, ~5 MiB lower PSS, digest differs |
| `f16/f16 + FA` | 81.853 s | 1,064,813 KB | FA-on control only |
| `q8_0/q8_0 + FA` | 81.938 s | 1,192,377 KB | REJECT: ~125 MiB higher PSS, digest differs |
| `q4_0/q4_0 + FA` | 81.730 s | 1,187,535 KB | REJECT: ~120 MiB higher PSS, digest differs |

The 0.8B policy stays at release defaults. Lower PSS for the FA control versus release baseline is not attributable to KV type because FA also changes.

#### Qwen3.5 2B (`t4/bt4`)

| Case | Warm total median | Peak observed PSS | Decision |
| --- | ---: | ---: | --- |
| `release-default` | 217.839 s | 2,132,802 KB | control |
| `K=q8_0`, FA off | 218.220 s | 2,645,895 KB | REJECT: +0.17% latency, ~501 MiB higher PSS, digest differs |
| `K=q4_0`, FA off | 237.354 s | 2,642,881 KB | REJECT: +8.96% latency, ~498 MiB higher PSS, digest differs |
| `f16/f16 + FA` | 219.067 s | 2,571,445 KB | FA-on control only |
| `q8_0/q8_0 + FA` | 233.648 s | 1,897,476 KB | REJECT default: ~658 MiB lower PSS, +6.66% latency, digest differs |
| `q4_0/q4_0 + FA` | 218.048 s | 2,433,852 KB | RESEARCH ONLY: ~134 MiB lower PSS, latency neutral, digest differs |

### LLRT-6 decision

LLRT-6C closes **KEEP DEFAULTS** for both tiers. K-only quantization is rejected and quantized K+V is not promoted. The 2B `q4_0/q4_0 + FA` signal remains research-only pending explicit quality evidence; no profile becomes `MEASURED`.

### LLRT-9 short-profile diagnosis

On Samsung `SM-A566B`, Qwen3.5 2B at `1024 / 8` passed exact serial/native parity at widths `2` and `3`, then failed at width `4` for source prompt `2`. A dedicated follow-up on Harness `0ec8b1bf44c700a57f7f50fa512e8c1ea03a518e` showed that the mismatch followed source prompt `2` after prompt permutation and disappeared under greedy sampling. The evidence therefore favors prompt-sensitive numerical/stochastic divergence rather than a slot/sequence/KV attribution defect. The exact-output gate remains unchanged.

### LLRT-9 canonical `2048 / 64` physical findings

The canonical LLRT-9C profile ran on the same Samsung `SM-A566B`, Harness `3ac9a64ffc115de14beb890065ae4719f065787b`, pinned llama.cpp, seed `42`, `b128/ub64`, thinking disabled and four order-balanced samples per width.

#### Qwen3.5 2B (`t4/bt4`)

| Width | Correctness | Serial median | Native-batch median | Result |
| ---: | --- | ---: | ---: | --- |
| 2 | PASS, 4/4 exact digest + token parity | 91.50 s | 84.71 s | `1.080x`, positive |
| 3 | PASS, 4/4 exact digest + token parity | 131.71 s | 119.40 s | `1.103x`, positive |
| 4 | FAIL at sample 0 | n/a | n/a | UNQUALIFIED |

Width `4` reproduced the same source-prompt-2 digest divergence seen in the short profile (`b118...` serial vs `d722...` native batch). Width `2` reduces median wall-clock by about `7.4%`, width `3` by about `9.4%`, and thermal status remained `0`. Width `3` is the highest passing canonical width for this exact device/profile, not a global product cap.

#### Qwen3.5 0.8B (`t2/bt4`)

| Width | Correctness | Serial median | Native-batch median | Result |
| ---: | --- | ---: | ---: | --- |
| 2 | PASS, 4/4 exact digest + token parity | 100.69 s | 115.39 s | batch `14.6%` slower |
| 3 | PASS, 4/4 exact digest + token parity | 119.42 s | 132.53 s | batch `11.0%` slower |
| 4 | FAIL at sample 0 | n/a | n/a | UNQUALIFIED |

Width `4` failed the per-case token-count gate: serial `[24,45,8,7]` versus native batch `[39,24,10,7]`. This is a hard correctness failure but does not alone prove sequence mis-attribution. Because widths `2` and `3` are already slower, native batching is **REJECTED for the canonical 0.8B profile on this device**.

### Tier-specific LLRT-9 decision

For this exact device/backend/profile identity:

- **0.8B:** widths `2`/`3` are correct but slower; width `4` fails correctness; native batching is rejected.
- **2B:** widths `2`/`3` are correct and faster; width `4` fails correctness; native batching remains a candidate only up to width `3`.

This is evidence against one global Qwen3.5 batching policy. Correctness gates remain fail-closed and no result promotes a runtime profile to `MEASURED`.

Q35-6 remains `IN PROGRESS`: canonical LLRT-6 and LLRT-9 on this device are complete, but broader representative-device evidence and lifecycle/memory acceptance remain before profile promotion or generalized production policy.

## Remaining Q35-6 evidence

- continue broader/representative 0.8B and 2B physical tuning required by measured-profile acceptance;
- keep KV-cache release defaults for both tiers on this exact device/profile unless later quality-backed evidence reopens the decision;
- preserve 2B `q4_0/q4_0 + FA` only as a research-only memory signal, not a runtime policy;
- keep 0.8B native batching rejected and 2B width `4` unqualified for this exact device/profile unless superseded by a later evidence wave;
- mark profiles `MEASURED` only after TTFT, prefill/decode throughput, peak PSS and thermal evidence is recorded;
- validate cancellation, model switching, memory pressure and idle unload on measured configurations.

## State transition rule

Move a row from `PLANNED` to `IN PROGRESS` only when implementation starts on `dev`. Move it to `DONE` only when its owning acceptance criteria and applicable repository gates pass. Do not use percentage completion.

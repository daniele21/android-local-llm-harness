# Qwen3.5 workstream state

Status: active
Document type: workstream-state
Owner: qwen35
Canonical scope: qwen35.state
Read when: determining Qwen3.5-only product progress, blockers or the next implementation slice
Last reviewed: 2026-08-24

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

### LLRT-9 short-profile physical findings

A physical Samsung `SM-A566B` run on exact Harness commit `016467c300e84decb16697850aaef40d5e592753`, pinned llama.cpp `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3`, Qwen3.5 2B Q4_K_M, context `1024`, output budget `8`, seed `42`, batch `128`, ubatch `64`, CPU/batch threads `4/4` established a useful bounded signal without closing canonical LLRT-9C:

- width `2`: all four balanced samples preserved exact serial/native output digests and per-case output-token counts; native batching was consistently faster, with median speedup around `1.055x`;
- width `3`: all four balanced samples preserved exact serial/native output digests and per-case output-token counts; median speedup increased to roughly `1.08x`;
- width `4`: the first `SERIAL_FIRST` sample failed the hard exact-output gate because source prompt index `2` diverged while source prompt indices `0`, `1` and `3` remained identical;
- thermal status remained `0` throughout the reported samples, so the width-4 mismatch was not accompanied by an observed thermal escalation.

The follow-up width-4 diagnostic ran on exact Harness commit `0ec8b1bf44c700a57f7f50fa512e8c1ea03a518e` with the same device, model artifact, pinned backend and short-profile runtime envelope. It classified the mismatch:

- `baseline-quality` reproduced the mismatch only for source prompt `2` in slot `2`; token counts still matched and thermal status remained `0`;
- `swap02-quality` moved source prompt `2` to slot `0`, and the mismatch moved with that prompt to slot `0`; source prompt `0`, now occupying slot `2`, matched exactly;
- `baseline-greedy` produced exact serial/native output-digest and token-count parity for all four prompts at width `4`;
- therefore the observed failure is **prompt-following and sampling-sensitive**, which favors numerical sensitivity under stochastic Quality sampling rather than a slot/sequence/KV attribution defect.

The durable decision is deliberately fail-closed: the exact-output gate remains unchanged, and **width `4` under Quality sampling is not qualified by this short-profile evidence**.

### LLRT-9 canonical `2048 / 64` physical findings

The canonical LLRT-9C profile was then run on the same Samsung `SM-A566B`, exact Harness commit `3ac9a64ffc115de14beb890065ae4719f065787b`, pinned llama.cpp revision, seed `42`, `b128/ub64`, thinking disabled and four order-balanced samples per width. Both curated Q4_K_M tiers were exercised.

#### Qwen3.5 2B

`threads=4`, `batchThreads=4`:

| Width | Correctness | Serial median | Native-batch median | Median speedup | Decision |
| ---: | --- | ---: | ---: | ---: | --- |
| 2 | PASS, 4/4 exact digest + token parity | 91.50 s | 84.71 s | `1.080x` | positive candidate |
| 3 | PASS, 4/4 exact digest + token parity | 131.71 s | 119.40 s | `1.103x` | positive candidate |
| 4 | FAIL at sample 0 | n/a | n/a | n/a | UNQUALIFIED |

Width `4` reproduced the same prompt-sensitive digest divergence already seen in the short profile: source prompt index `2` changed from digest prefix `b118...` in serial execution to `d722...` in native batch, while the other three prompt outputs matched. This confirms that the width-4 Quality mismatch is not specific to the `1024 / 8` diagnostic workload.

For this exact device/profile identity, width `2` reduces median wall-clock by about `7.4%` and width `3` by about `9.4%`; thermal status remained `0`. Width `3` is the highest canonical width that passed the exact-output gate on this device. This is still an evidence-backed device/tier finding, not a global product cap for unrelated hardware.

#### Qwen3.5 0.8B

`threads=2`, `batchThreads=4`:

| Width | Correctness | Serial median | Native-batch median | Relative result | Decision |
| ---: | --- | ---: | ---: | ---: | --- |
| 2 | PASS, 4/4 exact digest + token parity | 100.69 s | 115.39 s | native batch `14.6%` slower | REJECT for performance |
| 3 | PASS, 4/4 exact digest + token parity | 119.42 s | 132.53 s | native batch `11.0%` slower | REJECT for performance |
| 4 | FAIL at sample 0 | n/a | n/a | n/a | UNQUALIFIED |

Width `4` failed before output-digest comparison because per-case output-token counts diverged: serial `[24,45,8,7]` versus native batch `[39,24,10,7]`. This is a hard correctness failure under the LLRT-9C contract. It does **not** by itself prove sequence mis-attribution, because divergent sampled outputs can also terminate at different lengths; no production conclusion should over-interpret the failure mechanism.

The 0.8B result is nevertheless decisive for policy selection on this exact device/profile: native multi-sequence batching provides no throughput benefit at widths `2` or `3`, and width `4` additionally fails correctness. Therefore native batching is **REJECTED for the canonical 0.8B profile on this device**; further width-4 root-cause work is not required for product promotion unless the native batching implementation is revisited independently.

### Tier-specific LLRT-9 decision from the canonical wave

For this exact Samsung/device/backend/profile identity:

```text
Qwen3.5 0.8B canonical Quality:
  width 2 -> correctness PASS, performance REJECT
  width 3 -> correctness PASS, performance REJECT
  width 4 -> correctness FAIL / UNQUALIFIED
  native batching -> REJECT for product use on this device/profile

Qwen3.5 2B canonical Quality:
  width 2 -> correctness PASS, performance POSITIVE
  width 3 -> correctness PASS, performance POSITIVE
  width 4 -> correctness FAIL / UNQUALIFIED
  native batching -> candidate only up to width 3 on this device/profile
```

The result is explicitly tier-specific. It is evidence against a single global batching policy for all Qwen3.5 dense tiers. The exact-output and per-case token gates remain unchanged; no failure is relaxed to obtain a favorable performance result.

Q35-6 remains `IN PROGRESS` because canonical LLRT-9 evidence on this device is only one part of measured-profile selection. Broader representative-device evidence and lifecycle/memory acceptance remain before any profile becomes `MEASURED` or a production policy is generalized.

## Remaining Q35-6 evidence

- preserve the completed canonical LLRT-9 0.8B/2B findings as tier-specific physical evidence for Samsung `SM-A566B`;
- continue broader/representative 0.8B and 2B physical tuning evidence required by the measured-profile acceptance criteria;
- review eligible evidence separately by tier and choose evidence-backed runtime defaults;
- keep 0.8B native batching rejected for this exact device/profile unless a later intentionally re-opened evidence wave supersedes it;
- keep 2B width `4` Quality unqualified and treat widths `2`/`3` only as device/profile candidates until policy promotion criteria are satisfied;
- mark selected profiles `MEASURED` only after TTFT, prefill/decode throughput, peak PSS and thermal evidence is recorded;
- validate cancellation, model switching, memory pressure and idle unload on the measured configurations.

## State transition rule

Move a row from `PLANNED` to `IN PROGRESS` only when implementation starts on `dev`. Move it to `DONE` only when its owning acceptance criteria and applicable repository gates pass. Do not use percentage completion.

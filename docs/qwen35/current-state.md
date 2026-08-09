# Qwen3.5 workstream state

Status: active
Document type: workstream-state
Owner: qwen35
Canonical scope: qwen35.state
Read when: determining Qwen3.5-only product progress, blockers or the next implementation slice
Last reviewed: 2026-08-09

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
| Q35-6 | Android runtime tuning | IN PROGRESS | Software matrix/evidence tooling is complete; 0.8B/2B physical-device evidence and measured-profile selection remain. |
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

Q35-6 remains `IN PROGRESS` because physical evidence must still be collected for both tiers and reviewed before selecting versioned measured defaults.

## Remaining Q35-6 evidence

- run the complete 0.8B Q4_K_M matrix on representative physical Android hardware;
- run the complete 2B Q4_K_M matrix on the same device classes;
- review eligible evidence separately by tier and choose evidence-backed runtime defaults;
- mark selected profiles `MEASURED` only after TTFT, prefill/decode throughput, peak PSS and thermal evidence is recorded;
- validate cancellation, model switching, memory pressure and idle unload on the measured configurations.

## State transition rule

Move a row from `PLANNED` to `IN PROGRESS` only when implementation starts on `dev`. Move it to `DONE` only when its owning acceptance criteria and applicable repository gates pass. Do not use percentage completion.

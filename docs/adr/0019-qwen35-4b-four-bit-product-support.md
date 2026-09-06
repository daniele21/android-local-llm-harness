# ADR 0019: Qwen3.5 4B four-bit product support

- Status: Accepted
- Date: 2026-09-06

## Context

ADR 0011 deliberately closed the Harnex product model surface to Qwen3.5 dense 0.8B and 2B and required a new product decision before admitting another tier. The product now needs the Unsloth `Qwen3.5-4B-GGUF` release to support higher-capability local use cases while keeping the catalog closed, verifiable and Android-resource-aware.

The Unsloth 4B repository publishes multiple quantizations. Harnex does not need the full repository surface. The requested envelope is 4-bit only, with immutable artifact identity and Harnex-owned generation policy derived from Unsloth's Qwen3.5 recommendations.

## Decision

Extend the closed Qwen3.5 product envelope with dense **4B, 4-bit-only** GGUF artifacts from the repository-reviewed Unsloth release. ADR 0011 remains authoritative for the Qwen3.5-only and family-neutral-contract decisions; this ADR supersedes only its 0.8B/2B tier restriction.

The curated 4B set is exactly:

- `IQ4_XS`;
- `IQ4_NL`;
- `Q4_0`;
- `Q4_1`;
- `Q4_K_S`;
- `Q4_K_M`;
- `UD-Q4_K_XL`.

Every artifact is pinned to an exact Hugging Face source revision, SHA-256 and byte size. No 5-bit, 6-bit, 8-bit, full-precision, arbitrary URL or user-supplied 4B artifact enters the product surface.

`UD-Q4_K_XL` is the preferred 4B validation candidate because Unsloth uses that quantization in its `llama.cpp` examples. Preference does not imply certification: all 4B artifacts remain `CANDIDATE` until exact-artifact physical-device evidence passes the applicable acceptance matrix.

Harnex owns tier-aware sampling defaults. For the 4B tier it maps the existing preset vocabulary to Unsloth guidance:

| Harnex intent | Thinking | temperature | top_p | top_k | min_p | presence_penalty | repeat_penalty |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Fast / JSON | disabled | 0.7 | 0.8 | 20 | 0 | 1.5 | 1.0 |
| Quality / reasoning | disabled | 1.0 | 0.95 | 20 | 0 | 1.5 | 1.0 |
| Thinking | enabled | 1.0 | 0.95 | 20 | 0 | 1.5 | 1.0 |
| Precise / coding | enabled | 0.6 | 0.95 | 20 | 0 | 0 | 1.0 |

Thinking is enabled through the typed Qwen3.5 chat-template argument, not prompt-string hacks. Existing 0.8B and 2B generation behavior is not changed by admitting 4B.

The initial Android resource policy is deliberately conservative: 8 GB total device RAM is the catalog minimum and 12 GB is recommended. Runtime tuning remains a separate unmeasured 4B candidate with bounded Harnex context tiers; model-advertised maximum context does not override Android resource policy.

## Consequences

- The executable product catalog contains Qwen3.5 dense 0.8B, 2B and 4B releases, with 4B restricted to the seven reviewed 4-bit artifacts above.
- Public lifecycle, Binder, model-authority and telemetry contracts remain family-neutral and unchanged.
- Consumers still select from Harnex-reviewed model/profile policy rather than supplying raw model URLs or sampling configuration as product authority.
- The 4B tier can use the existing `llama.cpp` generation path; no new native sampling primitive is introduced.
- Catalog compatibility can reject low-memory devices before a 4B download/load attempt.
- 4B certification is not inherited from 0.8B/2B or between quantizations. Physical-device latency, memory, thermal and output-quality evidence remains required per exact artifact.

## Alternatives considered

### Add only Q4_K_M

Rejected because the user requirement is the Unsloth 4-bit surface, and the seven reviewed 4-bit variants are useful for measured quality/memory/throughput trade-offs without reopening arbitrary model import.

### Add every Unsloth 4B quantization

Rejected because it violates the explicit 4-bit-only product constraint and expands storage/device validation cost without product need.

### Reuse the 2B tier internally

Rejected because generation defaults, resource policy, evidence and future tuning are tier-specific. Treating 4B as 2B would silently apply the wrong policy and make telemetry/evidence ambiguous.

### Expose Unsloth parameters directly to consumer apps

Rejected because Harnex owns model-specific execution policy. Public consumers should express use-case intent while the Host resolves the exact model and effective generation configuration.

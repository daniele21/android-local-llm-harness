# OMBRA model-quality support policy v1

Status: active
Document type: release-policy
Owner: apps/local-llm-console
Canonical scope: shared-runtime.consumer-api.pii-redactor.quality-policy.v1
Read when: running or reviewing OMBRA supported-model quality evaluation and release acceptance
Last reviewed: 2026-08-15
Registered: 2026-08-15

This policy is registered **before execution of the supported Qwen3.5 quality runs**. Its values are product acceptance criteria, not thresholds fitted to observed model results. A model that does not satisfy the policy is not declared supported for the affected OMBRA release/category claim; the policy is not lowered after seeing results.

## Frozen corpus identity

- schema version: `1`
- corpus version: `ombra-pii-synthetic-v2`
- SHA-256: `a04f79dec42ee4208e4db27512664cc20f66cc863fd80ae4fcdc1019a2f37a5f`
- required categories: `full-name`, `email`, `telephone`, `postal-address`, `italian-tax-code`, `iban`, `custom-1`

The corpus contains five positive exact occurrences for every required category and 35 exact positive occurrences in aggregate. It also contains negative, near-miss, repeated, overlap, injection-like and Italian-text cases.

## Release-support thresholds

| Metric | Threshold |
| --- | ---: |
| aggregate precision | `>= 0.90` |
| aggregate recall | `>= 0.98` |
| aggregate F1 | `>= 0.94` |
| per-category precision | `>= 0.80` |
| per-category recall | `>= 0.90` |
| per-category F1 | `>= 0.85` |
| structured completion | `>= 0.98` |
| invalid finding rate | `<= 0.02` |
| invalid result rate | `<= 0.00` |

## Why recall is stricter

OMBRA requires human review before export. A false positive can therefore be inspected and changed to `Ignora`; a false negative never enters the review surface and cannot be corrected there. The release-support policy consequently prioritizes recall over precision.

Because the frozen v2 corpus is discrete, the recall/completion thresholds are intentionally stronger than their decimal representation suggests:

- `5` positives per category means `4/5 = 0.80`, so per-category recall `>= 0.90` requires `5/5`;
- `35` positives aggregate means one miss gives `34/35 ~= 0.971`, so aggregate recall `>= 0.98` requires `35/35`;
- `32` cases means one incomplete case gives `31/32 ~= 0.969`, so structured completion `>= 0.98` requires all `32/32` cases.

The precision thresholds allow a small number of reviewable false positives while still rejecting noisy category behavior. Invalid structured results remain fail-closed.

## Change control

Changing any threshold, required category, corpus version or corpus hash creates a new policy version. Do not edit v1 in place after model results exist. Experimental measurements may be recorded below the support threshold, but they do not become supported-model claims.

Latency, TTFT, token rate, memory and thermal behavior are recorded separately from semantic quality and cannot compensate for a failed quality gate.

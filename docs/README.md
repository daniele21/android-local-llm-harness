# Documentation map

Status: active
Document type: documentation-governance
Owner: repository
Canonical scope: documentation.routing
Read when: locating documentation ownership or changing documentation governance
Last reviewed: 2026-08-23

Use progressive disclosure: root guide -> closest scoped guide -> focused owner. One fact has one canonical owner. Machine policy: [`.engineering/documentation-policy.json`](../.engineering/documentation-policy.json). [`documentation-policy.json`](documentation-policy.json) is only its compatibility symlink.

## Canonical sources

| Question | Canonical source |
| --- | --- |
| Integrated state/blockers | [`current-state.md`](current-state.md) |
| Capability milestones | [`roadmap.md`](roadmap.md) |
| Repository target | [`implementation-plan.md`](implementation-plan.md) |
| Active bounded repository work | [`workstreams/`](workstreams/README.md) when needed |
| Architecture hardening | [`reference-architecture-hardening-plan.md`](reference-architecture-hardening-plan.md), [`reference-architecture-hardening-progress.md`](reference-architecture-hardening-progress.md) |
| llama.cpp optimization | [`llama-cpp-runtime-optimization-plan.md`](llama-cpp-runtime-optimization-plan.md) |
| Memory management | [`memory-management/README.md`](memory-management/README.md) |
| Qwen3.5 | [`qwen35/README.md`](qwen35/README.md) |
| Model evaluation | [`model-evaluation/README.md`](model-evaluation/README.md) |
| Architecture/decisions | [`architecture.md`](architecture.md), [`adr/`](adr/) |
| Merge/release completion | [`definition-of-done.md`](definition-of-done.md), [`releases/harness-0.5.md`](releases/harness-0.5.md) |
| Historical exception material | [`archive/`](archive/) |

## Active source index

### Architecture, delivery and API

- [`implementation-plan.md`](implementation-plan.md)
- [`reference-architecture-hardening-plan.md`](reference-architecture-hardening-plan.md)
- [`reference-architecture-hardening-progress.md`](reference-architecture-hardening-progress.md)
- [`llama-cpp-runtime-optimization-plan.md`](llama-cpp-runtime-optimization-plan.md)
- [`memory-management/README.md`](memory-management/README.md)
- [`workstreams/README.md`](workstreams/README.md)
- [`architecture.md`](architecture.md)
- [`adr/README.md`](adr/README.md)
- [`api-usage.md`](api-usage.md)
- [`shared-runtime/README.md`](shared-runtime/README.md)
- [`shared-runtime/consumer-api/pii-redactor/README.md`](shared-runtime/consumer-api/pii-redactor/README.md)
- [`definition-of-done.md`](definition-of-done.md)
- [`versioning.md`](versioning.md)
- [`releases/harness-0.5.md`](releases/harness-0.5.md)

### Model lifecycle and generation

- [`qwen35/README.md`](qwen35/README.md)
- [`model-catalog-download-plan.md`](model-catalog-download-plan.md)
- [`curated-model-catalog.md`](curated-model-catalog.md)
- [`secure-model-download.md`](secure-model-download.md)
- [`model-installation.md`](model-installation.md)
- [`phone-model-distribution.md`](phone-model-distribution.md)
- [`model-management-phone.md`](model-management-phone.md)
- [`harness-model-inventory-state.md`](harness-model-inventory-state.md)
- [`generation-configuration-and-prompting-plan.md`](generation-configuration-and-prompting-plan.md)

### Observability, diagnostics and evaluation

- [`console-observability.md`](console-observability.md)
- [`health-engine.md`](health-engine.md)
- [`resource-observability.md`](resource-observability.md)
- [`benchmark-engine.md`](benchmark-engine.md)
- [`model-evaluation/README.md`](model-evaluation/README.md)
- [`harness-telemetry-composition.md`](harness-telemetry-composition.md)
- [`harness-logs-composition.md`](harness-logs-composition.md)
- [`harness-health-composition.md`](harness-health-composition.md)
- [`harness-resource-composition.md`](harness-resource-composition.md)
- [`harness-benchmark-composition.md`](harness-benchmark-composition.md)

### Applications, UX and brand

- [`features/phone-app-architecture.md`](features/phone-app-architecture.md)
- [`harness-ux-ui-implementation-plan.md`](harness-ux-ui-implementation-plan.md)
- [`harness-ux-ui-implementation-progress.md`](harness-ux-ui-implementation-progress.md)
- [`phone-inference-playground.md`](phone-inference-playground.md)
- [`console-inference-playground.md`](console-inference-playground.md)
- [`design-system.md`](design-system.md)
- [`harness-brand-guidelines.md`](harness-brand-guidelines.md)
- [`android-brand-assets.md`](android-brand-assets.md)
- [`assets/brand/README.md`](assets/brand/README.md)
- [`assets/brand/master/README.md`](assets/brand/master/README.md)
- [`shared-runtime/consumer-api/assets/README.md`](shared-runtime/consumer-api/assets/README.md)

### Build, distribution and evidence

- [`android-build-and-run.md`](android-build-and-run.md)
- [`android-upload-key.md`](android-upload-key.md)
- [`device-e2e-testing.md`](device-e2e-testing.md)
- [`device-e2e-evidence.md`](device-e2e-evidence.md)
- [`play-internal-phone-test.md`](play-internal-phone-test.md)
- [`emulator-e2e-results.md`](emulator-e2e-results.md)

## Document lifecycle

Durable types: `roadmap`, `target-specification`, `feature-index`, `feature-specification`, `architecture`, `adr-index`, `api-reference`, runbooks/evidence, release/completion policy and design/asset owners. `current-state` is the single repository operational ledger.

`workstream-state` is temporary. New repository-level workstreams live under [`workstreams/`](workstreams/README.md). Existing legacy plan/progress sources remain valid until intentionally consolidated; do not create new paired status files.

Completed workstreams are **deleted by default** after durable transfer. `archive/` is exception-only for independent audit, regulatory, release-evidence or historical value. Git history owns normal implementation history.

## Before creating a document

1. Search `Canonical scope` and this index.
2. Update an existing owner when possible.
3. Create only a durable independent owner or a genuinely necessary bounded workstream.
4. Set supported type, owner, unique canonical scope and precise `Read when`.
5. Link it from this or the closest domain index.
6. Finalize temporary work by transferring durable knowledge and deleting it; archive only under the exception rule.

Do not create documentation merely to record a branch, PR or isolated implementation completion.

## Required metadata

Every active non-ADR Markdown document under `docs/` contains:

```text
Status: active
Document type: <supported type>
Owner: <repository or domain>
Canonical scope: <unique dotted scope>
Read when: <specific trigger>
Last reviewed: YYYY-MM-DD
```

ADRs keep ADR status/date format. Archived documents use `Status: historical`.

## Reading and writing budgets

CI enforces [`.engineering/documentation-policy.json`](../.engineering/documentation-policy.json): budgets, reachability, unique canonical scopes, duplicate detection and agent-guide limits. Baselined oversized documents may only shrink.

## Precedence

Executable contracts/tests -> accepted ADRs -> architecture -> focused specifications -> target overview -> current state -> roadmap -> README/agent guides -> retained history. Correct the owning source rather than silently reconciling behavioral contradictions.

## Validation

```bash
python3 scripts/verify-docs.py --base <target-branch-commit>
python3 scripts/verify-agent-navigation.py
python3 -m py_compile scripts/*.py
git diff --check
```

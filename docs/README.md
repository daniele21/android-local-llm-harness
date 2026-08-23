# Documentation map

Status: active
Document type: documentation-governance
Owner: repository
Canonical scope: documentation.routing
Read when: locating the canonical owner of repository documentation or changing documentation governance
Last reviewed: 2026-08-23

Documentation uses progressive disclosure: an agent starts from the repository guide, adds the closest scoped guide, then reads only the focused source that owns the question. A fact has one canonical owner; summaries link to that owner instead of repeating the same claim at the same precision.

Machine-enforced document types, reading budgets and duplication thresholds are defined in [`.engineering/documentation-policy.json`](../.engineering/documentation-policy.json). The legacy [`documentation-policy.json`](documentation-policy.json) path is a compatibility symlink to that owner, not a second policy source.

## Canonical sources

| Question | Canonical source |
| --- | --- |
| What is integrated, blocked or next? | [`current-state.md`](current-state.md) |
| Which capabilities and milestones remain? | [`roadmap.md`](roadmap.md) |
| What is the repository-level target? | [`implementation-plan.md`](implementation-plan.md), then the focused specification |
| What bounded repository workstream is active? | [`workstreams/`](workstreams/README.md), when a temporary cross-PR ledger is actually required |
| What is the reference-grade architecture hardening target and current workstream state? | [`reference-architecture-hardening-plan.md`](reference-architecture-hardening-plan.md), then [`reference-architecture-hardening-progress.md`](reference-architecture-hardening-progress.md) |
| How is llama.cpp runtime efficiency, upstream qualification and hardware execution staged? | [`llama-cpp-runtime-optimization-plan.md`](llama-cpp-runtime-optimization-plan.md) |
| How is runtime memory residency, admission, pressure handling and validation governed? | [`memory-management/README.md`](memory-management/README.md) |
| What is the Qwen3.5-only product status and plan? | [`qwen35/README.md`](qwen35/README.md) |
| How are model quality and dataset-based performance evaluated? | [`model-evaluation/README.md`](model-evaluation/README.md) |
| What architecture exists today? | [`architecture.md`](architecture.md) and accepted [`adr/`](adr/) records |
| What is required before merge or release? | [`definition-of-done.md`](definition-of-done.md) |
| What remains for Harness 0.5.0? | [`releases/harness-0.5.md`](releases/harness-0.5.md) |
| How is a procedure executed? | The applicable build, signing, device or evidence runbook |
| Which historical material is retained independently of Git history? | [`archive/`](archive/) only for audit, regulatory, release-evidence or other independently justified history |

## Active source index

Use this index to locate a source, not as a mandatory reading list.

### Architecture, delivery and API

- [`implementation-plan.md`](implementation-plan.md) — repository target overview and focused-spec routing
- [`reference-architecture-hardening-plan.md`](reference-architecture-hardening-plan.md) — cross-cutting reference-grade architecture target, milestones and dependency sequencing
- [`reference-architecture-hardening-progress.md`](reference-architecture-hardening-progress.md) — concise architecture-hardening workstream state and next slice
- [`llama-cpp-runtime-optimization-plan.md`](llama-cpp-runtime-optimization-plan.md) — staged llama.cpp upstream qualification, CPU efficiency, device/backend execution and accelerator evidence target
- [`memory-management/README.md`](memory-management/README.md) — runtime memory ownership, residency, budgeting, admission, pressure response and validation workstream
- [`workstreams/README.md`](workstreams/README.md) — repository-level bounded workstream lifecycle
- [`architecture.md`](architecture.md) — current dependency and ownership boundaries
- [`adr/README.md`](adr/README.md) — accepted durable decisions
- [`api-usage.md`](api-usage.md) — embedded public API assembly and lifecycle
- [`shared-runtime/README.md`](shared-runtime/README.md) — planned cross-application Binder runtime routing and workstreams
- [`shared-runtime/consumer-api/pii-redactor/README.md`](shared-runtime/consumer-api/pii-redactor/README.md) — OMBRA PDF/PII reference-consumer product and implementation plan
- [`definition-of-done.md`](definition-of-done.md) — merge and production completion policy
- [`versioning.md`](versioning.md) — version and release policy
- [`releases/harness-0.5.md`](releases/harness-0.5.md) — Harness 0.5.0 release gates

### Model lifecycle and generation

- [`qwen35/README.md`](qwen35/README.md) — Qwen3.5-only product status, routing and implementation plan
- [`model-catalog-download-plan.md`](model-catalog-download-plan.md) — distribution lifecycle entry point
- [`curated-model-catalog.md`](curated-model-catalog.md) — catalog releases and compatibility
- [`secure-model-download.md`](secure-model-download.md) — verified network transfer
- [`model-installation.md`](model-installation.md) — inspection, publication and rollback
- [`phone-model-distribution.md`](phone-model-distribution.md) — phone catalog/download/install orchestration
- [`model-management-phone.md`](model-management-phone.md) — phone import, selection and removal controls
- [`harness-model-inventory-state.md`](harness-model-inventory-state.md) — unified model presentation state
- [`generation-configuration-and-prompting-plan.md`](generation-configuration-and-prompting-plan.md) — model-aware generation planning

### Observability, diagnostics and evaluation

- [`console-observability.md`](console-observability.md) — standalone console observability
- [`health-engine.md`](health-engine.md) — health and sanity behavior
- [`resource-observability.md`](resource-observability.md) — resource capture and load classification
- [`benchmark-engine.md`](benchmark-engine.md) — telemetry-derived benchmark history and regression policy
- [`model-evaluation/README.md`](model-evaluation/README.md) — dataset-based model-quality execution, performance comparison and implementation plan
- [`harness-telemetry-composition.md`](harness-telemetry-composition.md) — connected telemetry composition
- [`harness-logs-composition.md`](harness-logs-composition.md) — connected log composition
- [`harness-health-composition.md`](harness-health-composition.md) — connected health composition
- [`harness-resource-composition.md`](harness-resource-composition.md) — connected resource composition
- [`harness-benchmark-composition.md`](harness-benchmark-composition.md) — connected benchmark composition

### Applications, UX and brand

- [`features/phone-app-architecture.md`](features/phone-app-architecture.md) — connected app state, effect and navigation boundary
- [`harness-ux-ui-implementation-plan.md`](harness-ux-ui-implementation-plan.md) — connected UX/UI acceptance criteria
- [`harness-ux-ui-implementation-progress.md`](harness-ux-ui-implementation-progress.md) — focused phone UX workstream state
- [`phone-inference-playground.md`](phone-inference-playground.md) — connected inference workflow
- [`console-inference-playground.md`](console-inference-playground.md) — standalone console inference workflow
- [`design-system.md`](design-system.md) — shared Compose tokens and components
- [`harness-brand-guidelines.md`](harness-brand-guidelines.md) — concise brand and product-language contract
- [`android-brand-assets.md`](android-brand-assets.md) — generated Android identity
- [`assets/brand/README.md`](assets/brand/README.md) and [`assets/brand/master/README.md`](assets/brand/master/README.md) — asset routing and vector masters
- [`shared-runtime/consumer-api/assets/README.md`](shared-runtime/consumer-api/assets/README.md) — OMBRA brand-kit and consumer-flow visual references

### Build, distribution and evidence

- [`android-build-and-run.md`](android-build-and-run.md) — Android build and launch runbook
- [`android-upload-key.md`](android-upload-key.md) — external upload-key custody
- [`device-e2e-testing.md`](device-e2e-testing.md) — ADB/instrumentation device execution
- [`device-e2e-evidence.md`](device-e2e-evidence.md) — physical-device evidence bundle
- [`play-internal-phone-test.md`](play-internal-phone-test.md) — Google Play Internal Testing
- [`emulator-e2e-results.md`](emulator-e2e-results.md) — explicitly labelled emulator evidence

## Document lifecycle

- `current-state`: one short repository operational ledger.
- `workstream-state`: one bounded temporary ledger that links to the repository state or durable owner.
- `roadmap`: capability milestones without branch or commit history.
- `target-specification`: intended behavior and acceptance criteria.
- `feature-index`: a small routing document for a cross-module lifecycle.
- `feature-specification`: durable behavior for one owner.
- `architecture` and `adr-index`: current boundaries and durable decisions.
- `api-reference`: public assembly and lifecycle usage.
- `runbook` and `evidence-runbook`: executable operational procedures.
- `evidence`: immutable, explicitly scoped results.
- `release-policy`, `release-checklist` and `completion-policy`: delivery constraints and gates.
- `design-guideline`, `asset-specification` and `asset-index`: brand intent and asset ownership.

New temporary repository-level workstreams live under [`workstreams/`](workstreams/README.md). Existing legacy plan/progress sources remain valid until intentionally consolidated; touching them should reduce fragmentation rather than create another paired status file.

Completed workstreams are **deleted by default** after durable knowledge is transferred. `archive/` is exception-only for material with independent audit, regulatory, release-evidence or historical value; Git history owns normal implementation history.

## Before creating a document

1. Search `Canonical scope` and this index for the owning source.
2. Update the existing owner when the fact fits its scope.
3. Create a durable document only for an independently readable concern; create a temporary workstream only when cross-PR/agent state genuinely needs its own bounded ledger.
4. Give every active document one supported type, owner, unique canonical scope and precise `Read when` condition.
5. Link it from this index or the closest domain index in the same change.
6. State which source it replaces, or why no source already owns the concern.
7. Finalize a completed workstream by transferring durable knowledge and deleting it; archive only under the exception rule above.

Do not create a document solely to report that a branch, pull request or isolated implementation step completed.

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

ADRs retain their accepted ADR status and date format. Archived documents use `Status: historical`. A compatibility redirect outside `archive/` stays below the configured redirect budget and links to its replacement or archive record.

## Reading and writing budgets

- CI enforces line and estimated-token limits by document type from [`.engineering/documentation-policy.json`](../.engineering/documentation-policy.json).
- New documents must fit their type budget.
- An explicitly baselined oversized document may only shrink; growth fails validation.
- Root and scoped `AGENTS.md` files have separate budgets.
- Active documents must be reachable from this map or an agent guide.
- Duplicate canonical scopes and long exact duplicate paragraphs fail validation.
- Near-duplicate paragraphs are reported for human review.

Summaries may restate a high-level conclusion when they clearly link to the canonical owner. They must not copy detailed checklists, status tables or acceptance criteria.

## Precedence

When sources disagree: executable contracts and tests, accepted ADRs, architecture, focused feature specifications, target overview, current state, roadmap, README/agent guides, then independently retained historical material.

Do not silently reconcile a contradiction that changes behavior. Correct the owning source or surface it in review.

## Validation

```bash
python3 scripts/verify-docs.py --base <target-branch-commit>
python3 scripts/verify-agent-navigation.py
python3 -m py_compile scripts/*.py
git diff --check
```

The cost report shows active token delta, largest changed source, canonical scopes and mandatory agent-guide bundles. The CI workflow publishes it for every documentation change.

# Android Local LLM Harness — Coding Agent Guide

This file is the repository-wide navigation layer for coding agents. It owns durable invariants, routing and validation selection. It is not a project-status ledger.

## Read only what the task requires

Always read this guide, then the closest scoped guide when the target is below one of these paths:

| Scoped guide | Scope |
| --- | --- |
| [`models/AGENTS.md`](models/AGENTS.md) | Profiles, installed storage, catalog, download and installation |
| [`backends/llama-cpp/AGENTS.md`](backends/llama-cpp/AGENTS.md) | Kotlin/JNI/C++ backend, GGUF inspection, CMake and upstream pin |
| [`observability/AGENTS.md`](observability/AGENTS.md) | Telemetry, persistence, health, resources and benchmarks |
| [`apps/local-llm-phone-test/AGENTS.md`](apps/local-llm-phone-test/AGENTS.md) | Connected Compose app, model management and device evidence |

Use additional sources only when they answer a question required by the task:

| Need | Source |
| --- | --- |
| Repository purpose, toolchain and module overview | [`README.md`](README.md) |
| Current integrated state, blockers or next block | [`docs/current-state.md`](docs/current-state.md) |
| Capability roadmap | [`docs/roadmap.md`](docs/roadmap.md) |
| Target behavior and acceptance criteria | [`docs/implementation-plan.md`](docs/implementation-plan.md) or the focused feature specification |
| Architecture and dependency ownership | [`docs/architecture.md`](docs/architecture.md) and accepted [`docs/adr/README.md`](docs/adr/README.md) records |
| Branch, promotion or hotfix procedure | [`BRANCHING.md`](BRANCHING.md) |
| Merge or release readiness | [`docs/definition-of-done.md`](docs/definition-of-done.md) |
| Harness 0.5 release gates | [`docs/releases/harness-0.5.md`](docs/releases/harness-0.5.md) |
| Documentation ownership and lifecycle | [`docs/README.md`](docs/README.md) |
| Public embedded API | [`docs/api-usage.md`](docs/api-usage.md) |
| Cross-application Binder runtime target and workstreams | [`docs/shared-runtime/README.md`](docs/shared-runtime/README.md) |
| Physical-device execution | [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md) and [`docs/play-internal-phone-test.md`](docs/play-internal-phone-test.md) |

Do not load every plan for a local change. README, BRANCHING, current state and Definition of Done are conditional references, not mandatory context for every edit.

## Non-negotiable invariants

- Keep public contracts independent from Android UI, Capacitor and `llama.cpp` types.
- Never expose native pointers, backend structures or backend-owned handles outside the backend module.
- Keep runtime orchestration independent from transport and persistence implementations.
- Resolve models explicitly through `applicationId + useCaseId`; never silently select or substitute a model.
- Support only Qwen3.5 dense 0.8B and 2B at the product boundary under [`ADR 0011`](docs/adr/0011-qwen35-only-product-support.md) while keeping public lifecycle and backend contracts model-family neutral; reject unsupported artifacts explicitly and never delete legacy installed bytes as part of admission.
- Keep catalog selection, verified transfer, installation, binding, selection and runtime loading as separate explicit operations.
- Never expose the verified-download backing path.
- Store GGUF artifacts by immutable SHA-256 identity and never commit or bundle model binaries.
- Keep prompts and generated content out of normal telemetry, persistence and shared validation reports.
- Treat cancellation, shutdown, partial failure and cleanup as normal lifecycle paths.
- Keep one loaded model and one active decode by default until representative measurements justify another policy.
- Prefer composition and dependency injection over global mutable state.
- Keep native, phone-test and future Capacitor adapters thin; domain policy belongs in the owning shared module.
- Never present emulator evidence as physical-device or production evidence.

## Find the owning boundary

Start from the domain owner, then inspect direct consumers and tests before editing.

| Change | Start here | Inspect next |
| --- | --- | --- |
| Public request, session, generation, metric or error contract | `core/contracts` | runtime, transports, observability and app consumers |
| Runtime lifecycle, queueing, memory pressure or residency | `core/runtime-core` | backend, model store, transport and runtime-owning apps |
| Model profile, use case or application binding | `models/model-profile` | runtime resolution, installer and app registries |
| Installed GGUF import, verification or removal | `models/model-store` | runtime ownership, installer and model UI |
| Catalog, targeting or compatibility | `models/model-catalog` | downloader, installer and phone UI |
| Remote transfer and verified holding | `models/model-download` | catalog input, installer and app orchestration |
| Verified installation and inspection | `models/model-install` | model store, backend inspector and phone UI |
| JNI, native generation or GGUF inspection | `backends/llama-cpp` | runtime adapter, installer and device validation |
| Telemetry or query contracts | `observability/contracts` | all stores, engines and presenters |
| Persistence or migration | matching observability store | contracts, engines, migration tests and UI queries |
| Health, resources or benchmarks | matching observability engine | contracts, stores and Diagnostics UI |
| Embedded transport | `transports/in-process` | public contracts and runtime orchestrator |
| Shared Binder/AIDL transport | `docs/shared-runtime/README.md` until its modules exist | core contracts, runtime, host/client apps and security boundary |
| Shared Compose components | `ui/design-system` | both Android apps and accessibility tests |
| Connected phone behavior | `apps/local-llm-phone-test` | owning domain contracts; keep policy out of the app |
| Standalone console | `apps/local-llm-console` | observability and model-store contracts |
| Packaged shared-runtime client validation | `apps/shared-runtime-client-consumer-fixture` | Binder client/contract AARs and packaging validation |
| Device lifecycle validation | `apps/device-test-runner` and `scripts` | production store/runtime/backend and evidence docs |
| CI, packaging or repository navigation | `.github/workflows`, Gradle or `scripts` | affected docs and validation guards |

Use `settings.gradle.kts` as the authoritative module list. Prefer scoped searches such as `rg '<symbol>' <module>` and exclude build outputs and `third_party/llama.cpp` unless the task explicitly concerns upstream integration.

## Change workflow

1. Confirm the owning contract and smallest coherent scope.
2. Read the closest scoped guide and only the focused feature or architecture sources required by the change.
3. Inspect implementations, direct consumers, fakes and tests before changing a public boundary.
4. Implement one vertical slice without speculative modules or parallel domain logic.
5. Add deterministic success, invalid-input, failure, cancellation and cleanup coverage where applicable.
6. Run targeted formatting, compilation and tests while iterating.
7. Expand to repository-wide validation only for shared contracts, multiple domains, Gradle, CI or packaging changes.
8. Update the canonical document whose durable behavior or status changed; do not repeat the same update across multiple ledgers.
9. Record physical-device evidence as pending when it was not executed and do not claim production readiness.
10. Inspect the complete diff before push.

For documentation changes, search `Canonical scope` and [`docs/README.md`](docs/README.md) before creating a file. Prefer updating the existing owner. Every new active source needs supported metadata, a unique scope, a bounded `Read when` trigger and an index link. Completed plans and temporary ledgers move to the archive after durable behavior is transferred.

Ordinary work starts from the latest green `dev` and opens a focused pull request back to `dev`. `main` is used only for validated promotions or explicit emergency hotfixes as defined in `BRANCHING.md`.

## Validation levels

Choose the narrowest gate that covers the changed module and its direct consumers.

### Documentation and navigation

```bash
python3 scripts/verify-docs.py --base <target-branch-commit>
python3 scripts/verify-agent-navigation.py
python3 scripts/test_verify_docs.py
python3 -m py_compile scripts/*.py
git diff --check
```

### Targeted Android iteration

Use the closest scoped guide. A typical module-local loop is:

```bash
./gradlew :<module>:spotlessCheck :<module>:testDebugUnitTest :<module>:compileDebugKotlin
```

Add Android Lint, assembly, instrumentation compilation or downstream modules when the changed boundary requires them.

### Repository-wide Android gate

Use for public contracts, multiple domains, build configuration or validation infrastructure:

```bash
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew check
./gradlew lintDebug :apps:local-llm-console:lintInternal
./gradlew assembleDebug :apps:local-llm-console:assembleInternal
LOCAL_LLM_PHONE_TEST_ALLOW_UNSIGNED_RELEASE=true \
  ./gradlew :apps:local-llm-phone-test:assembleDebug :apps:local-llm-phone-test:bundleRelease
./gradlew :apps:device-test-runner:assembleDebugAndroidTest
./gradlew :observability:room-store:assembleDebugAndroidTest
python3 scripts/verify-android-packaging.py
```

### Native host tests

```bash
cmake -S backends/llama-cpp/src/test-native -B build/native-tests -DCMAKE_BUILD_TYPE=Release
cmake --build build/native-tests --parallel 2
ctest --test-dir build/native-tests --output-on-failure
```

### Physical-device evidence

```bash
bash scripts/capture-device-e2e-evidence.sh \
  --model /absolute/path/to/model.gguf \
  --architecture <architecture> \
  --quantization <quantization> \
  --memory-repeat 5
```

Physical-device evidence is required before production-readiness, device-performance or downstream application-release claims.

## Maintaining agent guides

Agent guides contain only durable routing, local hazards, reading order and validation commands.

Documentation reading budgets, lifecycle metadata, canonical-scope uniqueness, reachability and duplicate-content rules are machine-enforced from [`docs/documentation-policy.json`](docs/documentation-policy.json). Do not raise a budget to avoid splitting or consolidating a document; an exception must preserve or reduce the prior reading cost through the policy ratchet.

Do not put these items in an agent guide:

- current branch or pull-request state;
- completed-work histories;
- release checklists;
- temporary workarounds;
- duplicate architecture or feature specifications.

Update the root guide only for repository-wide routing or invariants. Update the closest scoped guide for subtree ownership, hazards or commands. When a canonical document moves, update links and run both documentation guards in the same change.

Pause and surface the conflict rather than improvising when a change would violate a public contract or accepted ADR, expose private/native state, commit model or signing material, duplicate an active implementation path, or claim production readiness without physical-device evidence.

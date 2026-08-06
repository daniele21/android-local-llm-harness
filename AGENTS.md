# Android Local LLM Harness — Coding Agent Guide

This is the repository-wide guide for coding agents. It owns durable global rules and routes work to the correct source, module and scoped guide. Current implementation status belongs in [`docs/current-state.md`](docs/current-state.md) and [`docs/roadmap.md`](docs/roadmap.md); target behavior belongs in [`docs/implementation-plan.md`](docs/implementation-plan.md).

## Guide hierarchy

Read this file first. When work falls below a directory with its own `AGENTS.md`, read that guide next; both apply. A scoped guide adds local ownership, navigation and validation details, but cannot weaken repository-wide invariants.

| Scoped guide | Use it for | Update it when |
| --- | --- | --- |
| [`models/AGENTS.md`](models/AGENTS.md) | Profiles, installed storage, catalog, download and installation | The model lifecycle, responsibility split, contracts or module-specific checks change |
| [`backends/llama-cpp/AGENTS.md`](backends/llama-cpp/AGENTS.md) | Kotlin/JNI/C++ backend and GGUF inspection adapter | Native ownership, JNI entry points, CMake, the upstream pin or native tests change |
| [`observability/AGENTS.md`](observability/AGENTS.md) | Telemetry, persistence, health, resources and benchmarks | Schemas, retention, privacy, migrations or observability dependencies change |
| [`apps/local-llm-phone-test/AGENTS.md`](apps/local-llm-phone-test/AGENTS.md) | Connected Compose app and Play/device validation | App composition, navigation, model-management UI, evidence or app-specific checks change |

The guide under `third_party/llama.cpp` belongs to the pinned upstream project. Do not edit it as repository navigation. Work that changes the pin or integration must also follow the backend guide above.

If no scoped guide covers the target path, this root guide is sufficient. Do not create a nested guide merely to repeat global rules; add one only when a subtree has distinct ownership, hazards, reading order or validation.

## Start here

Before a non-trivial change:

1. Read [`README.md`](README.md) for purpose, toolchain and the top-level map.
2. Read [`BRANCHING.md`](BRANCHING.md) for the canonical branch and pull-request discipline.
3. Read [`docs/current-state.md`](docs/current-state.md) for the active integration and recovery order.
4. Read the closest scoped guide from the table above, when one applies.
5. Use the task-routing table below to select the remaining sources and code. Do not load every planning document when the change does not touch it.

Canonical sources by question:

| Question | Source of truth |
| --- | --- |
| What is implemented or still open? | [`docs/current-state.md`](docs/current-state.md), then [`docs/roadmap.md`](docs/roadmap.md) for detailed history |
| What is the active Harness 0.5.0 sequence? | [`docs/dev-integration-and-harness-0.5-plan.md`](docs/dev-integration-and-harness-0.5-plan.md) |
| What behavior and acceptance criteria are intended? | [`docs/implementation-plan.md`](docs/implementation-plan.md) |
| What are the dependency and ownership boundaries? | [`docs/architecture.md`](docs/architecture.md) and accepted [`docs/adr/README.md`](docs/adr/README.md) records |
| What is required before merge or release? | [`docs/definition-of-done.md`](docs/definition-of-done.md) |
| How is the embedded API assembled and closed? | [`docs/api-usage.md`](docs/api-usage.md) |
| How is Android device validation executed and recorded? | [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md), [`docs/device-e2e-evidence.md`](docs/device-e2e-evidence.md) and [`docs/play-internal-phone-test.md`](docs/play-internal-phone-test.md) |

When sources disagree, use this precedence: executable contracts and tests, accepted ADRs, architecture, implementation plan, current-state ledger, roadmap, README and agent guides. Do not silently reconcile contradictions; surface them before changing behavior.

## Non-negotiable architecture invariants

- Keep public contracts independent from Android UI, Capacitor and `llama.cpp` types.
- Never expose native pointers, backend structures or backend-owned handles outside the backend module.
- Keep runtime orchestration independent from transport and persistence implementations.
- Resolve models explicitly through `applicationId + useCaseId`; never silently select or substitute a model.
- Keep remote catalog selection, verified transfer, installation, binding and runtime loading as separate explicit operations.
- Never expose a verified-download backing path; installation consumes only an opaque handle plus expected immutable identity.
- Keep one loaded model and one active decode by default until measurements justify another policy.
- Keep prompts and generated content out of normal telemetry and shared validation reports.
- Store GGUF artifacts by immutable SHA-256 identity and never commit or bundle model binaries.
- Treat cancellation, shutdown and partial failure as normal lifecycle paths.
- Prefer composition and dependency injection over global mutable state.
- Native, phone-test and Capacitor integrations must remain thin and must not duplicate runtime policy.
- Never present emulator evidence as physical-device or production evidence.

## Find the right change location

Start at the owning boundary, then inspect every direct consumer before editing:

| Change type | Start here | Inspect next |
| --- | --- | --- |
| Public requests, responses, sessions, metrics or errors | `core/contracts` | `core/runtime-core`, transports, observability contracts and app consumers |
| Runtime lifecycle, scheduling, memory pressure or integrity cache | `core/runtime-core` | Backend, model store, transport and runtime-owning apps |
| Model/profile/use-case/application binding | `models/model-profile` | Runtime resolution, installation and app registries |
| Installed GGUF identity, import, verification or removal | `models/model-store` | Runtime ownership, installation and model-management UI |
| Catalog releases, targeting or compatibility | `models/model-catalog` | Download authorization, installation reconciliation and phone UI |
| Remote URI policy, transfer, retry or verified holding | `models/model-download` | Catalog inputs, installer handle consumption and app orchestration |
| Verified download installation or GGUF inspection contract | `models/model-install` | Model store, catalog/download contracts, backend inspector and phone UI |
| JNI, generation, native handles or GGUF backend inspection | `backends/llama-cpp` | Runtime adapter, installer and device validation |
| Telemetry schema or query contract | `observability/contracts` | Every implementation, health/benchmark engine and app presenter |
| In-memory or Room persistence and retention | Matching observability store | Contract tests, migrations, engines and UI queries |
| Health, resource or benchmark behavior | Matching observability engine | Contracts, stores and connected Diagnostics UI |
| Embedded client delegation | `transports/in-process` | Public contracts and runtime orchestrator |
| Shared Compose tokens or components | `ui/design-system` | Both Android apps and accessibility tests |
| Connected phone UX, distribution or physical validation | `apps/local-llm-phone-test` | Owning domain contracts; keep policy out of the app |
| Standalone console presentation | `apps/local-llm-console` | Observability/model-store contracts; never open another app's database |
| ADB/instrumentation lifecycle validation | `apps/device-test-runner` and `scripts` | Production store/runtime/backend and device evidence docs |
| Module, CI, packaging or repository navigation | `settings.gradle.kts`, `.github/workflows` or `scripts` | README, this guide and affected validation docs |

Use `settings.gradle.kts` as the authoritative module list. Search for interfaces, implementations, tests and Gradle dependencies before assuming ownership from a filename alone. Prefer `rg '<symbol>'` and `rg --files <scope>` for code navigation.

## Repository map

| Path | Responsibility |
| --- | --- |
| `core/contracts` | Stable request, response, session, metric and error contracts |
| `core/runtime-core` | Runtime orchestration, scheduling, lifecycle and memory policy |
| `models/model-profile` | GGUF artifacts, load profiles, use cases and app bindings |
| `models/model-store` | Content-addressed installed-model import, storage and integrity verification |
| `models/model-catalog` | Admin-managed model release contracts, validation, target filtering and compatibility policy |
| `models/model-download` | Secure remote transfer and opaque access to the verified app-private holding area |
| `models/model-install` | Catalog/profile reconciliation, GGUF inspection and explicit ModelStore publication |
| `backends/llama-cpp` | Kotlin/JNI/C++ backend, native resource ownership and GGUF inspector adapter |
| `observability/contracts` | Stable telemetry, health, resource and benchmark schemas |
| `observability/in-memory-store` | Bounded ephemeral telemetry implementation |
| `observability/room-store` | Persistent Android telemetry repository |
| `observability/health-engine` | Health-suite orchestration and persisted checks |
| `observability/android-resource-probe` | Android memory and thermal snapshot collection |
| `observability/benchmark-engine` | Cold/warm baselines and regression checks |
| `transports/in-process` | Embedded client-to-runtime delegation |
| `ui/design-system` | Shared Compose theme, visual tokens and reusable Harness components |
| `apps/local-llm-console` | Developer console and future cross-app control plane |
| `apps/device-test-runner` | ADB/instrumentation GGUF lifecycle and memory validation |
| `apps/local-llm-phone-test` | Connected Compose console and Play-installable physical-device validation |
| `third_party/llama.cpp` | Pinned upstream submodule |
| `scripts` | Repository, packaging, device and evidence validation |
| `docs` | Architecture, plans, operations and evidence |

Run `python3 scripts/verify-agent-navigation.py` after adding, removing or renaming a module or agent guide.

## Change workflow

1. Confirm the latest green `dev`, the intended target and active pull requests; use `main` only for an explicit hotfix.
2. Read the owning contracts, their implementations, direct consumers, tests and the closest guide.
3. Implement the smallest coherent vertical slice in the owning module.
4. Add deterministic tests for success, invalid input, failure, cancellation and cleanup where applicable.
5. Run targeted checks while iterating and inspect the complete diff before expanding validation.
6. Update the source-of-truth documents and agent guides whose durable navigation changed. Do not record transient completion state in an agent guide.
7. Before every push, run formatting, static analysis, compilation and targeted tests covering all changed modules and direct consumers.
8. Run the aggregate repository validation before merge when shared contracts or multiple domains change.
9. Record deferred physical-device evidence explicitly without claiming production readiness.
10. Keep model files, signing keys, credentials and private paths outside the repository.

Ordinary work starts from the latest green `dev` and opens a pull request back to `dev`. A red cumulative `dev` freezes new integrations until a focused fix-forward or revert restores it. Feature pull requests normally squash into `dev`; promotions preserve the validated `dev` identity with a merge commit. Never reuse a merged or superseded branch.

## Validation commands

Choose the narrowest commands that cover the changed scope and its direct consumers. The closest scoped guide lists the domain-specific gate.

Before pushing, inspect the complete staged diff and resolve every locally reproducible formatting, static-analysis, compilation or test failure. GitHub Actions is the final clean-checkout confirmation, not the first debugger. If a required check cannot run locally, document the limitation before pushing, run the strongest available equivalent and do not describe the change as validated.

### Navigation and scripts

```bash
python3 scripts/verify-agent-navigation.py
find scripts -type f -name '*.sh' -exec bash -n {} \;
python3 -m py_compile scripts/*.py
python3 scripts/test_detect_ci_scope.py
```

### Repository-wide Android gate

Use for shared contracts, multiple domains or validation/build configuration changes:

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

Use the phone-test app for Play-installed validation when developer mode or ADB is unavailable. When ADB is available:

```bash
bash scripts/capture-device-e2e-evidence.sh \
  --model /absolute/path/to/model.gguf \
  --architecture <architecture> \
  --quantization <quantization> \
  --memory-repeat 5
```

Physical-device evidence is mandatory before production readiness, application-consumer release or device-performance claims.

## Testing expectations

- Keep domain logic behind interfaces and use fakes for deterministic orchestration tests.
- Test invalid input, cleanup after failure or cancellation, and recovery after partial work—not only successful results.
- Test idempotent close and release behavior for owned resources.
- Use an injected deterministic clock instead of timing assertions where possible.
- Keep prompts, generated output, signed URLs, document URIs and private paths out of persisted fixtures and shared reports.
- Verify Android/JNI/ABI behavior on representative `arm64-v8a` hardware with a real supported GGUF before production-readiness claims.

## Maintaining `AGENTS.md`

Agent guides are a navigation layer, not an additional project ledger. Keep them short, durable and verifiable.

Use the exact uppercase filename `AGENTS.md` for every guide.

### Put information in the right place

| Information | Update |
| --- | --- |
| Repository-wide invariant, workflow or task routing | This root guide |
| Subtree ownership, local hazards, reading order or commands | The closest scoped guide |
| Implemented, pending or blocked work | `docs/current-state.md` and `docs/roadmap.md` |
| Target behavior or acceptance criteria | `docs/implementation-plan.md` or the focused plan |
| Durable architectural decision | `docs/architecture.md` and, when constraining future work, an ADR |
| Public API behavior | Public contracts, tests and `docs/api-usage.md` |
| Operational or evidence procedure | The corresponding runbook under `docs` |

### Update triggers

- When a module is added, removed or renamed, update `settings.gradle.kts`, the repository map, task routing, the applicable scoped guide and navigation validation in the same change.
- When responsibility crosses a module boundary, update architecture documentation first, then root routing and both affected scoped guides. Add or supersede an ADR when the decision constrains future implementations.
- When a public contract changes, update its consumers, usage documentation and the relevant scoped guide if navigation or validation changes.
- When tests, Gradle tasks, scripts or CI gates change, update the narrowest guide that lists the command; update the root only for repository-wide gates.
- When a canonical document moves, update every guide link in the same change.
- When adding a scoped guide, give it a unique scope, link it from the hierarchy table, link back to this root, add its own maintenance triggers and run the navigation guard.
- Before deleting a scoped guide, move any still-valid unique instructions to the root or a surviving guide and remove its root index entry.

### Review checklist

1. Compare the guide against `settings.gradle.kts`, module dependencies, executable contracts, tests, workflows and scripts.
2. Remove duplicated global rules and volatile status; link to the canonical source instead.
3. Verify that every command names existing modules/tasks and that every relative link resolves from the guide's directory.
4. Confirm that scoped instructions do not relax global architecture, privacy, security or evidence rules.
5. Run `python3 scripts/verify-agent-navigation.py` and inspect the complete documentation diff.

Pause and surface the issue rather than improvising when a change conflicts with public contracts or an ADR, would commit a model, signing key or native dependency, exposes backend or verified-download state, duplicates an active implementation line, or would claim production readiness without physical-device evidence.

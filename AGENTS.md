# Android Local LLM Harness — Coding Agent Guide

This file is the stable entry point for coding agents. Current implementation status belongs in [`docs/current-state.md`](docs/current-state.md) and [`docs/roadmap.md`](docs/roadmap.md); target behavior belongs in [`docs/implementation-plan.md`](docs/implementation-plan.md).

## Start here

Read these sources before a non-trivial change:

1. [`README.md`](README.md) — purpose, toolchain and top-level structure.
2. [`BRANCHING.md`](BRANCHING.md) — canonical branch and pull-request discipline.
3. [`docs/dev-integration-and-harness-0.5-plan.md`](docs/dev-integration-and-harness-0.5-plan.md) — active Harness 0.5.0 integration sequence.
4. [`docs/architecture.md`](docs/architecture.md) — data-plane and control-plane boundaries.
5. [`docs/current-state.md`](docs/current-state.md) — active integration and recovery order.
6. [`docs/roadmap.md`](docs/roadmap.md) — detailed implementation status and remaining evidence.
7. [`docs/implementation-plan.md`](docs/implementation-plan.md) — target behavior and acceptance criteria.
8. [`docs/definition-of-done.md`](docs/definition-of-done.md) — merge and production readiness.
9. [`docs/api-usage.md`](docs/api-usage.md) — embedded API and lifecycle.
10. [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md), [`docs/device-e2e-evidence.md`](docs/device-e2e-evidence.md) and [`docs/play-internal-phone-test.md`](docs/play-internal-phone-test.md) — Android validation paths.
11. [`docs/adr/README.md`](docs/adr/README.md) — accepted architectural decisions.

When sources disagree, use this precedence: executable contracts and tests, accepted ADRs, architecture, implementation plan, current-state ledger, roadmap, README and this guide. Do not silently reconcile contradictions.

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

`settings.gradle.kts` is authoritative for the Gradle module list. Run `python3 scripts/verify-agent-navigation.py` after adding, removing or renaming a module.

### Ownership and routing

- Public API changes start in `core/contracts`; inspect all runtime, transport and observability consumers.
- Lifecycle, scheduling and memory changes start in `core/runtime-core`; preserve serialized state mutation and cleanup after failure.
- Installed GGUF storage or integrity changes start in `models/model-store` and `models/model-profile`; preserve streaming I/O, atomic staging and SHA-256 identity.
- Catalog release, target-filtering or device-compatibility changes start in `models/model-catalog`; keep remote distribution policy outside the runtime and final artifact store.
- Remote URI policy, transfer, retry, restart cleanup or verified-holding changes start in `models/model-download`; do not import into `ModelStore` or expose source paths there.
- Verified-download installation changes start in `models/model-install`; preserve exact catalog/profile matching, metadata inspection before import, post-import verification and absence of binding/runtime side effects.
- JNI or generation changes start in `backends/llama-cpp`; preserve opaque handles, idempotent release and cooperative cancellation. Backend-specific GGUF inspection is adapted into the neutral installation contract here.
- The phone-test app may orchestrate existing contracts, import through Android's Storage Access Framework and format privacy-safe evidence, but must not own alternate inference or model-installation policy.
- Console code must not open another application's private database directly; cross-app access requires the planned signature-protected diagnostics bridge.

## Branch discipline

- Ordinary work starts from the latest green `dev` and opens a pull request back to `dev`.
- Do not open a feature, dependency or documentation pull request directly to `main`; the validation gate rejects it.
- `main` is reserved for a complete `dev -> main` promotion or an explicit emergency hotfix.
- A red cumulative `dev` freezes new integrations until a focused fix-forward or revert restores the branch.
- Feature pull requests normally squash into `dev`; promotions preserve the validated `dev` identity with a merge commit.
- Never reuse a merged or superseded branch for new implementation work.

## Change workflow

1. Confirm the latest green `dev`, the intended target and active pull requests; use `main` only for an explicit hotfix.
2. Read relevant contracts, implementation, tests and documentation.
3. Implement the smallest coherent vertical slice in the owning module.
4. Add deterministic tests for success, failure and lifecycle paths.
5. Run targeted local checks while iterating.
6. Before every push, review the complete diff and run the formatter, static analysis, compilation and targeted tests that cover the changed modules.
7. Push only after those local checks pass. GitHub Actions is the final clean-checkout confirmation, not the first debugger for locally reproducible failures.
8. Run the aggregate repository validation before merge.
9. Record deferred physical-device evidence explicitly without claiming production readiness.
10. Keep model files, signing keys and credentials outside the repository.

### Mandatory pre-push gate

Do not push an implementation commit merely to discover formatter, Detekt, compilation or unit-test errors in `validate.yml`.

Before pushing, the agent or contributor must:

1. inspect the complete staged diff and verify that the implementation matches the real contracts and existing APIs;
2. run `spotlessCheck` or `spotlessApply` followed by `spotlessCheck` for the affected Kotlin/Markdown sources;
3. run Detekt for the affected scope;
4. compile the affected production and test sources;
5. run the targeted unit tests for the changed behavior;
6. run any relevant repository, packaging or script guard when the change touches modules, ABI configuration, native code, workflows or documentation navigation;
7. resolve all locally reproducible failures before pushing.

The exact command may be narrower than the full repository gate while iterating, but it must cover every changed module and its direct consumers. Examples:

```bash
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt
./gradlew :models:model-download:testDebugUnitTest \
  :models:model-install:testDebugUnitTest \
  :backends:llama-cpp:testDebugUnitTest
```

For changes spanning multiple modules or touching shared contracts, run `./gradlew check` before pushing unless there is a documented environment limitation. When the local environment cannot execute a required check, state that limitation before the push, perform the strongest available equivalent validation, and do not describe the change as validated.

A CI failure may still reveal clean-checkout, runner, packaging or integration-only issues. It must not be used as a substitute for code review and checks that can be run locally.

## Validation commands

### Navigation and scripts

```bash
python3 scripts/verify-agent-navigation.py
find scripts -type f -name '*.sh' -exec bash -n {} \;
python3 -m py_compile scripts/*.py
bash scripts/run-device-e2e.sh --help
bash scripts/capture-device-e2e-evidence.sh --help
```

### Kotlin and Android

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

Use `apps/local-llm-phone-test` for Play-installed validation when developer mode or ADB is unavailable. Use the ADB evidence script when device access permits it:

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
- Test cleanup after generation, failure, cancellation and partial model installation.
- Test idempotent close and release behavior.
- Avoid timing assertions without a deterministic clock.
- Keep prompts, generated output, signed URLs, document URIs and private paths out of persisted or shared reports.
- Verify the Play-installed app on representative `arm64-v8a` hardware with a real supported GGUF.

## Maintaining `AGENTS.md`

Use the exact uppercase filename. Keep this guide navigational and durable:

- link to canonical documents instead of duplicating changing status;
- update the repository map whenever `settings.gradle.kts` changes;
- update validation commands when CI changes;
- document new ownership boundaries and link accepted ADRs;
- keep active integration state in `docs/current-state.md` and detailed completion status in `docs/roadmap.md`;
- run the navigation guard after every edit.

Pause and surface the issue rather than improvising when a change conflicts with public contracts or an ADR, would commit a model, signing key or native dependency, exposes backend or verified-download state, duplicates an active implementation line, or would claim production readiness without physical-device evidence.

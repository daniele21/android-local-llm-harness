# E2E Environment Fidelity Contract

Version: 0.1.0

This contract defines how Android Local LLM Harness chooses end-to-end execution environments so automated evidence becomes progressively representative of the real target while physical-device validation remains responsible only for irreducible native/hardware differences.

The governing rule is:

> Final target-environment validation should confirm residual environment-specific claims, not become the first time the complete workflow is exercised.

This contract complements the existing operating and execution-capability contracts:

- `.engineering/commands.json` owns project command semantics;
- `EXECUTION-CAPABILITY-CONTRACT.md` owns `AGENT_LOCAL`, `REMOTE_AUTOMATED` and `REAL_ENVIRONMENT` executor classification;
- `.engineering/e2e.json` owns Harness target environments, execution environments, critical journeys, fidelity classes and residual gaps.

## Execution capability is not environment fidelity

Executor location and environment representativeness are independent. An Android emulator running in GitHub Actions is `REMOTE_AUTOMATED` but remains `simulated_or_emulated`; it is not physical ARM64/JNI/model/memory/thermal evidence. A physical-device run is `REAL_ENVIRONMENT` unless a repository-owned device farm automates it.

## Fidelity classes

Harness uses the standard ordering:

1. `host_or_fake`;
2. `simulated_or_emulated`;
3. `representative_virtual`;
4. `representative_physical`;
5. `target_environment`.

Use the cheapest reliable environment that proves the changed claim. Higher fidelity is required only when the claim depends on a dimension missing from the cheaper environment.

## Harness specialization

Material target dimensions include Android framework/process lifecycle, CPU ABI, JNI/native `llama.cpp`, real GGUF storage/model lifecycle, Binder/process behavior, memory pressure/reclamation, and thermal/OEM behavior.

Current automated emulator evidence proves Android/Binder contract behavior within its declared scope. It does not prove production ARM64 inference. Current identity-bearing physical-device workflows remain required for the native/model/resource claims declared in `.engineering/e2e.json`.

When a practical automated environment can reproduce a failure currently discovered only on the physical device, move that evidence earlier instead of normalizing final device validation as the first whole-system test.

## Critical-journey rule

Keep E2E small. Unit/integration/contract tests remain primary for deterministic invariants. Each critical journey in `.engineering/e2e.json` declares:

- the complete user/system outcome being claimed;
- target environment refs;
- automated execution environments when available;
- minimum automated fidelity;
- residual real-environment gaps;
- whether real-environment confirmation is required, conditional or unnecessary;
- an explicit automation-gap reason when no truthful automated environment exists.

## Built artifacts and evidence

When install/package behavior is part of a claim, execute the produced APK/AAB-derived installable surface where technically practical. Running a real APK on an emulator can prove packaging/install/workflow behavior but still cannot establish physical ARM64 resource/thermal claims.

E2E evidence records source/build/run identity, `.engineering/e2e.json` environment ID, fidelity class, artifact surface, known gaps and privacy-safe logs/traces. Do not report a generic `E2E PASS` when stronger environment claims remain pending.

## Completion

The strongest product/release claim requires applicable lower-level evidence, required automated E2E at the declared fidelity, built-artifact coverage when material, explicit residual gaps and required physical/target confirmation. `AUTOMATED_PREFLIGHT_CONFIRMED` means deterministic automated evidence is complete; it does not convert pending physical-device evidence into a pass.

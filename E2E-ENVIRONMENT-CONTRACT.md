# E2E Environment Fidelity Contract

Version: 0.2.1

Harnex separates **executor**, **environment fidelity** and **UI evidence mode**. `.engineering/e2e.json` owns concrete target/execution environments, critical journeys and stage policy.

## Governing rules

> Automated integration proves the coherent feature before `dev`; residual physical/device validation is deferred to release and confirms ARM64/native/model/resource deltas.

> Use the cheapest automated environment sufficient for the changed claim and escalate fidelity only when a material target dimension requires it.

> UI presence alone does not force video, but a material UI/UX critical journey entering shared development requires `FULL_MEDIA`.

## Fidelity and journeys

Canonical order: `host_or_fake` → `simulated_or_emulated` → `representative_virtual` → `representative_physical` → `target_environment`.

A GitHub Android emulator remains `simulated_or_emulated`; it does not establish ARM64 JNI/llama.cpp, real GGUF loading, physical memory, thermal or OEM behavior. Lower-level tests own deterministic invariants; E2E owns assembled outcomes. Each journey declares target/automated environments, minimum fidelity, residual gaps and real-environment confirmation.

## Stage policy

- **ITERATION**: E2E only when it is the cheapest useful falsifier.
- **INTEGRATION**: affected automated critical journeys must pass before `dev`; real-environment evidence is non-blocking and retained as `DEFERRED_TO_RELEASE`.
- **RELEASE**: release-critical E2E plus every required real-environment confirmation must PASS.

UI evidence modes are `ASSERTIONS`, `SCREENSHOTS`, `FULL_MEDIA`. `FULL_MEDIA` means bounded screenshots plus one continuous journey video for material UI integration outcomes or claims about motion, timing/progression, navigation/transitions, lifecycle visibility or release acceptance. Missing required evidence is `E2E_EVIDENCE_INCOMPLETE`, never permission to downgrade.

Harnex mapping remains: Binder/shared-runtime system journeys use assertions; `phone-cold-start` normally uses screenshots unless the UI/UX journey itself is the integration claim; production local-inference lifecycle retains explicit physical ARM64/GGUF/resource release evidence.

Evidence identifies journey, source/build/run, execution environment/fidelity and selected UI mode, remains privacy-safe and bounded, and is cleaned with owned temporary state on every exit path.

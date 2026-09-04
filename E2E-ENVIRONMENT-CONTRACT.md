# E2E Environment Fidelity Contract

Version: 0.2.0

Harnex separates **executor**, **environment fidelity** and **UI evidence mode**. `.engineering/e2e.json` owns the concrete target/execution environments and critical journeys.

## Governing rules

> Final physical/device validation confirms residual ARM64/native/model/resource claims; it should not be the first complete-system test.

> Use the cheapest automated environment sufficient for the changed claim and escalate fidelity only when a material target dimension requires it.

> UI presence alone does not force video.

## Fidelity

Canonical order:

1. `host_or_fake`
2. `simulated_or_emulated`
3. `representative_virtual`
4. `representative_physical`
5. `target_environment`

A GitHub Android emulator remains `simulated_or_emulated`; it does not establish ARM64 JNI/llama.cpp, real GGUF loading, physical memory, thermal or OEM behavior.

## Critical journeys

Keep E2E small. Lower-level tests own deterministic invariants; E2E owns assembled outcomes.

For every journey declare target environments, automated environments, minimum automated fidelity, residual gaps and real-environment confirmation. Execute against built/package surfaces when package/install behavior is part of the claim.

## UI evidence modes

- `ASSERTIONS` — UI is incidental and deterministic system behavior is the changed claim.
- `SCREENSHOTS` — stable visible layout/hierarchy/copy/state/recovery/adaptive semantics must be inspected.
- `FULL_MEDIA` — sequence over time matters: motion, timing/progression, navigation/transitions, lifecycle visibility or release/product acceptance.

The selected mode is a **minimum**. A workflow may retain stronger evidence when useful, but stronger media should not be required solely because a journey traverses UI.

Harnex mapping:

- Binder serialization and shared-runtime roundtrip: assertions;
- `phone-cold-start`: screenshots normally suffice;
- production local-inference lifecycle: lower-level automation plus explicit physical ARM64/GGUF/resource evidence.

If required evidence for the selected mode is missing, report `E2E_EVIDENCE_INCOMPLETE` rather than overclaiming PASS.

## Evidence identity and lifecycle

Evidence must identify journey, source/build/run, execution environment/fidelity and selected UI evidence mode. Logs/screenshots/videos remain privacy-safe bounded artifacts, not durable design truth.

E2E owns cleanup of project processes/listeners, emulator/device run state, temporary model/test data and generated evidence on success/failure/timeout/cancellation.

## Escalation

During ITERATION, do not run E2E by default unless a cheap journey is the fastest useful falsifier. At INTEGRATION run only affected critical journeys. At RELEASE add release-critical and residual environment evidence required by the claim.

# E2E Environment Fidelity Contract

Version: 0.2.1

Harnex separates **executor**, **environment fidelity**, **delivery stage** and **UI evidence mode**. `.engineering/e2e.json` owns the concrete target/execution environments, stage policy and critical journeys.

## Governing rules

> Final physical/device validation confirms residual ARM64/native/model/resource claims; it should not be the first complete-system test.

> Prove the affected feature automatically before `dev`; close required residual target-environment gaps before `main`/release.

> Use the cheapest automated environment sufficient for the changed claim and escalate fidelity only when a material target dimension requires it.

> UI presence alone does not force video, but a material UI/UX integration journey uses `FULL_MEDIA`.

## Stage policy

### INTEGRATION -> `dev`

Affected critical journeys must pass automatically when lower-level evidence cannot prove the complete outcome. Emulator/simulator evidence is valid for the claims it represents. `REAL_ENVIRONMENT` requirements are explicit but non-blocking and become `DEFERRED_TO_RELEASE`.

For a material UI/UX critical journey, the integration minimum is `FULL_MEDIA`: bounded screenshot checkpoints plus one continuous journey video. UI that is merely a harness for a non-visual system invariant may remain `ASSERTIONS`.

### RELEASE -> `main`

Release uses FULL validation plus release-critical artifact/E2E evidence. Every journey with `real_environment_confirmation: required` must close its residual physical/target-environment gap before release readiness.

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
- `FULL_MEDIA` — the integrated product claim materially depends on UI/UX sequence, motion, timing/progression, navigation/transitions, lifecycle visibility or release/product acceptance.

The selected mode is a **minimum**. A workflow may retain stronger evidence when useful.

Harnex mapping:

- Binder serialization and shared-runtime roundtrip: assertions;
- `phone-cold-start`: screenshots normally suffice because the claim is stable startup/rendering, not a material end-user journey sequence;
- production local-inference lifecycle: lower-level automation plus explicit physical ARM64/GGUF/resource evidence at release when required.

If required evidence for the selected mode is missing, report `E2E_EVIDENCE_INCOMPLETE` rather than overclaiming PASS.

## Evidence identity and lifecycle

Evidence must identify journey, source/build/run, execution environment/fidelity and selected UI evidence mode. Logs/screenshots/videos remain privacy-safe bounded artifacts, not durable design truth.

E2E owns cleanup of project processes/listeners, emulator/device run state, temporary model/test data and generated evidence on success/failure/timeout/cancellation.

## Escalation

During ITERATION, do not run E2E by default unless a cheap journey is the fastest useful falsifier. At INTEGRATION run only affected automated critical journeys. At RELEASE add release-critical and every required residual real-environment gate. Physical-device runs remain valid earlier when diagnosing an explicitly hardware-specific defect, but they do not become the normal feature-PR integration blocker.

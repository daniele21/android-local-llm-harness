---
name: validate-change
description: Run the cheapest sufficient Harnex edit/test loop, diagnose failures with discriminating evidence, and expand validation by risk and delivery stage.
---

# Validate Change

Use `.engineering/commands.json` or the native selector summary. Resolve **outcome/owner -> risks -> concrete gates -> profile**. Prefer formatter/static checks, affected compile and focused behavior tests; add material consumers for shared boundaries. Contained work is usually `SCOPED`; Binder/public contract, persistence/security, lifecycle, native/JNI/package/manifest/R8 is usually `STRONG`; selector/global build/toolchain, unknown executable scope and release are `FULL`.

During **ITERATION**, exact-head publication, full diff, durable docs, broad E2E and remote preflight are not routine. At **INTEGRATION**, hand off to `../preflight-change/SKILL.md`; at **RELEASE**, it additionally requires blocking real-environment confirmation.

## Diagnose before repairing

Classify change regression, baseline, environment/toolchain, flaky, base drift or incorrect assumption. Use the cheapest discriminating experiment and fix the owning invariant. Never suppress/weaken a legitimate test. Each failed repair needs a new falsifiable hypothesis. **After two failed repairs with the same signature, change diagnostic strategy and obtain new evidence before a third repair** (smaller reproducer, targeted instrumentation or revisit owner/assumption).

## Harnex fidelity

Binder/API35 emulator evidence is `simulated_or_emulated`, never ARM64/native/model/thermal proof. `phone-cold-start` normally needs screenshots; a material UI/UX integration outcome requires `FULL_MEDIA`. Production llama.cpp/GGUF/memory/thermal/OEM claims retain explicit physical release evidence where automation cannot substitute truthfully.

Report a bounded stage/source identity, risks/profile, required gates with reasons and PASS/FAIL/PENDING/N/A, evidence refs, remaining gaps and next action. Keep full logs available by reference; never hide failed/pending required gates.

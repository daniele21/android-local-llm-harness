# SR-6 release governance review

Status: accepted for repository implementation; distribution remains gated by physical evidence
Document type: release-review
Owner: shared-runtime-validation
Canonical scope: shared-runtime.sr6-release-governance
Last reviewed: 2026-08-14

## Decision

The current shared-runtime repository implementation is suitable to proceed to exact-candidate physical release validation. It is **not yet approved for consumer publication** because SR-6 still requires retained physical-device evidence for the exact host/client/runtime/model/signing identity.

This review closes the repository-level public API, security, versioning, packaging and console-governance review. It does not substitute for device evidence and does not weaken any gate in `workstreams/validation-rollout.md`.

## Distribution claim under review

The supported claim is deliberately narrow:

- an explicitly configured Android client binds to a separately installed shared local-LLM host;
- host and client are trusted through the accepted same-signer policy;
- the host owns model selection, runtime lifecycle, scheduling, policy and diagnostics;
- the client receives only the consumer inference contract and negotiated capabilities;
- prompts and generated output are not persisted as release evidence;
- package replacement is supported for compatible same-signer releases, but live sessions are not promised to survive process/package replacement.

Claims about arbitrary third-party callers, cross-signer public access, cloud fallback, live-session survival across host replacement or model-quality certification are out of scope.

## Review matrix

| Area | Repository evidence | Decision |
| --- | --- | --- |
| Public API surface | Consumer applications use the Binder client/contracts artifacts rather than generated Binder implementation details. | ACCEPT |
| Runtime boundary | Core `LocalLlmClient` semantics remain behind transport adapters; the shared host composes the existing runtime rather than exposing llama.cpp/JNI types. | ACCEPT |
| Service exposure | The proof host service is exported only behind a signature-level permission. | ACCEPT |
| Binding | Client configuration uses an explicit host package/service component. | ACCEPT |
| Caller authorization | Host authorization is based on Binder calling identity plus configured package/signing policy; denied callers do not progress to runtime negotiation. | ACCEPT |
| Model control | External consumers select application/use-case semantics, not arbitrary model files or host-private paths. | ACCEPT |
| Session/request ownership | Host-owned identities and ledgers isolate callers and prevent external-ID collisions. | ACCEPT |
| Cancellation/death | Cancellation, callback failure, stale epochs and process death have deterministic cleanup paths covered by SR-5 tests. | ACCEPT |
| Backpressure | Bounded callback queues fail closed rather than permitting unbounded transport growth. | ACCEPT |
| Privacy | Release evidence retains identity/timing/resource fields only; prompt/output, Binder tokens, signing secrets, GGUF bytes and host-private paths are excluded. | ACCEPT |
| Protocol versioning | Major incompatibility fails before registration; compatible minor versions negotiate common features. | ACCEPT |
| Packaging | Release consumer fixture compiles against packaged Binder AARs, keeping generated Binder plumbing out of normal consumer code. | ACCEPT |
| Console governance | The consumer path exposes inference capabilities only; host diagnostics/control-plane state is not granted through the consumer Binder API. | ACCEPT |
| Upgrade semantics | Compatible same-signer host replacement is validated as new traffic before/after replacement; live session survival is explicitly not part of the contract. | ACCEPT, physical run pending |
| Transport overhead | Binder logs expose client-observed time and core runtime time; the SR-VAL-09 comparator only accepts matched model/context/thinking/tuning-case evidence and applies no invented threshold. | ACCEPT, physical comparison pending |

## SR-VAL-09 measurement policy

`compare-shared-runtime-transport-evidence.py` is the canonical closeout comparator.

A comparison is accepted as **comparable evidence** only when:

1. the Binder and in-process records identify the same model SHA-256;
2. context size matches;
3. thinking mode matches;
4. the in-process record uses the explicit `sr6-transport-v1` tuning case;
5. at least one warm in-process sample exists;
6. the Binder transport envelope is internally consistent with `clientObservedTotalMs - coreTotalMs`.

The comparator reports measurements; it does not create a pass/fail performance budget. Any release threshold must be an explicit product/runtime policy rather than a hidden test constant.

The in-process tuning run used for this comparison must use the same non-sensitive SR-6 transport prompt and generation intent as the packaged Binder run. The `tuningCaseId` identifies that scenario without storing prompt text in the retained evidence.

Example closeout command:

```bash
python3 scripts/compare-shared-runtime-transport-evidence.py \
  --binder-log build/shared-runtime-evidence/<run>/positive-instrumentation.log \
  --in-process-log build/qwen35-tuning/<matched-run>.log \
  --tuning-case-id sr6-transport-v1 \
  --output build/shared-runtime-evidence/<run>/transport-comparison.json
```

## Package replacement policy

`capture-shared-runtime-package-upgrade-evidence.sh` is the canonical SR-6 upgrade runner.

It requires:

- full source commit SHA for both base and replacement host APKs;
- same accepted signing certificate for base host, replacement host, packaged client and client instrumentation APK;
- arm64-v8a physical hardware unless explicitly running emulator preflight;
- an already installed/selected curated Qwen3.5 model in host app data.

The runner proves:

```text
base host installed
  -> packaged client Binder traffic PASS
  -> adb install -r compatible replacement host
  -> host ready
  -> packaged client Binder traffic PASS
  -> selected model digest unchanged
```

It does **not** claim that the old process, Binder object, request or session survives replacement.

## Release blockers after this review

The remaining SR-6 blockers are evidence, not missing release architecture:

- execute the same-signer release-like matrix on representative physical arm64-v8a hardware;
- execute the independently signed denial case at release-package level;
- execute and retain the package-upgrade runner on the exact candidate pair;
- execute the matched SR-VAL-09 in-process/Binder measurement on the same representative device/model/profile conditions;
- complete applicable Q35 physical runtime evidence for the selected release model(s);
- bind all retained artifacts to the exact candidate commit, package versions, protocol/client versions, llama.cpp revision, signing certificate digest and model digest.

Until these are complete, the client AAR must not be described as generally released or the shared host as production-certified.

## Release sign-off checklist

- [x] Public consumer API remains transport-focused and does not expose JNI/llama.cpp internals.
- [x] Same-signer service permission and host authorization remain fail-closed.
- [x] Host retains model/use-case policy ownership.
- [x] Cross-client ownership, cancellation, death and backpressure behavior are deterministic.
- [x] Protocol major/minor compatibility policy is explicit.
- [x] Packaged AAR consumer path exists.
- [x] Console consumer access does not imply diagnostics/control-plane access.
- [x] Evidence privacy boundary is explicit.
- [x] Package-replacement semantics are explicit and do not promise session survival.
- [x] SR-VAL-09 comparison policy is explicit and threshold-free.
- [ ] Physical same-signer release-like evidence retained for the exact candidate.
- [ ] Physical invalid-signer denial retained for the exact candidate.
- [ ] Physical package-upgrade pre/post traffic evidence retained.
- [ ] Matched physical Binder/in-process SR-VAL-09 comparison retained.
- [ ] Applicable Q35 physical runtime evidence retained.
- [ ] Exact release notes bind all candidate identities and evidence references.

## Final repository-level conclusion

There is no repository-level governance reason to redesign the current shared-runtime boundary before device validation. The remaining work is to **execute and retain the exact physical evidence**, review the measured transport/device behavior, and only then decide whether the candidate can be published.

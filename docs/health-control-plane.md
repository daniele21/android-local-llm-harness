# Health and sanity control plane

This document describes the implemented Phase 2 health control plane. It covers static content-addressed model integrity and deterministic use-case sanity checks. It does not claim full GGUF compatibility, native load health, memory stability or physical-device readiness.

## Modules

| Module | Responsibility |
| --- | --- |
| `observability/contracts` | Stable targets, fixture definitions, rules, findings, reports and `HealthControlPlane` |
| `observability/health-engine` | Model-integrity orchestration, rule evaluation and privacy-safe persistence |
| `models/model-store` | File lookup, size metadata, streaming SHA-256 verification and store snapshot |
| `core/contracts` | `LocalLlmClient`, generation lifecycle and typed errors used by the sanity adapter |
| `observability/room-store` or `observability/in-memory-store` | Latest privacy-safe health-result persistence |

The health engine does not depend on Room, the console, JNI or Android UI.

## Assembly

```kotlin
import io.github.daniele21.localllm.observability.health.HealthEngine
import io.github.daniele21.localllm.observability.health.LocalLlmSanityExecutor

val health = HealthEngine(
    modelStore = modelStore,
    telemetryRepository = telemetryRepository,
    sanityExecutor = LocalLlmSanityExecutor(localLlmClient),
)
```

`localLlmClient` may be the in-process client today and a future transport implementation later. The adapter uses only the stable `LocalLlmClient` lifecycle.

## Model-integrity report

```kotlin
import io.github.daniele21.localllm.observability.ModelIntegrityTarget

val report = health.runModelIntegrity(
    ModelIntegrityTarget(
        id = modelProfile.id,
        digest = modelProfile.artifact.digest,
        expectedSizeBytes = modelProfile.artifact.sizeBytes,
    ),
)

check(report.status != HealthStatus.FAIL) {
    report.findings
        .filter { it.status == HealthStatus.FAIL }
        .joinToString { "${it.id}: ${it.remediation}" }
}
```

The current suite checks:

1. the exact digest is registered in the model store;
2. the resolved path is a regular file;
3. stored and on-disk size match the declared artifact size;
4. streaming SHA-256 verification matches the expected digest;
5. the model-store snapshot is consistent with the resolved entry.

A missing model makes dependent checks `NOT_RUN` rather than manufacturing secondary failures. A digest mismatch is a failure and recommends removing or quarantining the artifact before reimport.

The suite does not yet inspect tokenizer metadata, chat templates, architecture compatibility or quantization support. Those checks require the later metadata/load-health slice.

## Deterministic sanity suite

```kotlin
import io.github.daniele21.localllm.observability.SanityFixture
import io.github.daniele21.localllm.observability.SanityGenerationConfig
import io.github.daniele21.localllm.observability.SanityRule
import io.github.daniele21.localllm.observability.SanitySuiteDefinition

val suite = SanitySuiteDefinition(
    id = "assistant-sanity-v1",
    fixtures = listOf(
        SanityFixture(
            id = "short-summary",
            applicationId = applicationId,
            useCaseId = useCaseId,
            input = "Return exactly: READY",
            generation = SanityGenerationConfig(
                maxOutputTokens = 8,
                temperature = 0.0f,
                seed = 42L,
            ),
            rules = listOf(
                SanityRule.nonEmpty("non-empty"),
                SanityRule.exact("version-lock", "READY"),
                SanityRule.maxOutputTokens("token-limit", 8),
            ),
            timeoutMs = 30_000L,
        ),
    ),
)

val report = health.runSanitySuite(suite)
```

Each fixture performs:

```text
prepare
create session
generate with deterministic overrides
await terminal event
cancel on timeout
close session
```

A generation failure produces one failed execution finding. Its assertions become `NOT_RUN`, preserving the actual failing stage.

## Rules

The initial transport-friendly rule set supports:

- `NON_EMPTY`;
- `CONTAINS`;
- `NOT_CONTAINS`;
- `EXACT`;
- `MATCHES_REGEX` using a full-output match;
- `MAX_OUTPUT_TOKENS` using runtime generation metrics.

Use `EXACT` only for fixtures intentionally locked to one model digest, profile and deterministic configuration. Prefer structural assertions for normal use cases.

JSON-schema assertions, semantic labels and LLM-as-judge evaluation are not implemented in this slice.

## Persistence and privacy

The returned `HealthSuiteReport` contains rich findings and remediation. The telemetry repository stores one latest `HealthCheckResult` per suite/check identifier.

Normal persisted health data contains:

- suite and check identifiers;
- `PASS`, `WARN`, `FAIL` or `NOT_RUN`;
- duration;
- generic detail;
- recommended remediation.

It does not contain:

- fixture input;
- generated output;
- arbitrary exception messages;
- model bytes.

Fixture input and output exist only in memory during execution. A telemetry write failure does not fail the health suite or inference.

## Interpreting status

Suite status is aggregated as follows:

1. any `FAIL` makes the suite `FAIL`;
2. otherwise any `WARN` makes the suite `WARN`;
3. all `NOT_RUN` makes the suite `NOT_RUN`;
4. a partial mix containing `NOT_RUN` becomes `WARN`;
5. otherwise the suite is `PASS`.

## Remaining health work

The following remain separate Phase 2 slices:

- GGUF header, architecture, quantization, tokenizer and chat-template compatibility;
- native model load, context creation/reset/close and unload health;
- memory-return tolerance and thermal snapshots;
- cache health and corruption recovery;
- historical health runs and console views;
- benchmark baselines and regression policy;
- physical-device execution evidence.

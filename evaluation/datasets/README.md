# Evaluation datasets

`evaluation/datasets` owns deterministic parsing and pack-integrity behavior for model-evaluation datasets. It depends only on frozen evaluation contracts and the deterministic evaluator registry.

## Responsibilities

- parse canonical UTF-8/LF JSONL as bounded untrusted input;
- reject BOMs, CR/CRLF, blank records, duplicate JSON keys, unknown fields and unsupported case schema versions;
- validate pack-level case/category/preset/evaluator consistency;
- canonicalize case records with frozen field ordering;
- compute and verify the ordered SHA-256 content digest declared by the manifest.

## Boundaries

This module does not install or delete packs, choose samples, run inference, persist results or compose UI. Dataset message/expected-answer/metadata content stays in memory or app-private dataset storage and must not enter ordinary telemetry.

The canonical digest is sensitive to ordered case content and list ordering. Map-valued evaluator parameters and metadata are emitted with sorted keys so in-memory map construction order cannot change identity.

## Validation

Focused checks:

```bash
./gradlew :evaluation:datasets:testDebugUnitTest :evaluation:datasets:lintDebug
./gradlew spotlessCheck detekt verifyNoModelArtifacts
```

Repository validation and coding-agent navigation must also pass before D-02/D-03/D-04 are marked complete.

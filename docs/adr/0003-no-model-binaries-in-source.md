# ADR 0003: Model binaries are external artifacts

- Status: Accepted
- Date: 2026-08-01

## Context

GGUF files can be hundreds of megabytes or larger. Committing or packaging them accidentally would inflate the repository and application artifacts, complicate licensing and make model updates inseparable from SDK releases.

## Decision

GGUF and GGML binaries are forbidden in the repository source tree. The build runs a model-artifact guard, Git ignores these extensions and Android packaging excludes them as a secondary defense.

Models will be imported or downloaded into a content-addressed application-private store. CI test models must be fetched through an explicitly versioned source with a pinned digest.

## Consequences

- SDK releases remain independent from model releases.
- Developers must configure a local or CI model source for real-model tests.
- Model licensing and checksums can be reviewed separately.
- Future Play AI pack delivery can implement the same artifact-source contract.

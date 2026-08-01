# Architecture

## Data plane

```text
Native app / Capacitor plugin
            |
       LocalLlmClient
            |
     InProcessTransport
            |
    RuntimeOrchestrator
      |       |       |
 App registry |   Telemetry sink
      |       |
 Model profile|
      |    Model store
      |       |
      +--- llama.cpp JNI ---> GGUF
```

The embedded runtime and the future shared service must execute the same data plane. Only the transport and model-store ownership change.

## Control plane

The `local-llm-console` application is the initial developer control plane. It will expose:

- runtime overview;
- application/use-case bindings;
- installed GGUF artifacts;
- model/context lifecycle;
- per-run timelines;
- structured logs;
- latency, throughput and memory metrics;
- cache inspection and invalidation;
- sanity and health suites;
- benchmark history;
- privacy-safe diagnostic export.

During the embedded phase, apps will expose a signature-protected diagnostics bridge. In the shared phase, the console will query the central host directly.

## Model identity

A model is represented at three levels:

1. `GgufArtifact`: immutable physical file identified by SHA-256.
2. `GgufModelProfile`: exact load configuration for that artifact.
3. `UseCaseProfile`: prompt, generation, output and cache policy for a product use case.

`AppModelBinding` resolves `applicationId + useCaseId` to a single explicit use-case profile.

## Cache hierarchy

Caches are separate domains because they have different invalidation rules:

1. GGUF artifact store on disk.
2. Operating-system file page cache through memory mapping.
3. Loaded model handle in RAM.
4. Context/KV cache per session.
5. Prefix/session snapshots keyed by model, backend build and prompt profile.
6. Optional deterministic result cache, namespaced by application and use case.

The initial implementation only defines artifact and in-memory lifecycle contracts. Snapshot/result caching will be added after correctness and benchmark baselines are established.

## Runtime invariants

- One loaded model and one active decode by default.
- No undeclared model substitution.
- Every generation has stable application, use-case, session and request identifiers.
- Prompt/output persistence is disabled by default.
- Native handles are never exposed outside the backend module.
- Large payloads will not cross the future Binder boundary inline.

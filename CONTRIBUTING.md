# Contributing

## Branches and commits

- Branches: `feature/<scope>`, `fix/<scope>`, `chore/<scope>`
- Commits: imperative and scoped when useful
- Do not commit GGUF files or diagnostic exports

## Architectural rules

- Keep product model selection explicit in `AppModelBinding`.
- Do not expose native pointers or llama.cpp types outside `backends/llama-cpp`.
- Do not persist prompts or outputs in telemetry by default.
- Add a cache only with a documented key, invalidation policy, size budget and metrics.
- Any native runtime upgrade requires benchmark and sanity-suite comparison.

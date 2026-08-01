# Contributing

## Development prerequisites

- JDK 17
- Android SDK API 37
- Android Build Tools 36.0.0
- Android NDK 28.2.13676358

Use the committed Gradle wrapper for every local and CI build.

## Local validation

```bash
./gradlew qualityCheck check lintDebug assembleDebug
```

The first repository bootstrap may temporarily use an installed Gradle 9.5.0 distribution only to generate and commit the wrapper.

## Formatting and static analysis

```bash
./gradlew spotlessApply
./gradlew detekt
```

Detekt is intentionally executed through its CLI from the root build. The stable Detekt Gradle plugin is not coupled to Android Gradle Plugin 9.

## Dependency locking

Dependency locking is enabled for all configurations. Whenever a dependency is added or intentionally updated, regenerate and review lock state with:

```bash
./gradlew dependencies --write-locks
```

Do not introduce dynamic versions such as `latest.release`, `+` or unbounded ranges.

## Branches and commits

- Branches: `feature/<scope>`, `fix/<scope>`, `chore/<scope>`
- Commits: imperative and scoped when useful
- Do not commit GGUF, GGML or diagnostic export files

## Architectural rules

- Keep product model selection explicit in `AppModelBinding`.
- Do not expose native pointers or `llama.cpp` types outside `backends/llama-cpp`.
- Do not persist prompts or outputs in telemetry by default.
- Add a cache only with a documented key, invalidation policy, size budget and metrics.
- Any native runtime upgrade requires benchmark and sanity-suite comparison.
- Add an ADR for choices that materially constrain public contracts, native source ownership, storage or process boundaries.

## Pull-request checks

A change is ready only when formatting, static analysis, unit tests, Android lint and debug/internal builds pass from a clean checkout.

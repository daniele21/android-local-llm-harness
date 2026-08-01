# ADR 0002: Build and quality toolchain

- Status: Accepted
- Date: 2026-08-01

## Context

The project combines Android libraries, an Android application and a native C++ backend. It needs reproducible versions and quality checks without coupling the build to tools that do not yet support Android Gradle Plugin 9 reliably.

## Decision

- Use Android Gradle Plugin 9.3.0, Gradle 9.5.0, JDK 17, Android API 37, Build Tools 36.0.0 and NDK 28.2.13676358.
- Use the Android Gradle Plugin built-in Kotlin support.
- Centralize dependency and plugin versions in `gradle/libs.versions.toml`.
- Use Spotless with ktlint for formatting.
- Run stable Detekt 1.23.8 through a root JavaExec CLI task rather than applying its Android Gradle plugin.
- Use Android Lint as a blocking build check.
- Enable Gradle dependency locking and prohibit dynamic versions.
- Keep common build configuration in the root and module scripts for now; introduce convention plugins only after repeated build patterns justify them.

## Consequences

- Static analysis remains independent from AGP/Kotlin plugin compatibility.
- Build files remain straightforward during the early module count.
- The wrapper and all tool versions are pinned.
- A future move to Detekt 2 or convention plugins requires a new review but not an SDK API change.

## Alternatives considered

- Detekt 2 alpha Gradle plugin: rejected for Phase 0 because it is pre-stable and has AGP 9 built-in Kotlin caveats.
- Detekt 1.x Android Gradle plugin: rejected because its compatibility matrix predates AGP 9.
- Immediate custom convention plugins: deferred because they would initially wrap almost identical configuration without clear domain-specific variants.

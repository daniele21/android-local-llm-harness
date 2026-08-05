#!/usr/bin/env python3
"""Run the dev-integration documentation migration with unambiguous anchors."""

from __future__ import annotations

from pathlib import Path

import apply_dev_integration_docs as base

ROOT = Path(__file__).resolve().parents[1]


def replace_first(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text()
    if old not in text:
        raise RuntimeError(f"Missing anchor in {path}: {old}")
    target.write_text(text.replace(old, new, 1))


def update_agents() -> None:
    base.replace_once(
        "AGENTS.md",
        """1. [`README.md`](README.md) — purpose, toolchain and top-level structure.
2. [`BRANCHING.md`](BRANCHING.md) — canonical branch and pull-request discipline.
3. [`docs/architecture.md`](docs/architecture.md) — data-plane and control-plane boundaries.
4. [`docs/current-state.md`](docs/current-state.md) — active integration and recovery order.
5. [`docs/roadmap.md`](docs/roadmap.md) — detailed implementation status and remaining evidence.
6. [`docs/implementation-plan.md`](docs/implementation-plan.md) — target behavior and acceptance criteria.
7. [`docs/definition-of-done.md`](docs/definition-of-done.md) — merge and production readiness.
8. [`docs/api-usage.md`](docs/api-usage.md) — embedded API and lifecycle.
9. [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md), [`docs/device-e2e-evidence.md`](docs/device-e2e-evidence.md) and [`docs/play-internal-phone-test.md`](docs/play-internal-phone-test.md) — Android validation paths.
10. [`docs/adr/README.md`](docs/adr/README.md) — accepted architectural decisions.
""",
        """1. [`README.md`](README.md) — purpose, toolchain and top-level structure.
2. [`BRANCHING.md`](BRANCHING.md) — canonical branch and pull-request discipline.
3. [`docs/dev-integration-and-harness-0.5-plan.md`](docs/dev-integration-and-harness-0.5-plan.md) — active Harness 0.5.0 integration sequence.
4. [`docs/architecture.md`](docs/architecture.md) — data-plane and control-plane boundaries.
5. [`docs/current-state.md`](docs/current-state.md) — active integration and recovery order.
6. [`docs/roadmap.md`](docs/roadmap.md) — detailed implementation status and remaining evidence.
7. [`docs/implementation-plan.md`](docs/implementation-plan.md) — target behavior and acceptance criteria.
8. [`docs/definition-of-done.md`](docs/definition-of-done.md) — merge and production readiness.
9. [`docs/api-usage.md`](docs/api-usage.md) — embedded API and lifecycle.
10. [`docs/device-e2e-testing.md`](docs/device-e2e-testing.md), [`docs/device-e2e-evidence.md`](docs/device-e2e-evidence.md) and [`docs/play-internal-phone-test.md`](docs/play-internal-phone-test.md) — Android validation paths.
11. [`docs/adr/README.md`](docs/adr/README.md) — accepted architectural decisions.
""",
    )
    base.insert_before(
        "AGENTS.md",
        "## Change workflow",
        """## Branch discipline

- Ordinary work starts from the latest green `dev` and opens a pull request back to `dev`.
- Do not open a feature, dependency or documentation pull request directly to `main`; the validation gate rejects it.
- `main` is reserved for a complete `dev -> main` promotion or an explicit emergency hotfix.
- A red cumulative `dev` freezes new integrations until a focused fix-forward or revert restores the branch.
- Feature pull requests normally squash into `dev`; promotions preserve the validated `dev` identity with a merge commit.
- Never reuse a merged or superseded branch for new implementation work.
""",
    )
    base.replace_once(
        "AGENTS.md",
        "1. Confirm the canonical base and active pull requests.",
        "1. Confirm the latest green `dev`, the intended target and active pull requests; use `main` only for an explicit hotfix.",
    )


def update_roadmap() -> None:
    base.replace_once(
        "docs/roadmap.md",
        """## Current execution status — August 2026

Phase 1 is merged into `main` through pull request #13. Phase 2 has progressed through persistent telemetry, health checks, generation sanity, cache health, Android resource observability, benchmark regression checks and selective sanity-rule recovery.

The current `main` head contains the work merged through pull request #29, including the ARM64 emulator preflight and the Google Play-installable physical-device validation app that does not require developer mode or ADB.

The repository is merge-ready for continued development but is **not production-ready** until the physical-device GGUF evidence gate is completed.
""",
        """## Current execution status — August 2026

The functional runtime, observability, connected phone console, model distribution and retained benchmark history are implemented. Harness 0.5.0 is now following the protected `dev` integration plan: `dev` is the ordinary development line, while `main` remains stable and release-oriented.

The active sequence is governance and cumulative CI, focused model-management recovery, real Android branding, Compose architecture and surface completion, UI/accessibility validation, then signed internal distribution and physical-device evidence.

The repository remains **not production-ready** until the representative physical-device GGUF evidence gate is completed.
""",
    )
    base.replace_once(
        "docs/roadmap.md",
        """- [x] use `main` as the canonical integrated implementation line
- [x] consolidate the complete Phase 1 runtime through PR #13
- [x] close superseded Phase 1 implementation PRs #8 and #12
- [x] keep dependency-only changes separate from functional runtime work
- [x] document branch and pull-request discipline in [`BRANCHING.md`](../BRANCHING.md)
- [x] supersede the alternative Phase 2 health-control-plane line in PR #22
- [x] recover only compatible sanity-assertion behavior on a fresh branch from current `main`
- [x] merge the recovery and emulator-preflight line through PR #28
- [ ] delete historical remote branches after their unique commits and recovery notes are audited
""",
        """- [x] keep `main` as the protected stable and release-oriented line
- [x] create `dev` from the restored green repository baseline
- [x] make `dev` the documented base and target for ordinary work
- [x] add automated rejection of ordinary pull requests opened directly to `main`
- [x] add cumulative Android and native validation after merges to `dev`
- [x] add complete non-scoped validation and packaging for `dev -> main` promotions
- [x] document hotfix, forward-port, merge and rollback behavior in ADR 0008
- [ ] apply repository-level protection to `dev` and verify direct pushes are rejected
- [ ] delete historical remote branches after their unique commits and recovery notes are audited
""",
    )
    base.insert_before(
        "docs/roadmap.md",
        "## Verified repository gate",
        """## Harness 0.5.0 integration sequence

- [x] restore the green repository baseline and protect `main` through PR #55
- [x] establish the `dev` branch and correct its curated-model-catalog Detekt regression through PR #58
- [x] implement repository-owned `dev` validation, promotion gates, target-branch policy, PR template and integration ADR
- [ ] apply the equivalent repository ruleset to `dev`
- [ ] align, validate and squash-merge the focused model-management recovery in PR #53
- [ ] close PR #34 as superseded after auditing its unique behavior
- [ ] integrate real Android launcher, themed-icon and Compose brand assets
- [ ] complete Navigation Compose, ViewModel/UDF and the five primary product surfaces
- [ ] add Compose UI, screenshot, accessibility and responsive-layout evidence
- [ ] build and upload the signed AAB to Google Play Internal Testing
- [ ] capture privacy-safe representative physical-device GGUF evidence
- [ ] promote the validated `dev` candidate to `main` with a merge commit and tag Harness 0.5.0 only after the applicable release gates pass
""",
    )
    base.replace_once(
        "docs/roadmap.md",
        "### Benchmark regression and baseline history console — PR #33",
        "### Benchmark regression and baseline history console — selectively recovered through PR #51",
    )
    replace_first(
        "docs/roadmap.md",
        "- [ ] rebase or retarget onto `main` after PR #31 is merged",
        "- [x] recover the current retained-history behavior through PR #51 without reviving the legacy standalone console",
    )
    base.replace_once(
        "docs/roadmap.md",
        "### Explicit model management console — PR #34",
        "### Legacy explicit model management console — PR #34",
    )
    base.replace_once(
        "docs/roadmap.md",
        "- [ ] rebase or retarget onto `main` after PR #31 is merged",
        "- [ ] close PR #34 as superseded after the focused PR #53 recovery is merged into `dev`",
    )


def main() -> int:
    base.update_branching()
    update_agents()
    base.update_readme()
    base.update_current_state()
    update_roadmap()
    base.update_definition_of_done()
    base.update_versioning()
    print("Canonical dev-integration documentation updated with unambiguous anchors.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

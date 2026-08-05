#!/usr/bin/env python3
"""Apply the canonical dev-integration documentation update for Harness 0.5.0."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text()
    if text.count(old) != 1:
        raise RuntimeError(f"Expected one anchor in {path}, found {text.count(old)}")
    target.write_text(text.replace(old, new, 1))


def insert_before(path: str, anchor: str, content: str) -> None:
    replace_once(path, anchor, f"{content}\n{anchor}")


def update_branching() -> None:
    replace_once(
        "BRANCHING.md",
        """## Current canonical line

As of August 2026:

- `main` is the canonical integrated baseline;
- pull request #13 merged the consolidated Phase 1 implementation into `main`;
- pull requests #21, #23, #24, #25, #26 and #27 merged the current Phase 2 telemetry, health, resource and benchmark slices into `main`;
- pull request #28 merged the useful sanity-assertion recovery and ARM64 emulator preflight;
- pull request #29 merged the Google Play-installable physical-device validation app;
- the physical-device GGUF gate remains open and blocks production readiness, releases to application consumers and device-performance claims.

New work must start from the latest `main` unless an explicit, documented stacked dependency requires otherwise.
""",
        """## Current canonical lines

As of August 2026:

- `main` is the stable, protected and release-oriented line;
- `dev` is the canonical integration base and target for ordinary feature, fix, documentation, dependency and UX/UI work;
- changes reach both long-lived branches through pull requests;
- `dev -> main` is the normal promotion path and is validated as one release candidate;
- direct pull requests to `main` are reserved for the `dev` promotion or an explicit emergency hotfix;
- the physical-device GGUF gate remains open and blocks production readiness, application-consumer releases and device-performance claims.

New work must start from the latest green `dev` unless it is an explicitly documented hotfix based on `main` or a short-lived stacked dependency.
""",
    )
    replace_once(
        "BRANCHING.md",
        "After a major integration into `main`:",
        "After a major integration into `dev` or a promotion into `main`:",
    )
    replace_once(
        "BRANCHING.md",
        "require the dependency branch to be based on the current `main` before review;",
        "require the dependency branch to be based on the current `dev` before review;",
    )
    replace_once(
        "BRANCHING.md",
        "1. Start from the latest intended target branch, normally `main`.",
        "1. Start from the latest intended target branch, normally the latest green `dev`; use `main` only for an explicit hotfix.",
    )
    replace_once(
        "BRANCHING.md",
        """## Required protection for `main`

Repository settings for `main` should require:

- changes through pull requests;
- the stable aggregate status check `Repository validation`;
- the branch to be current with `main` before merge;
- resolved review conversations;
- force pushes and branch deletion to be disabled;
- repository administrators to follow the same protection rules, except for documented emergency recovery.
""",
        """## Required protection for long-lived branches

Repository settings for both `main` and `dev` must require:

- changes through pull requests;
- the stable aggregate status check `Repository validation`;
- the pull-request branch to be current with its target before merge;
- resolved review conversations;
- force pushes and branch deletion to be disabled;
- repository administrators to follow the same protection rules, except for documented emergency recovery.

`main` additionally requires at least one approval and normally accepts only `dev` promotions. `dev` remains the daily integration target and is frozen when its cumulative post-merge validation is red.
""",
    )
    insert_before(
        "BRANCHING.md",
        "## Merge discipline",
        """## Merge and promotion strategy

- Feature, fix, documentation, dependency and UX/UI pull requests target `dev` and normally use squash merge.
- Promotion pull requests use `dev` as head and `main` as base, run complete non-scoped Android, native and packaging gates, and use a merge commit.
- After promotion, synchronize the resulting `main` merge commit back into `dev` before the next promotion cycle.
- Emergency hotfixes start from `main`, use squash merge into `main`, then return through a `main -> dev` forward-port pull request.
- Tags and release artifacts are created only from validated `main` commits.
""",
    )


def update_agents() -> None:
    replace_once(
        "AGENTS.md",
        "2. [`BRANCHING.md`](BRANCHING.md) — canonical branch and pull-request discipline.\n3. [`docs/architecture.md`](docs/architecture.md)",
        "2. [`BRANCHING.md`](BRANCHING.md) — canonical branch and pull-request discipline.\n3. [`docs/dev-integration-and-harness-0.5-plan.md`](docs/dev-integration-and-harness-0.5-plan.md) — active Harness 0.5.0 integration sequence.\n4. [`docs/architecture.md`](docs/architecture.md)",
    )
    for number in range(4, 11):
        replace_once(
            "AGENTS.md",
            f"\n{number}. ",
            f"\n{number + 1}. ",
        )
    insert_before(
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
    replace_once(
        "AGENTS.md",
        "1. Confirm the canonical base and active pull requests.",
        "1. Confirm the latest green `dev`, the intended target and active pull requests; use `main` only for an explicit hotfix.",
    )


def update_readme() -> None:
    replace_once(
        "README.md",
        """`main` is the canonical integrated implementation line. Repository-state alignment, Android Gradle Plugin 9.3.1 and `actions/checkout@v7` were integrated through PRs #44, #45 and #47.
""",
        """`dev` is the canonical integration line for ordinary work. `main` remains the protected stable and release-oriented line and receives normal changes only through a validated `dev -> main` promotion. Harness 0.5.0 integration is tracked in [`docs/dev-integration-and-harness-0.5-plan.md`](docs/dev-integration-and-harness-0.5-plan.md).
""",
    )
    replace_once(
        "README.md",
        """## Current priorities

1. Integrate catalog selection, download progress, explicit installation and installed-model state into the connected phone application.
2. Add durable installed-model catalog/profile metadata without making installation activate a binding.
3. Recover only the still-unique benchmark-history and model-management behavior from legacy draft PRs #33 and #34 on fresh branches from current `main`.
4. Complete the physical-device production-readiness evidence on representative Android `arm64-v8a` hardware.
5. Complete the remaining Compose architecture, accessibility, responsive and UI-test work.
6. Add native Android and Capacitor integration surfaces as thin adapters.
7. Add a signature-protected diagnostics bridge and Binder transport before promoting the console into a shared runtime host.
""",
        """## Current priorities

1. Complete and protect the `dev` integration line, cumulative CI and promotion gates for Harness 0.5.0.
2. Merge the focused model-management recovery from PR #53 into `dev`, then close the legacy PR #34 as superseded.
3. Integrate the Android brand kit into launcher assets, theme tokens and reusable Compose components.
4. Complete Navigation Compose, ViewModel/UDF migration and the Overview, Playground, Models, Diagnostics and Settings surfaces.
5. Add Compose UI, screenshot, accessibility and responsive-layout validation.
6. Produce a signed AAB for Google Play Internal Testing and capture representative privacy-safe physical-device GGUF evidence.
7. Add native Android and Capacitor adapters, followed by the signature-protected diagnostics bridge and Binder transport.
""",
    )
    replace_once(
        "README.md",
        "The complete repository gate includes repository guards, scoped Android validation, native host tests and packaging verification.",
        "Pull requests into `dev` use repository guards and the relevant Android, native and packaging scopes. Every merge push on `dev` runs cumulative validation; a `dev -> main` promotion runs complete non-scoped Android, native and packaging gates. The complete repository gate includes repository guards, Android validation, native host tests and packaging verification.",
    )


def update_current_state() -> None:
    replace_once(
        "docs/current-state.md",
        """## Canonical line

`main` is the only canonical integrated implementation line.

Integrated head before the next recovery block:

```text
29357430a2d161c8e8c0686d1e5f5429f168a816
Restore repository validation baseline (#55)
```

Historical implementation, staging and sandbox branches are audit references only. New work must start from current `main` unless a pull request explicitly documents a temporary stacked dependency.
""",
        """## Canonical lines

`dev` is the canonical integration base and target for ordinary repository work. `main` is the protected stable and release-oriented line.

Current integration baseline before the governance pull request:

```text
d9404084ee79c542ca24c4c790c0e0d20d118f01
Split curated model catalog by family (#58)
```

The normal path is a focused pull request into `dev`, cumulative validation on the merged `dev` commit, then a complete `dev -> main` promotion. Historical implementation, staging and sandbox branches are audit references only. New work starts from the latest green `dev` unless it is an explicit hotfix based on `main`.
""",
    )
    replace_once(
        "docs/current-state.md",
        """The post-merge validation, native host tests, brand reproducibility check and Android artifact packaging passed on `main` at `2935743`. The local and remote `dev` refs point to that same merge commit; `dev` remains a bootstrap ref rather than the canonical feature-integration line until the Phase 1 governance and CI changes are merged.
""",
        """The repository baseline was restored on `main` through PR #55. `dev` now contains the curated-catalog expansion and the modular Detekt fix from PR #58. The active governance pull request adds cumulative `dev` validation, promotion gates, branch-target enforcement and the canonical documentation required for Harness 0.5.0. Repository-level protection for `dev` remains an administrative gate that cannot be applied from the code tree.
""",
    )
    replace_once(
        "docs/current-state.md",
        """### Block 7 — selective model-management recovery

Status: **NEXT**.

Recover unique verification, confirmation, loaded-model protection, cleanup and operation-state behavior from PR #34 on a fresh branch from current `main`. Do not restore its parallel store or old console composition.
""",
        """### Block 7 — selective model-management recovery

Status: **IN PROGRESS through PR #53**.

PR #53 is retargeted to `dev`, contains only the focused connected-phone implementation and no longer includes temporary self-modifying workflows. Its source-level recovery compiles and its targeted controller tests pass. It remains draft until it is aligned with the final governance baseline and complete repository validation is green.
""",
    )
    replace_once(
        "docs/current-state.md",
        """fresh branch from current main
  -> focused implementation
  -> deterministic tests
  -> documentation and this ledger updated
  -> complete CI
  -> merge
  -> next block starts from refreshed main
""",
        """fresh branch from latest green dev
  -> focused implementation
  -> deterministic tests
  -> documentation and this ledger updated
  -> pull request and scoped CI
  -> squash merge into dev
  -> cumulative dev validation
  -> later complete promotion into main
""",
    )
    replace_once(
        "docs/current-state.md",
        """## Repository administration still required

Issue #46 tracks operational hardening outside the code tree:

- protect `main`;
- require pull requests;
- require the stable `Repository validation` check;
- block force pushes and deletion of `main`;
- enable automatic deletion of merged feature branches where appropriate.
""",
        """## Repository administration still required

`main` protection is implemented through issue #46. The remaining repository-level action for Harness 0.5.0 is to apply equivalent push, force-push and deletion protection to `dev`, require an up-to-date pull request and `Repository validation`, and require resolved conversations. The default branch remains `main`; feature branches are removed after merge and audit rather than through an indiscriminate global deletion rule.
""",
    )


def update_roadmap() -> None:
    replace_once(
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
    replace_once(
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
    insert_before(
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
    replace_once(
        "docs/roadmap.md",
        "### Benchmark regression and baseline history console — PR #33",
        "### Benchmark regression and baseline history console — selectively recovered through PR #51",
    )
    replace_once(
        "docs/roadmap.md",
        "- [ ] rebase or retarget onto `main` after PR #31 is merged",
        "- [x] recover the current retained-history behavior through PR #51 without reviving the legacy standalone console",
    )
    replace_once(
        "docs/roadmap.md",
        "### Explicit model management console — PR #34",
        "### Legacy explicit model management console — PR #34",
    )
    replace_once(
        "docs/roadmap.md",
        "- [ ] rebase or retarget onto `main` after PR #31 is merged",
        "- [ ] close PR #34 as superseded after the focused PR #53 recovery is merged into `dev`",
    )


def update_definition_of_done() -> None:
    insert_before(
        "docs/definition-of-done.md",
        "## Functional completion",
        """## Branch and promotion completion

- Ordinary feature, fix, dependency, documentation and UX/UI work is based on the latest green `dev` and targets `dev`.
- The pull request is current with `dev`, conflict-free and green on `Repository validation`.
- A merge into `dev` is not release evidence by itself; the resulting cumulative `dev` validation must also remain green.
- A release candidate is an exact `dev` commit promoted to `main` through a pull request with complete non-scoped Android, native and packaging validation.
- Promotion uses a merge commit so the validated `dev` identity remains auditable; tags and release artifacts originate only from validated `main` commits.
- An emergency hotfix is based on `main`, validated there and forward-ported through a `main -> dev` pull request.
- A red `dev` freezes unrelated integrations until a focused fix-forward or revert restores the branch.
""",
    )
    replace_once(
        "docs/definition-of-done.md",
        "The relevant narrow checks pass during development, and the complete repository gate passes before merge:",
        "The relevant narrow checks pass during development, the pull-request gate passes before merge into `dev`, and the cumulative `dev` gate passes on the resulting integration commit:",
    )
    replace_once(
        "docs/definition-of-done.md",
        "all required merge-readiness validation gates passing\n```",
        "all required pull-request and cumulative dev validation gates passing\nrelease promotion tied to an exact validated dev commit\n```",
    )


def update_versioning() -> None:
    insert_before(
        "docs/versioning.md",
        "## Release gate",
        """## Integration and release lines

- `dev` carries snapshot development and is the only normal base and target for feature work.
- `main` carries stable promotable history and receives ordinary changes only through a complete `dev -> main` promotion.
- Feature pull requests normally squash into `dev`; promotions use a merge commit to preserve the exact validated candidate.
- Tags, changelog release entries and distributed Android artifacts are created only from validated `main` commits.
- Emergency hotfixes are applied to `main` and then forward-ported to `dev`.
""",
    )
    replace_once(
        "docs/versioning.md",
        """A release requires:

- passing CI from a clean checkout;
""",
        """A release requires:

- an exact `dev` candidate promoted to `main` through a protected pull request;
- complete non-scoped Android, native and packaging validation on the candidate;
- passing CI from a clean checkout;
""",
    )
    replace_once(
        "docs/versioning.md",
        """## Development versions

The repository starts at `0.1.0-SNAPSHOT`. The first tagged `0.1.0` release is reserved for a functional embedded GGUF inference path and does not occur during repository hardening alone.
""",
        """## Development versions

Development builds on `dev` use snapshot semantics and are not releases. Harness `0.5.0` is the current internal-integration target; it may be promoted to `main` and distributed through Google Play Internal Testing only after its promotion gates pass. It must not be described as production-ready until representative physical-device GGUF lifecycle, cancellation, memory, JNI-loading and thermal evidence is complete.
""",
    )


def main() -> int:
    update_branching()
    update_agents()
    update_readme()
    update_current_state()
    update_roadmap()
    update_definition_of_done()
    update_versioning()
    print("Canonical dev-integration documentation updated.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

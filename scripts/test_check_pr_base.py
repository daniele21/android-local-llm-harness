#!/usr/bin/env python3
"""Deterministic tests for pull-request base policy."""

from __future__ import annotations

from check_pr_base import validate_pr_base


def assert_allowed(base: str, head: str) -> None:
    assert validate_pr_base(base, head) is None, f"expected allowed: {head} -> {base}"


def assert_rejected(base: str, head: str) -> None:
    assert validate_pr_base(base, head) is not None, f"expected rejected: {head} -> {base}"


def main() -> int:
    assert_allowed("dev", "codex/navigation-compose")
    assert_allowed("dev", "dependabot/gradle/plugin")
    assert_allowed("main", "dev")
    assert_allowed("main", "hotfix/release-signing")
    assert_allowed("main", "agent/hotfix-runtime-crash")
    assert_allowed("main", "codex/hotfix-ci")

    assert_rejected("main", "feature/models-screen")
    assert_rejected("main", "agent/recover-model-management")
    assert_rejected("main", "dependabot/gradle/plugin")

    print("Pull-request base policy tests passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

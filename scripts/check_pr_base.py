#!/usr/bin/env python3
"""Enforce the repository pull-request base policy."""

from __future__ import annotations

import argparse

ALLOWED_HOTFIX_PREFIXES = ("hotfix/", "agent/hotfix-", "codex/hotfix-")


def validate_pr_base(base: str, head: str) -> str | None:
    """Return an error message when a pull request violates branch policy."""
    normalized_base = base.strip()
    normalized_head = head.strip()

    if normalized_base != "main":
        return None
    if normalized_head == "dev":
        return None
    if normalized_head.startswith(ALLOWED_HOTFIX_PREFIXES):
        return None

    return (
        "pull requests to main are reserved for dev promotions or explicit "
        "hotfix branches; retarget this pull request to dev"
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    error = validate_pr_base(args.base, args.head)
    if error is not None:
        print(f"branch policy violation: {error}")
        return 1

    print(f"branch policy accepted: {args.head} -> {args.base}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Canonical documentation validator with repo-template-sw schema-2 support."""

from __future__ import annotations

import argparse
import importlib.util
import json
import math
from pathlib import Path
import sys

_IMPL_PATH = Path(__file__).with_name("verify_docs_impl.py")
_SPEC = importlib.util.spec_from_file_location("_harness_verify_docs_impl", _IMPL_PATH)
if _SPEC is None or _SPEC.loader is None:
    raise RuntimeError(f"cannot load documentation validator implementation: {_IMPL_PATH}")
_IMPL = importlib.util.module_from_spec(_SPEC)
sys.modules[_SPEC.name] = _IMPL
_SPEC.loader.exec_module(_IMPL)
_IMPL.POLICY_PATH = Path(".engineering/documentation-policy.json")

for _name in dir(_IMPL):
    if not _name.startswith("__"):
        globals()[_name] = getattr(_IMPL, _name)


def _measure(path: Path, chars_per_token: int) -> tuple[int, int]:
    text = path.read_text(encoding="utf-8")
    return len(text.splitlines()), math.ceil(len(text) / chars_per_token)


def _check_budget(
    path: Path,
    label: str,
    budget: dict[str, int],
    chars_per_token: int,
    errors: list[str],
) -> None:
    if not path.is_file():
        return
    lines, tokens = _measure(path, chars_per_token)
    if lines > int(budget["max_lines"]):
        errors.append(f"{label} too long: {lines} > {budget['max_lines']} ({path})")
    if tokens > int(budget["max_estimated_tokens"]):
        errors.append(
            f"{label} too expensive: ~{tokens} > {budget['max_estimated_tokens']} ({path})"
        )


def _schema2_main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--base")  # accepted for workflow compatibility; schema 2 is state-based
    parser.add_argument("--template-mode", action="store_true")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    policy_path = root / ".engineering/documentation-policy.json"
    if not policy_path.is_file():
        print("FAIL: missing .engineering/documentation-policy.json")
        return 1

    policy = json.loads(policy_path.read_text(encoding="utf-8"))
    if policy.get("schema_version") != 2:
        return _IMPL.main()

    budgets = policy.get("budgets", {})
    chars_per_token = int(policy.get("estimated_token_characters", 4))
    errors: list[str] = []

    required_budgets = {
        "root_agents",
        "scoped_agents",
        "current_state",
        "active_workstream",
        "architecture",
        "feature_doc",
    }
    missing = required_budgets - set(budgets)
    if missing:
        errors.append(f"documentation policy missing budgets: {sorted(missing)}")
    else:
        _check_budget(
            root / "AGENTS.md",
            "root AGENTS",
            budgets["root_agents"],
            chars_per_token,
            errors,
        )
        _check_budget(
            root / "docs/current-state.md",
            "current state",
            budgets["current_state"],
            chars_per_token,
            errors,
        )
        _check_budget(
            root / "docs/architecture.md",
            "architecture",
            budgets["architecture"],
            chars_per_token,
            errors,
        )

        excluded = set(policy.get("context_exclude_directories", []))
        for guide in root.rglob("AGENTS.md"):
            if guide == root / "AGENTS.md" or any(part in excluded for part in guide.relative_to(root).parts):
                continue
            _check_budget(
                guide,
                "scoped AGENTS",
                budgets["scoped_agents"],
                chars_per_token,
                errors,
            )

        feature_root = root / "docs/features"
        if feature_root.is_dir():
            for path in feature_root.glob("*.md"):
                if path.name != "README.md":
                    _check_budget(
                        path,
                        "feature doc",
                        budgets["feature_doc"],
                        chars_per_token,
                        errors,
                    )

        workstream_root = root / "docs/workstreams"
        completed_markers = tuple(
            marker.lower() for marker in policy.get("completed_workstream_markers", [])
        )
        active_count = 0
        if workstream_root.is_dir():
            for path in workstream_root.glob("*.md"):
                if path.name == "README.md" or path.name.startswith("_"):
                    continue
                active_count += 1
                _check_budget(
                    path,
                    "active workstream",
                    budgets["active_workstream"],
                    chars_per_token,
                    errors,
                )
                text = path.read_text(encoding="utf-8").lower()
                if any(marker in text for marker in completed_markers):
                    errors.append(
                        f"completed workstream kept active: {path.relative_to(root)}; finalize/delete by default"
                    )
        else:
            active_count = 0

    print("Documentation health")
    print(f"active workstreams: {locals().get('active_count', 0)}")
    for error in errors:
        print(f"FAIL: {error}")
    print("RESULT:", "FAIL" if errors else "PASS")
    return 1 if errors else 0


if __name__ == "__main__":
    policy_path = Path(".engineering/documentation-policy.json")
    try:
        policy = json.loads(policy_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        policy = {}
    if policy.get("schema_version") == 2:
        raise SystemExit(_schema2_main())
    raise SystemExit(_IMPL.main())

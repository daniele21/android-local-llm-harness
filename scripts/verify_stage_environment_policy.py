#!/usr/bin/env python3
"""Verify that real-environment evidence is release-gated, not integration-gated."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--template-mode", action="store_true")
    return parser.parse_args()


def load_json(path: Path, errors: list[str]) -> dict:
    if not path.is_file():
        errors.append(f"missing required file: {path}")
        return {}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as exc:
        errors.append(f"invalid JSON {path}: {exc}")
        return {}
    if not isinstance(value, dict):
        errors.append(f"{path} must contain a JSON object")
        return {}
    return value


def expect(section: dict, key: str, value: object, errors: list[str], prefix: str) -> None:
    if section.get(key) != value:
        errors.append(f"{prefix}.{key} must be {value!r}")


def main() -> int:
    args = parse_args()
    root = Path(args.root).resolve()
    errors: list[str] = []

    commands = load_json(root / ".engineering" / "commands.json", errors)
    e2e = load_json(root / ".engineering" / "e2e.json", errors)

    velocity = commands.get("development_velocity")
    if not isinstance(velocity, dict):
        errors.append("commands.json development_velocity must be an object")
        velocity = {}

    integration = velocity.get("integration")
    if not isinstance(integration, dict):
        errors.append("commands.json development_velocity.integration must be an object")
        integration = {}
    expect(integration, "automated_e2e_required_when_affected", True, errors, "development_velocity.integration")
    expect(integration, "real_environment_blocking", False, errors, "development_velocity.integration")
    expect(integration, "real_environment_deferred_to_release", True, errors, "development_velocity.integration")

    release = velocity.get("release")
    if not isinstance(release, dict):
        errors.append("commands.json development_velocity.release must be an object")
        release = {}
    expect(release, "required_real_environment_blocking", True, errors, "development_velocity.release")

    stage_policy = e2e.get("stage_policy")
    if not isinstance(stage_policy, dict):
        errors.append("e2e.json stage_policy must be an object")
        stage_policy = {}

    e2e_integration = stage_policy.get("integration")
    if not isinstance(e2e_integration, dict):
        errors.append("e2e.json stage_policy.integration must be an object")
        e2e_integration = {}
    expect(e2e_integration, "automated_e2e_before_shared_integration", True, errors, "stage_policy.integration")
    expect(e2e_integration, "real_environment_blocking", False, errors, "stage_policy.integration")
    expect(e2e_integration, "real_environment_deferred_to_release", True, errors, "stage_policy.integration")
    expect(e2e_integration, "material_ui_journey_minimum_evidence_mode", "full_media", errors, "stage_policy.integration")
    expect(e2e_integration, "incidental_ui_may_use_assertions", True, errors, "stage_policy.integration")

    e2e_release = stage_policy.get("release")
    if not isinstance(e2e_release, dict):
        errors.append("e2e.json stage_policy.release must be an object")
        e2e_release = {}
    expect(e2e_release, "full_validation_required", True, errors, "stage_policy.release")
    expect(e2e_release, "release_critical_e2e_required", True, errors, "stage_policy.release")
    expect(e2e_release, "required_real_environment_blocking", True, errors, "stage_policy.release")

    ui_evidence = e2e.get("ui_evidence")
    if not isinstance(ui_evidence, dict):
        errors.append("e2e.json ui_evidence must be an object")
        ui_evidence = {}
    modes = ui_evidence.get("modes") or []
    if "full_media" not in modes:
        errors.append("e2e.json ui_evidence.modes must include full_media")
    triggers = set(ui_evidence.get("full_media_triggers") or [])
    if "material_ui_integration_outcome" not in triggers:
        errors.append("e2e.json full_media_triggers must include material_ui_integration_outcome")

    print("Stage environment policy check")
    print(f"root: {root}")
    for error in errors:
        print(f"FAIL: {error}")
    if errors:
        print(f"RESULT: FAIL ({len(errors)} error(s))")
        return 1
    print("RESULT: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())

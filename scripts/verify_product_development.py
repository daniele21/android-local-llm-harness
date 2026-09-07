#!/usr/bin/env python3
"""Zero-dependency structural checks for product-development routing."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

DEPTHS = [
    "product_none",
    "product_local",
    "product_feature",
    "product_strategic",
]
RISKS = ["value", "usability", "feasibility", "viability"]
SUCCESS = ["acceptance", "outcome", "product_impact"]
PLACEHOLDERS = ("<REPLACE_WITH_", "<PROJECT_NAME>")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--template-mode", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = Path(args.root).resolve()
    errors: list[str] = []

    config_path = root / ".engineering/product.json"
    if not config_path.is_file():
        errors.append("missing .engineering/product.json")
        config = {}
    else:
        try:
            config = json.loads(config_path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError) as exc:
            errors.append(f"invalid product.json: {exc}")
            config = {}

    if config:
        if config.get("schema_version") != 1:
            errors.append("product.json schema_version must be 1")
        if not isinstance(config.get("applicable"), bool):
            errors.append("product.json applicable must be boolean")
        if config.get("change_depths") != DEPTHS:
            errors.append(f"product.json change_depths must equal {DEPTHS}")
        if config.get("default_depth") not in DEPTHS:
            errors.append("product.json default_depth must be a declared product depth")
        if config.get("risk_dimensions") != RISKS:
            errors.append(f"product.json risk_dimensions must equal {RISKS}")
        if config.get("success_layers") != SUCCESS:
            errors.append(f"product.json success_layers must equal {SUCCESS}")

        principles = config.get("principles")
        required_principles = {
            "outcomes_before_output",
            "evidence_before_commitment",
            "proportional_product_reasoning",
            "smallest_sufficient_solution_preferred",
            "discovery_may_conclude_do_not_build",
            "shipping_is_not_product_success",
        }
        if not isinstance(principles, dict):
            errors.append("product.json principles must be an object")
        else:
            for key in required_principles:
                if principles.get(key) is not True:
                    errors.append(f"product.json principles.{key} must be true")

        if config.get("applicable") is True:
            source = config.get("strategy_source")
            if not isinstance(source, str) or not source:
                errors.append("applicable product.json requires strategy_source")
            else:
                source_path = root / source
                if not source_path.is_file():
                    errors.append(f"missing product strategy source: {source}")
                elif not args.template_mode:
                    text = source_path.read_text(encoding="utf-8")
                    for marker in PLACEHOLDERS:
                        if marker in text:
                            errors.append(f"unresolved product placeholder {marker} in {source}")

            shaping = config.get("handoffs", {}).get("product_shaping_skill")
            if not isinstance(shaping, str) or not (root / shaping).is_file():
                errors.append("applicable product.json requires an existing product_shaping_skill")

            post_release = config.get("post_release_learning")
            if not isinstance(post_release, dict) or not isinstance(
                post_release.get("required_when_material"), bool
            ):
                errors.append("post_release_learning.required_when_material must be boolean")

    print("Product development contract check")
    print(f"root: {root}")
    for error in errors:
        print(f"FAIL: {error}")
    if errors:
        print(f"RESULT: FAIL ({len(errors)} error(s))")
        return 1
    applicable = config.get("applicable") if isinstance(config, dict) else None
    print(f"RESULT: PASS (applicable={applicable})")
    return 0


if __name__ == "__main__":
    sys.exit(main())

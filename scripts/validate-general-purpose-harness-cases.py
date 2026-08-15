#!/usr/bin/env python3
"""Validate Harness-owned General Purpose v1 authoring fragments."""

from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "docs" / "model-evaluation" / "general-purpose-v1"
STRUCTURED = BASE / "harness-structured-output.jsonl"
CONTEXT = BASE / "harness-context-retrieval.jsonl"


def load_jsonl(path: Path) -> list[dict]:
    raw = path.read_bytes()
    if raw.startswith(b"\xef\xbb\xbf"):
        raise AssertionError(f"{path} must not contain a UTF-8 BOM")
    if b"\r" in raw:
        raise AssertionError(f"{path} must use LF line endings")
    if not raw.endswith(b"\n"):
        raise AssertionError(f"{path} must end with LF")
    lines = raw.decode("utf-8").splitlines()
    if any(not line for line in lines):
        raise AssertionError(f"{path} must not contain blank JSONL records")
    return [json.loads(line) for line in lines]


def require_common(record: dict, prefix: str, category: str) -> None:
    expected_fields = {
        "schemaVersion",
        "id",
        "categoryId",
        "messages",
        "expected",
        "evaluator",
        "output",
        "metadata",
    }
    if set(record) != expected_fields:
        raise AssertionError(f"{record.get('id')} has unexpected field vocabulary")
    if record["schemaVersion"] != 1:
        raise AssertionError(f"{record['id']} must use case schema v1")
    if not record["id"].startswith(prefix):
        raise AssertionError(f"{record['id']} has wrong Harness case prefix")
    if record["categoryId"] != category:
        raise AssertionError(f"{record['id']} has wrong category")
    if record["metadata"].get("sourceFamily") != "harness-synthetic":
        raise AssertionError(f"{record['id']} must be labelled Harness synthetic")
    roles = [message.get("role") for message in record["messages"]]
    if "USER" not in roles:
        raise AssertionError(f"{record['id']} must contain a user message")


def validate_structured(records: list[dict]) -> None:
    if len(records) != 20:
        raise AssertionError("GP-05 must contain exactly 20 cases")
    for record in records:
        require_common(record, "gp-structured-", "structured-output")
        if record["expected"].get("kind") != "JSON":
            raise AssertionError(f"{record['id']} must use JSON expected answer")
        expected = json.loads(record["expected"]["value"])
        if not isinstance(expected, dict):
            raise AssertionError(f"{record['id']} expected JSON must be an object")
        evaluator = record["evaluator"]
        if evaluator.get("type") != "JSON_FIELDS" or evaluator.get("version") != 1:
            raise AssertionError(f"{record['id']} must use JSON_FIELDS v1")
        required = evaluator.get("parameters", {}).get("required_fields", "").split(",")
        if not required or any(not field for field in required):
            raise AssertionError(f"{record['id']} must declare required JSON fields")
        if any(field not in expected for field in required):
            raise AssertionError(f"{record['id']} required field missing from expected JSON")
        if record["output"].get("responseFormat") != "JSON":
            raise AssertionError(f"{record['id']} must request JSON output")


def validate_context(records: list[dict]) -> None:
    if len(records) != 20:
        raise AssertionError("GP-06 must contain exactly 20 cases")
    positions: Counter[str] = Counter()
    for record in records:
        require_common(record, "gp-context-", "context-retrieval")
        if record["expected"].get("kind") != "TEXT":
            raise AssertionError(f"{record['id']} must use TEXT expected answer")
        evaluator = record["evaluator"]
        expected_evaluator = {
            "type": "EXACT_MATCH",
            "version": 1,
            "parameters": {"case": "sensitive", "whitespace": "trim"},
        }
        if evaluator != expected_evaluator:
            raise AssertionError(f"{record['id']} must use deterministic exact-match v1")
        user_text = "\n".join(
            message["content"] for message in record["messages"] if message.get("role") == "USER"
        )
        answer = record["expected"]["value"]
        if answer not in user_text:
            raise AssertionError(f"{record['id']} expected answer must be present in provided context")
        position = record["metadata"].get("targetPosition")
        if position not in {"beginning", "middle", "end"}:
            raise AssertionError(f"{record['id']} has invalid targetPosition")
        positions[position] += 1
        if record["output"].get("responseFormat") != "TEXT":
            raise AssertionError(f"{record['id']} must request text output")
    if positions != Counter({"beginning": 7, "middle": 6, "end": 7}):
        raise AssertionError(f"Unexpected retrieval target-position distribution: {dict(positions)}")


def main() -> int:
    structured = load_jsonl(STRUCTURED)
    context = load_jsonl(CONTEXT)
    all_ids = [record["id"] for record in structured + context]
    if len(set(all_ids)) != 40:
        raise AssertionError("Harness-owned General Purpose case IDs must be globally unique")
    validate_structured(structured)
    validate_context(context)
    print("Harness-owned General Purpose v1 cases validated: 20 structured + 20 context retrieval")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

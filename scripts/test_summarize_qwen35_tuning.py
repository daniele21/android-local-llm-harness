#!/usr/bin/env python3
"""Regression coverage for Qwen3.5 tuning evidence schema compatibility."""

from __future__ import annotations

import csv
import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SUMMARIZER = ROOT / "summarize-qwen35-tuning.py"


def base_record(schema_version: int, sample_index: int) -> dict[str, object]:
    record: dict[str, object] = {
        "schemaVersion": schema_version,
        "tuningCaseId": f"schema-v{schema_version}",
        "sampleIndex": sample_index,
        "warmRepetitionsRequested": 3,
        "maxOutputTokens": 64,
        "modelDigest": "a" * 64,
        "modelTier": "B0_8",
        "architecture": "qwen35",
        "quantization": "Q4_K_M",
        "backendRevision": "b" * 40,
        "harnessCommit": "c" * 40,
        "runtimeProfileId": "runtime-test",
        "runtimeProfileVersion": "1",
        "generationProfileId": "generation-test",
        "generationProfileVersion": "1",
        "contextTokens": 2048,
        "cpuThreads": 4,
        "batchThreads": 4,
        "batchSize": 128,
        "microBatchSize": 64,
        "thinkingMode": "DISABLED",
        "deviceModel": "test-device",
        "androidRelease": "16",
        "sdkInt": 36,
        "abi": "arm64-v8a",
        "modelLoadKind": "COLD" if sample_index == 0 else "WARM",
        "stopReason": "MAX_TOKENS",
        "processPssKb": 100000 + sample_index,
        "availableMemoryBytes": 2_000_000_000 - sample_index,
        "thermalStatus": 0,
        "ttftMs": 100.0 + sample_index,
        "prefillMs": 90.0 + sample_index,
        "decodeMs": 200.0 + sample_index,
        "totalMs": 300.0 + sample_index,
        "prefillTokensPerSecond": 10.0,
        "decodeTokensPerSecond": 20.0,
    }
    if schema_version >= 4:
        record["promptDigest"] = "d" * 64
    if schema_version >= 5:
        record.update(
            {
                "outputDigest": "e" * 64,
                "flashAttention": False,
                "kvCacheTypeK": "DEFAULT",
                "kvCacheTypeV": "DEFAULT",
                "seedPolicy": "FIXED",
                "generationSeed": 42,
            }
        )
    return record


def run_summary(records: list[dict[str, object]]) -> tuple[subprocess.CompletedProcess[str], list[dict[str, str]]]:
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        source = root / "evidence.jsonl"
        output = root / "summary.csv"
        source.write_text("\n".join(json.dumps(record) for record in records) + "\n", encoding="utf-8")
        result = subprocess.run(
            [sys.executable, str(SUMMARIZER), str(source), str(output)],
            capture_output=True,
            check=False,
            text=True,
        )
        rows: list[dict[str, str]] = []
        if output.exists():
            with output.open(newline="", encoding="utf-8") as handle:
                rows = list(csv.DictReader(handle))
        return result, rows


def assert_schema_supported(schema_version: int) -> None:
    result, rows = run_summary([base_record(schema_version, index) for index in range(4)])
    assert result.returncode == 0, result.stderr
    assert len(rows) == 1
    assert rows[0]["schemaVersion"] == str(schema_version)
    assert rows[0]["eligibleForProfileSelection"] == "True"
    if schema_version == 5:
        assert rows[0]["outputDigestStable"] == "True"
        assert rows[0]["stableOutputDigest"] == "e" * 64


def assert_v5_requires_output_digest() -> None:
    records = [base_record(5, index) for index in range(4)]
    records[0].pop("outputDigest")
    result, _ = run_summary(records)
    assert result.returncode != 0
    assert "requires 64-char outputDigest" in result.stderr


def main() -> int:
    assert_schema_supported(3)
    assert_schema_supported(4)
    assert_schema_supported(5)
    assert_v5_requires_output_digest()
    print("Qwen3.5 tuning summarizer schema compatibility tests passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

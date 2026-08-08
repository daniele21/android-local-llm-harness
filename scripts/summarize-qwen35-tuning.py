#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import statistics
from collections import defaultdict
from pathlib import Path

IDENTITY_FIELDS = [
    "tuningCaseId",
    "modelDigest",
    "modelTier",
    "architecture",
    "quantization",
    "backendRevision",
    "harnessCommit",
    "runtimeProfileId",
    "runtimeProfileVersion",
    "generationProfileId",
    "generationProfileVersion",
    "contextTokens",
    "cpuThreads",
    "batchThreads",
    "batchSize",
    "microBatchSize",
    "thinkingMode",
    "modelLoadKind",
    "deviceModel",
    "androidRelease",
    "sdkInt",
    "abi",
]
METRIC_FIELDS = [
    "ttftMs",
    "prefillMs",
    "decodeMs",
    "totalMs",
    "prefillTokensPerSecond",
    "decodeTokensPerSecond",
    "processPssKb",
    "availableMemoryBytes",
    "thermalStatus",
]


def median(records: list[dict[str, object]], field: str) -> float | None:
    values = [float(record[field]) for record in records if record.get(field) is not None]
    return statistics.median(values) if values else None


def main() -> int:
    parser = argparse.ArgumentParser(description="Summarize privacy-safe Qwen3.5 device tuning evidence")
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    groups: dict[tuple[str, str], list[dict[str, object]]] = defaultdict(list)
    for line in args.input.read_text().splitlines():
        if not line.strip():
            continue
        record = json.loads(line)
        key = (record["tuningCaseId"], record["modelLoadKind"])
        groups[key].append(record)

    rows: list[dict[str, object]] = []
    for _, records in sorted(groups.items()):
        first = records[0]
        for field in IDENTITY_FIELDS:
            if any(record.get(field) != first.get(field) for record in records):
                raise SystemExit(f"identity drift for {first['tuningCaseId']}: {field}")
        row = {field: first.get(field) for field in IDENTITY_FIELDS}
        row["samples"] = len(records)
        for field in METRIC_FIELDS:
            row[f"median_{field}"] = median(records, field)
        rows.append(row)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = IDENTITY_FIELDS + ["samples"] + [f"median_{field}" for field in METRIC_FIELDS]
    with args.output.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {len(rows)} Qwen3.5 tuning summaries to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

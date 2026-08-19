#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import math
import statistics
from collections import defaultdict
from pathlib import Path

SCHEMA_VERSION = 2
MIN_WARM_SAMPLES = 3
IDENTITY_FIELDS = [
    "tuningCaseId",
    "warmRepetitionsRequested",
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
    "deviceModel",
    "androidRelease",
    "sdkInt",
    "abi",
]
NUMERIC_METRICS = [
    "ttftMs",
    "prefillMs",
    "decodeMs",
    "totalMs",
    "prefillTokensPerSecond",
    "decodeTokensPerSecond",
]
SUSTAINED_METRICS = [
    "ttftMs",
    "totalMs",
    "decodeTokensPerSecond",
]
REQUIRED_FIELDS = set(
    ["schemaVersion", "sampleIndex", "modelLoadKind", "stopReason", "processPssKb", "availableMemoryBytes", "thermalStatus"]
    + IDENTITY_FIELDS
    + NUMERIC_METRICS
)


def numeric_values(records: list[dict[str, object]], field: str) -> list[float]:
    return [float(record[field]) for record in records if record.get(field) is not None]


def median(records: list[dict[str, object]], field: str) -> float | None:
    values = numeric_values(records, field)
    return statistics.median(values) if values else None


def p95(records: list[dict[str, object]], field: str) -> float | None:
    values = sorted(numeric_values(records, field))
    if not values:
        return None
    index = max(0, math.ceil(0.95 * len(values)) - 1)
    return values[index]


def max_numeric(records: list[dict[str, object]], field: str) -> float | None:
    values = numeric_values(records, field)
    return max(values) if values else None


def min_numeric(records: list[dict[str, object]], field: str) -> float | None:
    values = numeric_values(records, field)
    return min(values) if values else None


def first_last_drift(records: list[dict[str, object]], field: str) -> tuple[float | None, float | None, float | None]:
    ordered = sorted(records, key=lambda record: int(record["sampleIndex"]))
    values = [(float(record[field]), int(record["sampleIndex"])) for record in ordered if record.get(field) is not None]
    if not values:
        return None, None, None
    first = values[0][0]
    last = values[-1][0]
    drift_percent = None if first == 0 else (last - first) * 100.0 / first
    return first, last, drift_percent


def read_records(path: Path) -> list[dict[str, object]]:
    records: list[dict[str, object]] = []
    for line_number, line in enumerate(path.read_text().splitlines(), start=1):
        if not line.strip():
            continue
        record = json.loads(line)
        missing = sorted(REQUIRED_FIELDS.difference(record))
        if missing:
            raise SystemExit(f"line {line_number}: missing evidence fields: {', '.join(missing)}")
        if record["schemaVersion"] != SCHEMA_VERSION:
            raise SystemExit(f"line {line_number}: expected schemaVersion {SCHEMA_VERSION}")
        if record["modelLoadKind"] not in {"COLD", "WARM"}:
            raise SystemExit(f"line {line_number}: modelLoadKind must be COLD or WARM")
        records.append(record)
    if not records:
        raise SystemExit("No Qwen3.5 tuning evidence found")
    return records


def verify_identity(records: list[dict[str, object]]) -> None:
    first = records[0]
    for field in IDENTITY_FIELDS:
        if any(record.get(field) != first.get(field) for record in records):
            raise SystemExit(f"identity drift for {first['tuningCaseId']}: {field}")


def eligibility(cold: list[dict[str, object]], warm: list[dict[str, object]]) -> tuple[bool, str]:
    requested = int((cold or warm)[0]["warmRepetitionsRequested"])
    if len(cold) != 1:
        return False, f"expected exactly 1 cold sample, got {len(cold)}"
    if requested < MIN_WARM_SAMPLES:
        return False, f"warmRepetitionsRequested must be >= {MIN_WARM_SAMPLES}"
    if len(warm) != requested:
        return False, f"expected {requested} warm samples, got {len(warm)}"
    expected_indexes = list(range(0, requested + 1))
    actual_indexes = sorted(int(record["sampleIndex"]) for record in cold + warm)
    if actual_indexes != expected_indexes:
        return False, f"sample indexes must be {expected_indexes}, got {actual_indexes}"
    stop_reasons = {str(record["stopReason"]).upper() for record in cold + warm}
    unsafe_markers = ("GUARD", "CANCEL", "UNKNOWN", "BUDGET")
    if any(any(marker in reason for marker in unsafe_markers) for reason in stop_reasons):
        return False, f"non-comparable stop reason: {','.join(sorted(stop_reasons))}"
    return True, "complete comparable evidence"


def summarize_case(records: list[dict[str, object]]) -> dict[str, object]:
    verify_identity(records)
    first = records[0]
    cold = [record for record in records if record["modelLoadKind"] == "COLD"]
    warm = [record for record in records if record["modelLoadKind"] == "WARM"]
    eligible, reason = eligibility(cold, warm)
    row: dict[str, object] = {field: first.get(field) for field in IDENTITY_FIELDS}
    row["coldSamples"] = len(cold)
    row["warmSamples"] = len(warm)
    for field in NUMERIC_METRICS:
        row[f"cold_{field}"] = median(cold, field)
        row[f"warm_median_{field}"] = median(warm, field)
        row[f"warm_p95_{field}"] = p95(warm, field)
    for field in SUSTAINED_METRICS:
        first_value, last_value, drift_percent = first_last_drift(warm, field)
        row[f"warm_first_{field}"] = first_value
        row[f"warm_last_{field}"] = last_value
        row[f"warm_driftPercent_{field}"] = drift_percent
    ordered_warm = sorted(warm, key=lambda record: int(record["sampleIndex"]))
    row["warm_first_thermalStatus"] = float(ordered_warm[0]["thermalStatus"]) if ordered_warm else None
    row["warm_last_thermalStatus"] = float(ordered_warm[-1]["thermalStatus"]) if ordered_warm else None
    row["warm_thermalStatusDelta"] = (
        row["warm_last_thermalStatus"] - row["warm_first_thermalStatus"]
        if row["warm_first_thermalStatus"] is not None and row["warm_last_thermalStatus"] is not None
        else None
    )
    row["peak_processPssKb"] = max_numeric(records, "processPssKb")
    row["min_availableMemoryBytes"] = min_numeric(records, "availableMemoryBytes")
    row["max_thermalStatus"] = max_numeric(records, "thermalStatus")
    row["stopReasons"] = ",".join(sorted({str(record["stopReason"]) for record in records}))
    row["eligibleForProfileSelection"] = eligible
    row["eligibilityReason"] = reason
    return row


def main() -> int:
    parser = argparse.ArgumentParser(description="Summarize privacy-safe Qwen3.5 physical-device tuning evidence")
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    groups: dict[str, list[dict[str, object]]] = defaultdict(list)
    for record in read_records(args.input):
        groups[str(record["tuningCaseId"])].append(record)

    rows = [summarize_case(records) for _, records in sorted(groups.items())]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    metric_columns: list[str] = []
    for field in NUMERIC_METRICS:
        metric_columns.extend([f"cold_{field}", f"warm_median_{field}", f"warm_p95_{field}"])
    sustained_columns: list[str] = []
    for field in SUSTAINED_METRICS:
        sustained_columns.extend([f"warm_first_{field}", f"warm_last_{field}", f"warm_driftPercent_{field}"])
    fieldnames = IDENTITY_FIELDS + [
        "coldSamples",
        "warmSamples",
        *metric_columns,
        *sustained_columns,
        "warm_first_thermalStatus",
        "warm_last_thermalStatus",
        "warm_thermalStatusDelta",
        "peak_processPssKb",
        "min_availableMemoryBytes",
        "max_thermalStatus",
        "stopReasons",
        "eligibleForProfileSelection",
        "eligibilityReason",
    ]
    with args.output.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    eligible = sum(1 for row in rows if row["eligibleForProfileSelection"])
    print(f"Wrote {len(rows)} Qwen3.5 tuning cases to {args.output}; {eligible} are evidence-eligible")
    print("No runtime profile was promoted automatically. MEASURED selection remains an explicit evidence review step.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

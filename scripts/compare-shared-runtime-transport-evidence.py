#!/usr/bin/env python3
"""Compare SR-6 Binder timing evidence with matching in-process Qwen3.5 evidence.

The comparator intentionally does not invent a performance threshold. It validates that the
records are comparable enough for SR-VAL-09 (same model digest, context, thinking mode and
explicit tuning case), then emits a small privacy-safe JSON summary for review.
"""

from __future__ import annotations

import argparse
import json
import statistics
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

BINDER_IDENTITY_PREFIX = "SR6_SHARED_RUNTIME identity "
BINDER_PROFILE_PREFIX = "SR6_SHARED_RUNTIME generationProfile "
BINDER_GENERATION_PREFIX = "SR6_SHARED_RUNTIME generation "
IN_PROCESS_PREFIX = "LOCAL_LLM_TUNING_JSON "
DEFAULT_TUNING_CASE_ID = "sr6-transport-v1"


class EvidenceError(ValueError):
    """Raised when evidence is missing, malformed or not comparable."""


@dataclass(frozen=True)
class BinderEvidence:
    model_digest: str
    context_tokens: int
    max_output_tokens: int
    thinking_mode: str
    ttft_ms: float
    core_total_ms: float
    client_observed_total_ms: float
    transport_envelope_ms: float
    output_tokens: int
    decode_tokens_per_second: float


def parse_key_values(line: str, prefix: str) -> dict[str, str]:
    if not line.startswith(prefix):
        raise EvidenceError(f"Expected line prefix: {prefix}")
    values: dict[str, str] = {}
    for token in line[len(prefix) :].strip().split():
        if "=" not in token:
            continue
        key, value = token.split("=", 1)
        values[key] = value
    return values


def require_value(values: dict[str, str], key: str, source: str) -> str:
    value = values.get(key)
    if value is None or value == "":
        raise EvidenceError(f"Missing {key} in {source}")
    return value


def last_prefixed(lines: Iterable[str], prefix: str) -> str:
    matches = [line.strip() for line in lines if line.strip().startswith(prefix)]
    if not matches:
        raise EvidenceError(f"Missing evidence marker: {prefix.strip()}")
    return matches[-1]


def parse_binder_log(path: Path) -> BinderEvidence:
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    identity = parse_key_values(last_prefixed(lines, BINDER_IDENTITY_PREFIX), BINDER_IDENTITY_PREFIX)
    profile = parse_key_values(last_prefixed(lines, BINDER_PROFILE_PREFIX), BINDER_PROFILE_PREFIX)
    generation = parse_key_values(last_prefixed(lines, BINDER_GENERATION_PREFIX), BINDER_GENERATION_PREFIX)

    try:
        return BinderEvidence(
            model_digest=require_value(identity, "modelDigestSha256", "Binder identity"),
            context_tokens=int(require_value(profile, "contextSize", "Binder profile")),
            max_output_tokens=int(require_value(profile, "maxOutputTokens", "Binder profile")),
            thinking_mode=require_value(profile, "thinkingMode", "Binder profile"),
            ttft_ms=float(require_value(generation, "ttftMs", "Binder generation")),
            core_total_ms=float(require_value(generation, "coreTotalMs", "Binder generation")),
            client_observed_total_ms=float(
                require_value(generation, "clientObservedTotalMs", "Binder generation")
            ),
            transport_envelope_ms=float(
                require_value(generation, "transportEnvelopeMs", "Binder generation")
            ),
            output_tokens=int(require_value(generation, "outputTokens", "Binder generation")),
            decode_tokens_per_second=float(
                require_value(generation, "decodeTokensPerSecond", "Binder generation")
            ),
        )
    except ValueError as error:
        raise EvidenceError(f"Invalid numeric Binder evidence: {error}") from error


def parse_in_process_log(path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        marker = line.find(IN_PROCESS_PREFIX)
        if marker < 0:
            continue
        raw = line[marker + len(IN_PROCESS_PREFIX) :].strip()
        try:
            value = json.loads(raw)
        except json.JSONDecodeError as error:
            raise EvidenceError(f"Malformed in-process JSON evidence: {error}") from error
        if not isinstance(value, dict):
            raise EvidenceError("In-process evidence marker must contain a JSON object")
        records.append(value)
    if not records:
        raise EvidenceError(f"Missing evidence marker: {IN_PROCESS_PREFIX.strip()}")
    return records


def require_record_value(record: dict[str, Any], key: str) -> Any:
    value = record.get(key)
    if value is None:
        raise EvidenceError(f"Missing {key} in in-process evidence")
    return value


def matching_warm_records(
    records: Iterable[dict[str, Any]],
    binder: BinderEvidence,
    tuning_case_id: str,
) -> list[dict[str, Any]]:
    matching: list[dict[str, Any]] = []
    for record in records:
        if str(record.get("modelLoadKind", "")).upper() != "WARM":
            continue
        if str(record.get("modelDigest", "")) != binder.model_digest:
            continue
        if int(record.get("contextTokens", -1)) != binder.context_tokens:
            continue
        if str(record.get("thinkingMode", "")) != binder.thinking_mode:
            continue
        if str(record.get("tuningCaseId", "")) != tuning_case_id:
            continue
        matching.append(record)
    if not matching:
        raise EvidenceError(
            "No comparable WARM in-process sample. Require same modelDigest, contextTokens, "
            f"thinkingMode and tuningCaseId={tuning_case_id}."
        )
    return matching


def numeric_series(records: Iterable[dict[str, Any]], key: str) -> list[float]:
    values: list[float] = []
    for record in records:
        value = require_record_value(record, key)
        try:
            values.append(float(value))
        except (TypeError, ValueError) as error:
            raise EvidenceError(f"Invalid numeric {key} in in-process evidence: {value}") from error
    return values


def median(records: Iterable[dict[str, Any]], key: str) -> float:
    return float(statistics.median(numeric_series(records, key)))


def percent(numerator: float, denominator: float) -> float | None:
    if denominator <= 0:
        return None
    return numerator * 100.0 / denominator


def build_summary(
    binder: BinderEvidence,
    warm_records: list[dict[str, Any]],
    tuning_case_id: str,
) -> dict[str, Any]:
    in_process_total_ms = median(warm_records, "totalMs")
    in_process_ttft_ms = median(warm_records, "ttftMs")
    in_process_decode_tps = median(warm_records, "decodeTokensPerSecond")

    envelope_from_totals = max(0.0, binder.client_observed_total_ms - binder.core_total_ms)
    envelope_consistent = abs(envelope_from_totals - binder.transport_envelope_ms) <= 1.0
    if not envelope_consistent:
        raise EvidenceError(
            "Binder transportEnvelopeMs is inconsistent with clientObservedTotalMs - coreTotalMs"
        )

    return {
        "schemaVersion": 1,
        "status": "COMPARABLE",
        "comparisonPolicy": "measurement-only-no-pass-threshold",
        "identity": {
            "modelDigestSha256": binder.model_digest,
            "contextTokens": binder.context_tokens,
            "thinkingMode": binder.thinking_mode,
            "binderMaxOutputTokens": binder.max_output_tokens,
            "tuningCaseId": tuning_case_id,
            "warmSampleCount": len(warm_records),
        },
        "binder": {
            "ttftMs": binder.ttft_ms,
            "coreTotalMs": binder.core_total_ms,
            "clientObservedTotalMs": binder.client_observed_total_ms,
            "transportEnvelopeMs": binder.transport_envelope_ms,
            "transportEnvelopePctOfCore": percent(
                binder.transport_envelope_ms, binder.core_total_ms
            ),
            "outputTokens": binder.output_tokens,
            "decodeTokensPerSecond": binder.decode_tokens_per_second,
        },
        "inProcessWarmMedian": {
            "ttftMs": in_process_ttft_ms,
            "totalMs": in_process_total_ms,
            "decodeTokensPerSecond": in_process_decode_tps,
        },
        "comparison": {
            "binderCoreVsInProcessMedianDeltaMs": binder.core_total_ms - in_process_total_ms,
            "binderClientVsInProcessMedianDeltaMs": (
                binder.client_observed_total_ms - in_process_total_ms
            ),
            "binderClientVsInProcessMedianPct": percent(
                binder.client_observed_total_ms - in_process_total_ms,
                in_process_total_ms,
            ),
            "transportEnvelopeInternallyConsistent": envelope_consistent,
        },
        "privacy": {
            "promptPersisted": False,
            "outputPersisted": False,
            "modelPathPersisted": False,
        },
    }


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compare SR-6 Binder timing with matched in-process Qwen3.5 evidence."
    )
    parser.add_argument("--binder-log", required=True, type=Path)
    parser.add_argument("--in-process-log", required=True, type=Path)
    parser.add_argument(
        "--tuning-case-id",
        default=DEFAULT_TUNING_CASE_ID,
        help="Required tuningCaseId for the in-process evidence.",
    )
    parser.add_argument("--output", type=Path, help="Optional JSON output file.")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])
    try:
        binder = parse_binder_log(args.binder_log)
        records = parse_in_process_log(args.in_process_log)
        warm_records = matching_warm_records(records, binder, args.tuning_case_id)
        summary = build_summary(binder, warm_records, args.tuning_case_id)
    except (OSError, EvidenceError) as error:
        print(f"SR6 transport evidence comparison failed: {error}", file=sys.stderr)
        return 1

    rendered = json.dumps(summary, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

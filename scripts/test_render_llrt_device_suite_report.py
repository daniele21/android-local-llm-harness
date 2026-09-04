#!/usr/bin/env python3
from __future__ import annotations

import csv
import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RENDERER = ROOT / "scripts" / "render-llrt-device-suite-report.py"


def main() -> int:
    with tempfile.TemporaryDirectory() as tmp_raw:
        run_dir = Path(tmp_raw)
        batch_root = run_dir / "evidence" / "0.8b" / "batch" / "llrt9" / "0.8b"
        kv_root = run_dir / "evidence" / "0.8b" / "kv" / "llrt6" / "0.8b"
        opencl_root = run_dir / "evidence" / "0.8b" / "opencl"
        batch_root.mkdir(parents=True)
        kv_root.mkdir(parents=True)
        opencl_root.mkdir(parents=True)

        manifest = {
            "schemaVersion": 1,
            "suite": "LLRT_QUICK_PHYSICAL_SCREENING",
            "runId": "fixture-run",
            "startedAtUtc": "2026-08-23T06:00:00+00:00",
            "harnessCommit": "0" * 40,
            "device": {
                "serial": "fixture-device",
                "manufacturer": "Fixture",
                "model": "Phone",
                "soc": "FixtureSoC",
                "androidRelease": "16",
                "sdkInt": 36,
                "abi": "arm64-v8a",
            },
            "profile": {
                "contextTokens": 1024,
                "maxOutputTokens": 8,
                "batchWidth": 2,
                "batchRepetitions": 4,
                "kvRepetitions": 3,
                "kvCases": ["release-default", "k-q8-fa-off"],
                "openClRepetitions": 3,
                "requestedGpuLayers": "1",
                "openClTier": "auto",
                "thinkingMode": "DISABLED",
                "generationSeed": 42,
            },
            "models": {"0.8b": "fixture-0.8b.gguf", "2b": None},
            "evidenceSemantics": "diagnostic-screening-only",
        }
        manifest_path = run_dir / "run.json"
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

        status_path = run_dir / "lane-status.tsv"
        with status_path.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.writer(handle, delimiter="\t")
            writer.writerow(["tier", "lane", "status", "exitCode", "evidenceRoot", "note"])
            writer.writerow(["0.8b", "batch", "PASS", 0, batch_root.parents[1], "completed"])
            writer.writerow(["0.8b", "kv", "PASS", 0, kv_root.parents[1], "completed"])
            writer.writerow(["0.8b", "opencl", "SKIP", 0, opencl_root, "headers not supplied"])

        (batch_root / "llrt9-fixture-summary.csv").write_text(
            "batchWidth,samples,serialFirstSamples,batchFirstSamples,serialMedianMs,batchMedianMs,medianSpeedup,maxObservedPssKb,maxThermalStatus\n"
            "2,4,2,2,1000,800,1.25,1048576,1\n",
            encoding="utf-8",
        )
        (batch_root / "llrt9-fixture-evidence.jsonl").write_text("", encoding="utf-8")

        (kv_root / "llama-cpp-kv-cache-fixture-summary.csv").write_text(
            "tuningCaseId,warm_median_totalMs,peak_processPssKb,max_thermalStatus,outputDigestStable,stableOutputDigest\n"
            "llrt6-0.8b-ctx1024-release-default-t2,900,1000000,1,True,abc\n"
            "llrt6-0.8b-ctx1024-k-q8-fa-off-t2,850,900000,1,True,abc\n",
            encoding="utf-8",
        )
        (kv_root / "llama-cpp-kv-cache-fixture-evidence.jsonl").write_text("", encoding="utf-8")

        report_md = run_dir / "report.md"
        report_html = run_dir / "report.html"
        subprocess.run(
            [
                "python3",
                str(RENDERER),
                str(manifest_path),
                str(status_path),
                str(run_dir),
                str(report_md),
                str(report_html),
            ],
            check=True,
        )

        md = report_md.read_text(encoding="utf-8")
        rendered_html = report_html.read_text(encoding="utf-8")
        assert "PASS_WITH_SKIPS" in md
        assert "1.250×" in md
        assert "-5.6%" in md
        assert "Diagnostic screening only" in rendered_html
        assert "llrt9-fixture-summary.csv" in rendered_html
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

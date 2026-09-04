#!/usr/bin/env python3
"""Render Markdown and HTML reports from LLRT quick-suite privacy-safe evidence."""
from __future__ import annotations

import csv
import html
import json
import math
import os
import sys
from pathlib import Path

manifest_path, status_path, run_dir_raw, md_path_raw, html_path_raw = sys.argv[1:]
run_dir = Path(run_dir_raw)
md_path = Path(md_path_raw)
html_path = Path(html_path_raw)
manifest = json.loads(Path(manifest_path).read_text(encoding="utf-8"))

with Path(status_path).open(newline="", encoding="utf-8") as handle:
    statuses = list(csv.DictReader(handle, delimiter="\t"))

def rel(path: Path) -> str:
    try:
        return path.relative_to(run_dir).as_posix()
    except ValueError:
        return os.path.relpath(path, run_dir)

def md_escape(value: object) -> str:
    return str(value).replace("|", "\\|").replace("\n", " ")

def fnum(value: object, digits: int = 2) -> str:
    if value is None or value == "":
        return "—"
    try:
        number = float(value)
    except (TypeError, ValueError):
        return str(value)
    if not math.isfinite(number):
        return "—"
    return f"{number:.{digits}f}"

def mib_from_kb(value: object) -> str:
    if value is None or value == "":
        return "—"
    try:
        return f"{float(value) / 1024.0:.1f}"
    except (TypeError, ValueError):
        return "—"

def pct_delta(value: object, baseline: object) -> str:
    try:
        value_f = float(value)
        baseline_f = float(baseline)
    except (TypeError, ValueError):
        return "—"
    if baseline_f == 0:
        return "—"
    return f"{(value_f - baseline_f) * 100.0 / baseline_f:+.1f}%"

def find_csv(root: Path, lane: str) -> Path | None:
    patterns = {
        "batch": "llrt9-*-summary.csv",
        "kv": "llama-cpp-kv-cache-*-summary.csv",
        "opencl": "llama-cpp-opencl-*-summary.csv",
    }
    matches = sorted(root.rglob(patterns[lane]))
    return matches[-1] if matches else None

def find_jsonl(root: Path, lane: str) -> Path | None:
    patterns = {
        "batch": "llrt9-*-evidence.jsonl",
        "kv": "llama-cpp-kv-cache-*-evidence.jsonl",
        "opencl": "llama-cpp-opencl-*-evidence.jsonl",
    }
    matches = sorted(root.rglob(patterns[lane]))
    return matches[-1] if matches else None

def read_csv(path: Path | None) -> list[dict[str, str]]:
    if path is None or not path.exists():
        return []
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))

def case_label(case_id: str) -> str:
    known = [
        "release-default",
        "k-q8-fa-off",
        "k-q4-fa-off",
        "f16-f16-fa-on",
        "q8-q8-fa-on",
        "q4-q4-fa-on",
    ]
    for label in known:
        if f"-{label}-" in case_id:
            return label
    return case_id

def batch_signal(speedup: object) -> str:
    try:
        value = float(speedup)
    except (TypeError, ValueError):
        return "UNKNOWN"
    if value >= 1.05:
        return "PROMISING"
    if value < 0.95:
        return "REGRESSION"
    return "NEUTRAL"

failed = [row for row in statuses if row["status"] == "FAIL"]
skipped = [row for row in statuses if row["status"] == "SKIP"]
if failed:
    overall = "FAIL"
elif skipped:
    overall = "PASS_WITH_SKIPS"
else:
    overall = "PASS"

device = manifest["device"]
profile = manifest["profile"]
models = manifest["models"]

md: list[str] = []
md.append("# LLRT quick physical-device screening report")
md.append("")
md.append("> **Diagnostic screening only.** This report does not close LLRT-6C, LLRT-7C or LLRT-9C and must not promote runtime defaults. Full qualification remains the canonical 2048-context / 64-output evidence matrix.")
md.append("")
md.append(f"**Overall:** `{overall}`  ")
md.append(f"**Run ID:** `{manifest['runId']}`  ")
md.append(f"**Harness commit:** `{manifest['harnessCommit']}`")
md.append("")
md.append("## Device and profile")
md.append("")
md.append("| Field | Value |")
md.append("| --- | --- |")
device_rows = [
    ("Device", f"{device['manufacturer']} {device['model']}"),
    ("SoC", device["soc"]),
    ("Android", f"{device['androidRelease']} / SDK {device['sdkInt']}"),
    ("ABI", device["abi"]),
    ("ADB serial", device["serial"]),
    ("Context tokens", profile["contextTokens"]),
    ("Max output tokens", profile["maxOutputTokens"]),
    ("Thinking", profile["thinkingMode"]),
    ("Seed", profile["generationSeed"]),
    ("Batch screen", f"width {profile['batchWidth']} × {profile['batchRepetitions']} balanced repetitions"),
    ("KV screen", f"{profile['kvRepetitions']} warm repetitions; {', '.join(profile['kvCases'])}"),
    ("OpenCL screen", f"{profile['openClRepetitions']} warm repetitions; requested gpuLayers={profile['requestedGpuLayers']}"),
    ("0.8B model", models.get("0.8b") or "not supplied"),
    ("2B model", models.get("2b") or "not supplied"),
]
for key, value in device_rows:
    md.append(f"| {md_escape(key)} | {md_escape(value)} |")

md.append("")
md.append("## Lane status")
md.append("")
md.append("| Tier | Lane | Status | Note | Evidence |")
md.append("| --- | --- | --- | --- | --- |")
for row in statuses:
    root = Path(row["evidenceRoot"])
    csv_path = find_csv(root, row["lane"])
    jsonl_path = find_jsonl(root, row["lane"])
    links = []
    if csv_path:
        links.append(f"[CSV]({rel(csv_path)})")
    if jsonl_path:
        links.append(f"[JSONL]({rel(jsonl_path)})")
    md.append(
        f"| {md_escape(row['tier'])} | {md_escape(row['lane'])} | `{row['status']}` | "
        f"{md_escape(row['note'])} | {' · '.join(links) if links else '—'} |"
    )

for row in statuses:
    if row["status"] != "PASS":
        continue
    tier = row["tier"]
    lane = row["lane"]
    root = Path(row["evidenceRoot"])
    csv_path = find_csv(root, lane)
    records = read_csv(csv_path)
    if not records:
        continue

    md.append("")
    md.append(f"## {tier} · {lane}")
    md.append("")

    if lane == "batch":
        md.append("| Width | Samples | Serial median ms | Native batch median ms | Speedup | Max observed PSS MiB | Max thermal | Signal |")
        md.append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |")
        for item in records:
            md.append(
                f"| {item.get('batchWidth','—')} | {item.get('samples','—')} | "
                f"{fnum(item.get('serialMedianMs'))} | {fnum(item.get('batchMedianMs'))} | "
                f"{fnum(item.get('medianSpeedup'), 3)}× | {mib_from_kb(item.get('maxObservedPssKb'))} | "
                f"{fnum(item.get('maxThermalStatus'), 0)} | `{batch_signal(item.get('medianSpeedup'))}` |"
            )
        md.append("")
        md.append("`PASS` means the canonical fixed-seed serial/native digest and per-case token equality gates passed. The signal is only a quick performance screen.")

    elif lane == "kv":
        baseline = next((item for item in records if case_label(item.get("tuningCaseId", "")) == "release-default"), None)
        md.append("| Case | Warm median total ms | Δ latency vs default | Peak PSS MiB | Δ PSS vs default | Max thermal | Digest stable | Same digest as default |")
        md.append("| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |")
        for item in records:
            label = case_label(item.get("tuningCaseId", ""))
            same_digest = "—"
            if baseline and item.get("stableOutputDigest") and baseline.get("stableOutputDigest"):
                same_digest = "yes" if item["stableOutputDigest"] == baseline["stableOutputDigest"] else "no"
            md.append(
                f"| {md_escape(label)} | {fnum(item.get('warm_median_totalMs'))} | "
                f"{pct_delta(item.get('warm_median_totalMs'), baseline.get('warm_median_totalMs') if baseline else None)} | "
                f"{mib_from_kb(item.get('peak_processPssKb'))} | "
                f"{pct_delta(item.get('peak_processPssKb'), baseline.get('peak_processPssKb') if baseline else None)} | "
                f"{fnum(item.get('max_thermalStatus'), 0)} | "
                f"{'yes' if item.get('outputDigestStable','').lower() == 'true' else 'no'} | {same_digest} |"
            )
        md.append("")
        md.append("KV digest differences are review signals, not automatic quality verdicts. Quantized V/FA-on cases require their dedicated control in the full matrix.")

    elif lane == "opencl":
        control = next((item for item in records if item.get("executionLane") == "CPU_CONTROL"), None)
        if control is None:
            control = next((item for item in records if item.get("requestedGpuLayers") in {"0", 0}), None)
        md.append("| Lane | Requested GPU layers | Warm median total ms | Δ latency vs CPU | Peak PSS MiB | Max thermal | Effective placement |")
        md.append("| --- | ---: | ---: | ---: | ---: | ---: | --- |")
        for item in records:
            md.append(
                f"| {md_escape(item.get('executionLane','—'))} | {md_escape(item.get('requestedGpuLayers','—'))} | "
                f"{fnum(item.get('warm_median_totalMs'))} | "
                f"{pct_delta(item.get('warm_median_totalMs'), control.get('warm_median_totalMs') if control else None)} | "
                f"{mib_from_kb(item.get('peak_processPssKb'))} | "
                f"{fnum(item.get('max_thermalStatus'), 0)} | {md_escape(item.get('effectivePlacement','UNAVAILABLE'))} |"
            )
        md.append("")
        md.append("Requested GPU layers are not reported as effective placement. The pinned runtime still records `effectivePlacement=UNAVAILABLE`.")

md.append("")
md.append("## Interpretation")
md.append("")
if failed:
    md.append("- One or more quick lanes failed. Treat the suite as an integration/correctness failure until the failing lane is understood; do not proceed directly to full qualification for that lane.")
else:
    md.append("- All executed quick lanes completed. Use the metrics above only to decide which candidates deserve the longer canonical physical qualification.")
if skipped:
    md.append("- One or more optional lanes were skipped. A skipped OpenCL screen does not invalidate CPU/KV/batching evidence.")
md.append("- The quick profile intentionally uses a shorter context/output budget to keep device runs bounded.")
md.append("- No runtime profile, KV-cache type, Flash Attention mode, OpenCL policy or batching policy is promoted by this report.")

md_path.write_text("\n".join(md) + "\n", encoding="utf-8")

def status_class(status: str) -> str:
    return {
        "PASS": "pass",
        "PASS_WITH_SKIPS": "skip",
        "SKIP": "skip",
        "FAIL": "fail",
    }.get(status, "neutral")

def table(headers: list[str], rows: list[list[object]]) -> str:
    head = "".join(f"<th>{html.escape(str(h))}</th>" for h in headers)
    body = "".join(
        "<tr>" + "".join(f"<td>{html.escape(str(cell))}</td>" for cell in row) + "</tr>"
        for row in rows
    )
    return f"<table><thead><tr>{head}</tr></thead><tbody>{body}</tbody></table>"

lane_rows = []
for row in statuses:
    root = Path(row["evidenceRoot"])
    csv_path = find_csv(root, row["lane"])
    jsonl_path = find_jsonl(root, row["lane"])
    links = []
    if csv_path:
        links.append(f'<a href="{html.escape(rel(csv_path))}">CSV</a>')
    if jsonl_path:
        links.append(f'<a href="{html.escape(rel(jsonl_path))}">JSONL</a>')
    lane_rows.append(
        "<tr>"
        f"<td>{html.escape(row['tier'])}</td>"
        f"<td>{html.escape(row['lane'])}</td>"
        f'<td><span class="badge {status_class(row["status"])}">{html.escape(row["status"])}</span></td>'
        f"<td>{html.escape(row['note'])}</td>"
        f"<td>{' · '.join(links) if links else '—'}</td>"
        "</tr>"
    )

metric_sections = []
for row in statuses:
    if row["status"] != "PASS":
        continue
    tier, lane = row["tier"], row["lane"]
    root = Path(row["evidenceRoot"])
    records = read_csv(find_csv(root, lane))
    if not records:
        continue
    if lane == "batch":
        rows = [
            [
                item.get("batchWidth", "—"),
                item.get("samples", "—"),
                fnum(item.get("serialMedianMs")),
                fnum(item.get("batchMedianMs")),
                f"{fnum(item.get('medianSpeedup'), 3)}×",
                mib_from_kb(item.get("maxObservedPssKb")),
                fnum(item.get("maxThermalStatus"), 0),
                batch_signal(item.get("medianSpeedup")),
            ]
            for item in records
        ]
        content = table(
            ["Width", "Samples", "Serial median ms", "Native batch median ms", "Speedup", "Max observed PSS MiB", "Max thermal", "Signal"],
            rows,
        )
    elif lane == "kv":
        baseline = next((item for item in records if case_label(item.get("tuningCaseId", "")) == "release-default"), None)
        rows = []
        for item in records:
            same_digest = "—"
            if baseline and item.get("stableOutputDigest") and baseline.get("stableOutputDigest"):
                same_digest = "yes" if item["stableOutputDigest"] == baseline["stableOutputDigest"] else "no"
            rows.append([
                case_label(item.get("tuningCaseId", "")),
                fnum(item.get("warm_median_totalMs")),
                pct_delta(item.get("warm_median_totalMs"), baseline.get("warm_median_totalMs") if baseline else None),
                mib_from_kb(item.get("peak_processPssKb")),
                pct_delta(item.get("peak_processPssKb"), baseline.get("peak_processPssKb") if baseline else None),
                fnum(item.get("max_thermalStatus"), 0),
                "yes" if item.get("outputDigestStable", "").lower() == "true" else "no",
                same_digest,
            ])
        content = table(
            ["Case", "Warm median total ms", "Δ latency vs default", "Peak PSS MiB", "Δ PSS vs default", "Max thermal", "Digest stable", "Same digest as default"],
            rows,
        )
    else:
        control = next((item for item in records if item.get("executionLane") == "CPU_CONTROL"), None)
        if control is None:
            control = next((item for item in records if item.get("requestedGpuLayers") == "0"), None)
        rows = [
            [
                item.get("executionLane", "—"),
                item.get("requestedGpuLayers", "—"),
                fnum(item.get("warm_median_totalMs")),
                pct_delta(item.get("warm_median_totalMs"), control.get("warm_median_totalMs") if control else None),
                mib_from_kb(item.get("peak_processPssKb")),
                fnum(item.get("max_thermalStatus"), 0),
                item.get("effectivePlacement", "UNAVAILABLE"),
            ]
            for item in records
        ]
        content = table(
            ["Lane", "Requested GPU layers", "Warm median total ms", "Δ latency vs CPU", "Peak PSS MiB", "Max thermal", "Effective placement"],
            rows,
        )
    metric_sections.append(f"<section><h2>{html.escape(tier)} · {html.escape(lane)}</h2>{content}</section>")

device_html = table(
    ["Field", "Value"],
    [[key, value] for key, value in device_rows],
)

html_doc = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>LLRT quick physical-device report</title>
<style>
:root {{ color-scheme: light dark; }}
body {{ font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 0; background: #f5f7fa; color: #18202a; }}
main {{ max-width: 1180px; margin: 0 auto; padding: 32px 20px 56px; }}
.hero {{ background: #fff; border: 1px solid #dfe5ec; border-radius: 18px; padding: 24px; box-shadow: 0 8px 28px rgba(20,32,50,.06); }}
h1 {{ margin: 0 0 8px; font-size: 28px; }}
h2 {{ margin-top: 30px; font-size: 20px; }}
.meta {{ color: #5d6978; line-height: 1.6; }}
.notice {{ margin-top: 18px; padding: 14px 16px; border-radius: 12px; background: #fff7df; border: 1px solid #ecd58c; }}
table {{ width: 100%; border-collapse: collapse; margin: 12px 0 24px; background: #fff; border: 1px solid #dfe5ec; border-radius: 12px; overflow: hidden; }}
th, td {{ padding: 10px 12px; border-bottom: 1px solid #e8edf2; text-align: left; vertical-align: top; font-size: 14px; }}
th {{ background: #f0f3f7; font-weight: 650; }}
tr:last-child td {{ border-bottom: 0; }}
.badge {{ display: inline-block; padding: 3px 8px; border-radius: 999px; font-size: 12px; font-weight: 700; }}
.pass {{ background: #dff5e6; color: #166534; }}
.skip {{ background: #fff0c2; color: #854d0e; }}
.fail {{ background: #fee2e2; color: #991b1b; }}
.neutral {{ background: #e9edf2; color: #394555; }}
a {{ color: #175cd3; }}
footer {{ margin-top: 34px; color: #667281; font-size: 13px; }}
@media (prefers-color-scheme: dark) {{
  body {{ background: #101418; color: #edf1f5; }}
  .hero, table {{ background: #171c22; border-color: #2b333d; }}
  th {{ background: #20262d; }}
  th, td {{ border-color: #2b333d; }}
  .meta {{ color: #a8b2bf; }}
  .notice {{ background: #332b16; border-color: #695b2f; }}
  a {{ color: #84adff; }}
}}
</style>
</head>
<body>
<main>
  <div class="hero">
    <h1>LLRT quick physical-device screening</h1>
    <div class="meta">
      Run <code>{html.escape(manifest['runId'])}</code><br>
      Harness <code>{html.escape(manifest['harnessCommit'])}</code>
    </div>
    <p><span class="badge {status_class(overall)}">{html.escape(overall)}</span></p>
    <div class="notice"><strong>Diagnostic screening only.</strong> This report does not close LLRT-6C, LLRT-7C or LLRT-9C and must not promote runtime defaults.</div>
  </div>

  <section><h2>Device and profile</h2>{device_html}</section>

  <section>
    <h2>Lane status</h2>
    <table>
      <thead><tr><th>Tier</th><th>Lane</th><th>Status</th><th>Note</th><th>Evidence</th></tr></thead>
      <tbody>{''.join(lane_rows)}</tbody>
    </table>
  </section>

  {''.join(metric_sections)}

  <section>
    <h2>Interpretation</h2>
    <ul>
      <li>Quick results are screening signals used to decide which candidates deserve full physical qualification.</li>
      <li>Skipped OpenCL does not invalidate CPU/KV/batching evidence.</li>
      <li>No runtime, cache, Flash Attention, OpenCL or batching policy is promoted by this report.</li>
    </ul>
  </section>

  <footer>Generated locally from privacy-safe LLRT evidence CSV/JSONL artifacts.</footer>
</main>
</body>
</html>
"""
html_path.write_text(html_doc, encoding="utf-8")

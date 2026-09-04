#!/usr/bin/env python3
"""Generate and compare identity-bearing Android package metadata."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile

SCHEMA_VERSION = 1
DEFAULT_HARNESS_VERSION = "0.5.0"
METADATA_ARTIFACT_NAME = "package-build-metadata"

ARTIFACT_GLOBS = (
    "apps/local-llm-console/build/outputs/apk/**/*.apk",
    "apps/local-llm-console/build/outputs/bundle/**/*.aab",
    "apps/shared-runtime-client-consumer-fixture/build/outputs/apk/**/*.apk",
    "apps/device-test-runner/build/outputs/apk/**/*.apk",
    "apps/local-llm-phone-test/build/outputs/apk/**/*.apk",
    "apps/local-llm-phone-test/build/outputs/bundle/**/*.aab",
    "**/build/outputs/aar/*.aar",
)

DEPENDENCY_INPUTS = (
    "gradle/libs.versions.toml",
    "gradle/wrapper/gradle-wrapper.properties",
    "settings.gradle.kts",
    ".gitmodules",
)

CONFIG_INPUTS = (
    "build.gradle.kts",
    "gradle.properties",
    ".engineering/commands.json",
    "apps/local-llm-console/build.gradle.kts",
    "apps/local-llm-phone-test/build.gradle.kts",
    "apps/shared-runtime-client-consumer-fixture/build.gradle.kts",
)

MIGRATION_PATTERNS = (
    "**/schemas/**/*.json",
    "**/migration*.kt",
    "**/Migration*.kt",
)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def hash_inputs(root: Path, paths: list[Path]) -> str:
    digest = hashlib.sha256()
    for path in sorted(paths, key=lambda p: p.as_posix()):
        rel = path.relative_to(root).as_posix()
        digest.update(rel.encode())
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def existing_named_inputs(root: Path, names: tuple[str, ...]) -> list[Path]:
    return [root / name for name in names if (root / name).is_file()]


def migration_inputs(root: Path) -> list[Path]:
    found: set[Path] = set()
    for pattern in MIGRATION_PATTERNS:
        found.update(
            path
            for path in root.glob(pattern)
            if path.is_file() and "/build/" not in path.as_posix()
        )
    return sorted(found)


def read_properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    if not path.is_file():
        return result
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip()
    return result


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def run_output(command: list[str]) -> str:
    try:
        return subprocess.check_output(
            command,
            text=True,
            stderr=subprocess.STDOUT,
        ).strip()
    except (OSError, subprocess.CalledProcessError) as exc:
        return f"unavailable:{type(exc).__name__}"


def first_non_empty_line(value: str) -> str:
    return next((line.strip() for line in value.splitlines() if line.strip()), "unavailable")


def discover_artifacts(root: Path) -> list[Path]:
    found: set[Path] = set()
    for pattern in ARTIFACT_GLOBS:
        found.update(path for path in root.glob(pattern) if path.is_file())
    return sorted(found)


def artifact_version(root: Path, rel: str) -> str:
    if rel.startswith("apps/local-llm-console/"):
        return read_properties(root / "apps/local-llm-console/version.properties").get(
            "versionName", "0.1.0"
        )
    if rel.startswith("apps/local-llm-phone-test/"):
        return read_properties(root / "apps/local-llm-phone-test/version.properties").get(
            "versionName", DEFAULT_HARNESS_VERSION
        )
    return DEFAULT_HARNESS_VERSION


def artifact_product(rel: str, path: Path) -> str:
    if rel.startswith("apps/local-llm-console/"):
        return "local-llm-console"
    if rel.startswith("apps/local-llm-phone-test/"):
        return "local-llm-phone-test"
    if rel.startswith("apps/shared-runtime-client-consumer-fixture/"):
        return "shared-runtime-client-consumer-fixture"
    if rel.startswith("apps/device-test-runner/"):
        return "device-test-runner"
    if path.suffix == ".aar":
        name = path.stem
        return name[:-8] if name.endswith("-release") else name
    return path.stem


def artifact_variant(rel: str) -> str:
    lowered = rel.lower()
    if "androidtest" in lowered:
        return "androidTest"
    for value in ("internal", "release", "debug"):
        if f"/{value}/" in lowered or f"-{value}." in lowered:
            return value
    return "unspecified"


def create_manifest(args: argparse.Namespace) -> int:
    root = Path(args.root).resolve()
    output = Path(args.output)
    if not output.is_absolute():
        output = root / output
    artifacts = discover_artifacts(root)
    if not artifacts:
        raise SystemExit("No package artifacts found; refusing to create an empty manifest.")

    dirty = bool(git(root, "status", "--porcelain", "--untracked-files=no"))
    actual_revision = git(root, "rev-parse", "HEAD")
    if actual_revision != args.source_revision:
        raise SystemExit(
            f"source revision mismatch: requested {args.source_revision}, checkout is {actual_revision}"
        )

    dependency_inputs = existing_named_inputs(root, DEPENDENCY_INPUTS)
    config_inputs = existing_named_inputs(root, CONFIG_INPUTS)
    migrations = migration_inputs(root)
    llama_revision = git(root / "third_party/llama.cpp", "rev-parse", "HEAD")

    manifest_artifacts = []
    for path in artifacts:
        rel = path.relative_to(root).as_posix()
        manifest_artifacts.append(
            {
                "product": artifact_product(rel, path),
                "productVersion": artifact_version(root, rel),
                "buildId": args.build_id,
                "sourceRevision": args.source_revision,
                "platform": "android",
                "architecture": "arm64-v8a",
                "channel": args.channel,
                "variant": artifact_variant(rel),
                "path": rel,
                "fileName": path.name,
                "sizeBytes": path.stat().st_size,
                "sha256": sha256_file(path),
            }
        )

    toolchain = {
        "java": first_non_empty_line(run_output(["java", "-version"])),
        "python": first_non_empty_line(run_output([sys.executable, "--version"])),
        "cmake": first_non_empty_line(run_output(["cmake", "--version"])),
        "gradleWrapper": first_non_empty_line(run_output([str(root / "gradlew"), "--version"])),
        "llamaCppRevision": llama_revision,
    }
    toolchain_fingerprint = hashlib.sha256(
        json.dumps(toolchain, sort_keys=True).encode()
    ).hexdigest()

    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "identity": {
            "repository": args.repository,
            "buildId": args.build_id,
            "sourceRevision": args.source_revision,
            "sourceRef": args.source_ref,
            "dirty": dirty,
            "runId": args.run_id,
            "runAttempt": args.run_attempt,
        },
        "lineage": {
            "project": "android-local-llm-harness",
            "platform": "android",
            "architecture": "arm64-v8a",
            "channel": args.channel,
            "variant": "package",
            "sourceRef": args.source_ref,
        },
        "fingerprints": {
            "dependencies": hash_inputs(root, dependency_inputs),
            "configuration": hash_inputs(root, config_inputs),
            "migrations": hash_inputs(root, migrations) if migrations else None,
            "toolchain": toolchain_fingerprint,
        },
        "fingerprintInputs": {
            "dependencies": [path.relative_to(root).as_posix() for path in dependency_inputs],
            "configuration": [path.relative_to(root).as_posix() for path in config_inputs],
            "migrations": [path.relative_to(root).as_posix() for path in migrations],
        },
        "toolchain": toolchain,
        "validation": {
            "packageBuild": "PASS",
            "androidPackaging": "PASS",
            "note": (
                "Manifest is generated only after the package build and Android packaging "
                "verification steps succeed."
            ),
        },
        "artifacts": manifest_artifacts,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return 0


def request_json(url: str, token: str) -> dict:
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "android-local-llm-harness-package-metadata",
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def request_bytes(url: str, token: str) -> bytes:
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "android-local-llm-harness-package-metadata",
        },
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def comparable(previous: dict, channel: str, source_ref: str) -> bool:
    lineage = previous.get("lineage") or {}
    return lineage.get("channel") == channel and lineage.get("sourceRef") == source_ref


def fetch_previous(args: argparse.Namespace) -> int:
    token = os.environ.get(args.token_env, "")
    if not token:
        print(
            f"{args.token_env} is empty; previous package metadata unavailable.",
            file=sys.stderr,
        )
        return 0

    api = f"https://api.github.com/repos/{args.repository}"
    query = urllib.parse.urlencode({"status": "success", "per_page": "50"})
    runs = request_json(
        f"{api}/actions/workflows/{args.workflow}/runs?{query}", token
    ).get("workflow_runs", [])
    for run in runs:
        if str(run.get("id")) == str(args.current_run_id):
            continue
        artifacts = request_json(
            f"{api}/actions/runs/{run['id']}/artifacts?per_page=100", token
        ).get("artifacts", [])
        metadata = next(
            (
                item
                for item in artifacts
                if item.get("name") == METADATA_ARTIFACT_NAME and not item.get("expired")
            ),
            None,
        )
        if metadata is None:
            continue
        try:
            archive = request_bytes(
                f"{api}/actions/artifacts/{metadata['id']}/zip", token
            )
        except urllib.error.HTTPError as exc:
            if exc.code in (404, 410):
                print(
                    f"Previous package metadata artifact from run {run['id']} is unavailable "
                    f"(HTTP {exc.code}); trying older successful runs.",
                    file=sys.stderr,
                )
                continue
            raise
        with zipfile.ZipFile(io.BytesIO(archive)) as bundle:
            names = [
                name for name in bundle.namelist() if name.endswith("build-manifest.json")
            ]
            if not names:
                continue
            previous = json.loads(bundle.read(names[0]).decode("utf-8"))
        if not comparable(previous, args.channel, args.source_ref):
            continue
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(
            json.dumps(previous, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        print(f"Using previous comparable package build run {run['id']}.")
        return 0

    print("No previous successful comparable package manifest found.")
    return 0


def changed(current: dict, previous: dict, key: str) -> str:
    return "CHANGED" if current.get(key) != previous.get(key) else "UNCHANGED"


def artifact_map(manifest: dict) -> dict[tuple[str, str, str], dict]:
    return {
        (item["product"], item["variant"], item["fileName"]): item
        for item in manifest.get("artifacts", [])
    }


def create_delta(args: argparse.Namespace) -> int:
    current = json.loads(Path(args.current).read_text(encoding="utf-8"))
    previous_path = Path(args.previous) if args.previous else None
    previous = (
        json.loads(previous_path.read_text(encoding="utf-8"))
        if previous_path and previous_path.is_file()
        else None
    )

    lines = [
        "# Build changelog",
        "",
        f"- Build ID: `{current['identity']['buildId']}`",
        f"- Source revision: `{current['identity']['sourceRevision']}`",
        (
            f"- Lineage: `{current['lineage']['project']} / {current['lineage']['platform']} / "
            f"{current['lineage']['architecture']} / {current['lineage']['channel']} / "
            f"{current['lineage']['variant']}`"
        ),
        "",
    ]

    if previous is None:
        lines += [
            (
                "No previous successful comparable build manifest was available. "
                "This build establishes the lineage baseline."
            ),
            "",
            "## Validation",
            "",
            "- Package build: PASS",
            "- Android packaging verification: PASS",
            "",
        ]
    else:
        lines += [
            (
                f"Compared with build `{previous['identity']['buildId']}` at source "
                f"`{previous['identity']['sourceRevision']}`."
            ),
            "",
            "## Source",
            "",
            (
                f"- Revision: `{previous['identity']['sourceRevision']}` → "
                f"`{current['identity']['sourceRevision']}`"
            ),
            (
                f"- Dirty state: `{previous['identity'].get('dirty')}` → "
                f"`{current['identity'].get('dirty')}`"
            ),
            "",
            "## Dependencies",
            "",
            f"- {changed(current['fingerprints'], previous['fingerprints'], 'dependencies')}",
            "",
            "## Toolchain",
            "",
            f"- {changed(current['fingerprints'], previous['fingerprints'], 'toolchain')}",
            "",
            "## Configuration",
            "",
            f"- {changed(current['fingerprints'], previous['fingerprints'], 'configuration')}",
            "",
            "## Compatibility / migrations",
            "",
            f"- {changed(current['fingerprints'], previous['fingerprints'], 'migrations')}",
            "",
            "## Artifact metrics",
            "",
            "| Product | Variant | File | Bytes | Delta bytes | SHA-256 changed |",
            "| --- | --- | --- | ---: | ---: | --- |",
        ]
        old = artifact_map(previous)
        current_map = artifact_map(current)
        for key, item in sorted(current_map.items()):
            before = old.get(key)
            delta = (
                item["sizeBytes"] - before["sizeBytes"]
                if before
                else item["sizeBytes"]
            )
            digest_changed = (
                "NEW"
                if before is None
                else ("YES" if item["sha256"] != before["sha256"] else "NO")
            )
            lines.append(
                f"| {item['product']} | {item['variant']} | `{item['fileName']}` | "
                f"{item['sizeBytes']} | {delta:+d} | {digest_changed} |"
            )
        for key in sorted(set(old) - set(current_map)):
            item = old[key]
            lines.append(
                f"| {item['product']} | {item['variant']} | `{item['fileName']}` | "
                f"0 | {-item['sizeBytes']:+d} | REMOVED |"
            )
        lines += [
            "",
            "## Validation",
            "",
            f"- Package build: {current['validation']['packageBuild']}",
            (
                "- Android packaging verification: "
                f"{current['validation']['androidPackaging']}"
            ),
            "",
        ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8")
    return 0


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser()
    sub = root.add_subparsers(dest="command", required=True)

    manifest = sub.add_parser("manifest")
    manifest.add_argument("--root", default=".")
    manifest.add_argument("--output", required=True)
    manifest.add_argument("--build-id", required=True)
    manifest.add_argument("--source-revision", required=True)
    manifest.add_argument("--source-ref", required=True)
    manifest.add_argument("--channel", required=True)
    manifest.add_argument("--repository", required=True)
    manifest.add_argument("--run-id", required=True)
    manifest.add_argument("--run-attempt", required=True)
    manifest.set_defaults(func=create_manifest)

    previous = sub.add_parser("fetch-previous")
    previous.add_argument("--repository", required=True)
    previous.add_argument("--workflow", default="package.yml")
    previous.add_argument("--current-run-id", required=True)
    previous.add_argument("--channel", required=True)
    previous.add_argument("--source-ref", required=True)
    previous.add_argument("--token-env", default="GITHUB_TOKEN")
    previous.add_argument("--output", required=True)
    previous.set_defaults(func=fetch_previous)

    delta = sub.add_parser("delta")
    delta.add_argument("--current", required=True)
    delta.add_argument("--previous")
    delta.add_argument("--output", required=True)
    delta.set_defaults(func=create_delta)

    return root


def main() -> int:
    args = parser().parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
#!/usr/bin/env python3
"""Create and verify immutable Harnex GitHub Release metadata."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

SCHEMA_VERSION = 1
HOST_PACKAGE = "io.github.daniele21.localllm.phonetest"
HEX_64 = re.compile(r"^[0-9a-f]{64}$")
SEMVER = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?$")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def read_properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def read_version(root: Path) -> str:
    version = (root / "VERSION").read_text(encoding="utf-8").strip()
    if not SEMVER.fullmatch(version):
        raise SystemExit(f"VERSION is not supported SemVer: {version!r}")
    return version


def extract_protocol(root: Path) -> tuple[int, int]:
    text = (
        root
        / "transports/android-binder-contract/src/main/kotlin/io/github/daniele21/localllm/transport/binder/contract/ProtocolModels.kt"
    ).read_text(encoding="utf-8")
    major = re.search(r"const val MAJOR\s*=\s*([0-9]+)", text)
    minor = re.search(r"const val MINOR\s*=\s*([0-9]+)", text)
    if not major or not minor:
        raise SystemExit("Unable to resolve Binder protocol major/minor")
    return int(major.group(1)), int(minor.group(1))


def changelog_section(root: Path, version: str) -> str:
    text = (root / "CHANGELOG.md").read_text(encoding="utf-8")
    marker = f"## [{version}]"
    start = text.find(marker)
    if start < 0:
        raise SystemExit(f"CHANGELOG.md has no {marker} section")
    next_heading = text.find("\n## ", start + len(marker))
    section = text[start : next_heading if next_heading >= 0 else len(text)].strip()
    if len(section.splitlines()) < 2:
        raise SystemExit(f"CHANGELOG section for {version} is empty")
    return section + "\n"


def prepare(args: argparse.Namespace) -> int:
    root = Path(args.root).resolve()
    apk = Path(args.apk).resolve()
    version = read_version(root)
    if args.version != version:
        raise SystemExit(f"requested version {args.version} does not match VERSION {version}")
    if not HEX_64.fullmatch(args.signer_sha256.lower()):
        raise SystemExit("signer SHA-256 must be 64 lowercase/uppercase hex characters")

    actual_revision = git(root, "rev-parse", "HEAD")
    if actual_revision != args.source_revision:
        raise SystemExit(
            f"source revision mismatch: requested {args.source_revision}, checkout is {actual_revision}"
        )
    if git(root, "status", "--porcelain", "--untracked-files=no"):
        raise SystemExit("release metadata requires a clean tracked source checkout")

    host_version = read_properties(root / "apps/local-llm-phone-test/version.properties")
    sdk_version = read_properties(root / "transports/android-binder-client/version.properties").get(
        "version"
    )
    if not sdk_version:
        raise SystemExit("Consumer SDK version is missing")
    protocol_major, protocol_minor = extract_protocol(root)
    llama_revision = git(root / "third_party/llama.cpp", "rev-parse", "HEAD")
    artifact_sha = sha256_file(apk)

    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "release": {
            "version": version,
            "tag": f"v{version}",
            "prerelease": "-" in version,
            "sourceRevision": actual_revision,
            "sourceRef": "main",
        },
        "host": {
            "packageName": HOST_PACKAGE,
            "versionName": host_version.get("versionName", "unknown"),
            "versionCode": int(host_version.get("versionCode", "0")),
            "distributionChannel": "github",
            "signerCertificateSha256": args.signer_sha256.lower(),
        },
        "consumerSdk": {"version": sdk_version},
        "binderProtocol": {"major": protocol_major, "minor": protocol_minor},
        "backend": {"name": "llama.cpp", "revision": llama_revision},
        "artifacts": [
            {
                "name": apk.name,
                "platform": "android",
                "architecture": "arm64-v8a",
                "sha256": artifact_sha,
                "sizeBytes": apk.stat().st_size,
            }
        ],
        "evidence": {
            "required": True,
            "status": "PENDING_REAL_ENVIRONMENT_REVIEW",
        },
    }

    manifest_output = Path(args.manifest_output)
    manifest_output.parent.mkdir(parents=True, exist_ok=True)
    manifest_output.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    checksums_output = Path(args.checksums_output)
    checksums_output.parent.mkdir(parents=True, exist_ok=True)
    checksums_output.write_text(f"{artifact_sha}  {apk.name}\n", encoding="utf-8")

    notes_output = Path(args.notes_output)
    notes_output.parent.mkdir(parents=True, exist_ok=True)
    notes_output.write_text(changelog_section(root, version), encoding="utf-8")
    return 0


def verify_candidate(args: argparse.Namespace) -> int:
    manifest = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    apk = Path(args.apk)
    version = manifest["release"]["version"]
    if args.version != version:
        raise SystemExit(f"candidate version mismatch: {version} != {args.version}")
    if manifest["release"]["sourceRevision"] != args.source_revision:
        raise SystemExit("candidate source revision does not match requested source")
    artifacts = manifest.get("artifacts", [])
    if len(artifacts) != 1:
        raise SystemExit("GitHub release candidate must contain exactly one public APK")
    artifact = artifacts[0]
    if artifact["name"] != apk.name:
        raise SystemExit("candidate APK filename does not match manifest")
    if artifact["sha256"] != sha256_file(apk):
        raise SystemExit("candidate APK SHA-256 does not match manifest")
    if artifact["sizeBytes"] != apk.stat().st_size:
        raise SystemExit("candidate APK size does not match manifest")
    return 0


def finalize(args: argparse.Namespace) -> int:
    manifest = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    if not args.evidence_ref.strip():
        raise SystemExit("physical evidence reference is required")
    evidence_sha = args.evidence_sha256.lower()
    if not HEX_64.fullmatch(evidence_sha):
        raise SystemExit("physical evidence SHA-256 must be 64 hex characters")

    record = dict(manifest)
    record["evidence"] = {
        "required": True,
        "status": "REVIEWED_FOR_PUBLICATION",
        "reference": args.evidence_ref.strip(),
        "sha256": evidence_sha,
        "candidateRunId": str(args.candidate_run_id),
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser()
    sub = root.add_subparsers(dest="command", required=True)

    prepare_parser = sub.add_parser("prepare")
    prepare_parser.add_argument("--root", default=".")
    prepare_parser.add_argument("--version", required=True)
    prepare_parser.add_argument("--source-revision", required=True)
    prepare_parser.add_argument("--apk", required=True)
    prepare_parser.add_argument("--signer-sha256", required=True)
    prepare_parser.add_argument("--manifest-output", required=True)
    prepare_parser.add_argument("--checksums-output", required=True)
    prepare_parser.add_argument("--notes-output", required=True)
    prepare_parser.set_defaults(func=prepare)

    verify_parser = sub.add_parser("verify")
    verify_parser.add_argument("--version", required=True)
    verify_parser.add_argument("--source-revision", required=True)
    verify_parser.add_argument("--apk", required=True)
    verify_parser.add_argument("--manifest", required=True)
    verify_parser.set_defaults(func=verify_candidate)

    finalize_parser = sub.add_parser("finalize")
    finalize_parser.add_argument("--manifest", required=True)
    finalize_parser.add_argument("--evidence-ref", required=True)
    finalize_parser.add_argument("--evidence-sha256", required=True)
    finalize_parser.add_argument("--candidate-run-id", required=True)
    finalize_parser.add_argument("--output", required=True)
    finalize_parser.set_defaults(func=finalize)
    return root


def main() -> int:
    args = parser().parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())

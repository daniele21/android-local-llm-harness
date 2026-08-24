#!/usr/bin/env python3

from __future__ import annotations

import argparse
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import urllib.error
import zipfile


SCRIPT = Path(__file__).with_name("package_artifact_metadata.py")
SPEC = importlib.util.spec_from_file_location("package_artifact_metadata", SCRIPT)
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


def write(root: Path, relative: str, content: bytes | str = b"x") -> Path:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(content, str):
        path.write_text(content, encoding="utf-8")
    else:
        path.write_bytes(content)
    return path


class PackageArtifactMetadataTest(unittest.TestCase):
    def test_manifest_has_identity_hashes_and_fingerprints(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "gradle/libs.versions.toml", "[versions]\na='1'\n")
            write(root, "gradle/wrapper/gradle-wrapper.properties", "distributionUrl=x\n")
            write(root, "settings.gradle.kts", 'rootProject.name="harness"\n')
            write(root, ".gitmodules", "")
            write(root, "build.gradle.kts", "")
            write(root, "gradle.properties", "")
            write(root, ".engineering/commands.json", "{}")
            write(root, "apps/local-llm-console/build.gradle.kts", "")
            write(root, "apps/local-llm-phone-test/build.gradle.kts", "")
            write(root, "apps/shared-runtime-client-consumer-fixture/build.gradle.kts", "")
            write(root, "apps/local-llm-console/version.properties", "versionName=0.1.0\n")
            write(root, "apps/local-llm-phone-test/version.properties", "versionName=0.5.0\n")
            artifact = write(
                root,
                "apps/local-llm-phone-test/build/outputs/bundle/release/local-llm-phone-test-release.aab",
                b"package-bytes",
            )
            write(root, "third_party/llama.cpp/.git", "gitdir: nowhere\n")
            output = root / "build/package-metadata/build-manifest.json"

            def fake_git(cwd: Path, *args: str) -> str:
                if args == ("status", "--porcelain", "--untracked-files=no"):
                    return ""
                if args == ("rev-parse", "HEAD"):
                    return "llama-sha" if cwd.name == "llama.cpp" else "source-sha"
                raise AssertionError((cwd, args))

            args = argparse.Namespace(
                root=str(root),
                output=str(output),
                build_id="100.2",
                source_revision="source-sha",
                source_ref="dev",
                channel="dev",
                repository="daniele21/android-local-llm-harness",
                run_id="100",
                run_attempt="2",
            )
            with mock.patch.object(module, "git", side_effect=fake_git), mock.patch.object(
                module, "run_output", return_value="tool-version"
            ):
                self.assertEqual(module.create_manifest(args), 0)

            manifest = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(manifest["identity"]["buildId"], "100.2")
            self.assertEqual(manifest["identity"]["sourceRevision"], "source-sha")
            self.assertFalse(manifest["identity"]["dirty"])
            self.assertEqual(manifest["lineage"]["channel"], "dev")
            self.assertEqual(len(manifest["artifacts"]), 1)
            item = manifest["artifacts"][0]
            self.assertEqual(item["product"], "local-llm-phone-test")
            self.assertEqual(item["productVersion"], "0.5.0")
            self.assertEqual(item["variant"], "release")
            self.assertEqual(item["sizeBytes"], artifact.stat().st_size)
            self.assertEqual(item["sha256"], module.sha256_file(artifact))
            self.assertEqual(manifest["validation"]["androidPackaging"], "PASS")

    def test_delta_compares_previous_successful_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            previous = {
                "identity": {"buildId": "1.1", "sourceRevision": "a", "dirty": False},
                "lineage": {
                    "project": "android-local-llm-harness",
                    "platform": "android",
                    "architecture": "arm64-v8a",
                    "channel": "dev",
                    "variant": "package",
                },
                "fingerprints": {
                    "dependencies": "dep-a",
                    "toolchain": "tool-a",
                    "configuration": "cfg",
                    "migrations": None,
                },
                "artifacts": [
                    {
                        "product": "phone",
                        "variant": "release",
                        "fileName": "phone.aab",
                        "sizeBytes": 100,
                        "sha256": "aaa",
                    }
                ],
            }
            current = {
                "identity": {"buildId": "2.1", "sourceRevision": "b", "dirty": False},
                "lineage": previous["lineage"],
                "fingerprints": {
                    "dependencies": "dep-b",
                    "toolchain": "tool-a",
                    "configuration": "cfg",
                    "migrations": None,
                },
                "validation": {"packageBuild": "PASS", "androidPackaging": "PASS"},
                "artifacts": [
                    {
                        "product": "phone",
                        "variant": "release",
                        "fileName": "phone.aab",
                        "sizeBytes": 125,
                        "sha256": "bbb",
                    }
                ],
            }
            current_path = root / "current.json"
            previous_path = root / "previous.json"
            output = root / "BUILD_CHANGELOG.md"
            current_path.write_text(json.dumps(current), encoding="utf-8")
            previous_path.write_text(json.dumps(previous), encoding="utf-8")
            args = argparse.Namespace(
                current=str(current_path), previous=str(previous_path), output=str(output)
            )
            self.assertEqual(module.create_delta(args), 0)
            text = output.read_text(encoding="utf-8")
            self.assertIn("Compared with build `1.1`", text)
            self.assertIn("## Dependencies", text)
            self.assertIn("- CHANGED", text)
            self.assertIn("| phone | release | `phone.aab` | 125 | +25 | YES |", text)

    def test_first_comparable_build_is_explicit(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            current = {
                "identity": {"buildId": "2.1", "sourceRevision": "b"},
                "lineage": {
                    "project": "android-local-llm-harness",
                    "platform": "android",
                    "architecture": "arm64-v8a",
                    "channel": "dev",
                    "variant": "package",
                },
                "validation": {"packageBuild": "PASS", "androidPackaging": "PASS"},
                "artifacts": [],
            }
            current_path = root / "current.json"
            output = root / "BUILD_CHANGELOG.md"
            current_path.write_text(json.dumps(current), encoding="utf-8")
            args = argparse.Namespace(current=str(current_path), previous=None, output=str(output))
            self.assertEqual(module.create_delta(args), 0)
            self.assertIn(
                "establishes the lineage baseline", output.read_text(encoding="utf-8")
            )

    def test_fetch_previous_skips_non_comparable_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            output = root / "previous.json"
            wrong = {
                "lineage": {"channel": "main", "sourceRef": "main"},
                "identity": {"buildId": "wrong"},
            }
            right = {
                "lineage": {"channel": "dev", "sourceRef": "dev"},
                "identity": {"buildId": "right"},
            }

            def archive(manifest: dict) -> bytes:
                buffer = io.BytesIO()
                with zipfile.ZipFile(buffer, "w") as bundle:
                    bundle.writestr(
                        "build/package-metadata/build-manifest.json",
                        json.dumps(manifest),
                    )
                return buffer.getvalue()

            def fake_json(url: str, token: str) -> dict:
                if "/actions/workflows/" in url:
                    return {"workflow_runs": [{"id": 9}, {"id": 8}]}
                if "/actions/runs/9/artifacts" in url:
                    return {
                        "artifacts": [
                            {"id": 90, "name": module.METADATA_ARTIFACT_NAME, "expired": False}
                        ]
                    }
                if "/actions/runs/8/artifacts" in url:
                    return {
                        "artifacts": [
                            {"id": 80, "name": module.METADATA_ARTIFACT_NAME, "expired": False}
                        ]
                    }
                raise AssertionError(url)

            def fake_bytes(url: str, token: str) -> bytes:
                return archive(wrong if "/90/" in url else right)

            args = argparse.Namespace(
                repository="owner/repo",
                workflow="package.yml",
                current_run_id="10",
                channel="dev",
                source_ref="dev",
                token_env="TEST_TOKEN",
                output=str(output),
            )
            with mock.patch.dict("os.environ", {"TEST_TOKEN": "secret"}), mock.patch.object(
                module, "request_json", side_effect=fake_json
            ), mock.patch.object(module, "request_bytes", side_effect=fake_bytes):
                self.assertEqual(module.fetch_previous(args), 0)

            self.assertEqual(
                json.loads(output.read_text(encoding="utf-8"))["identity"]["buildId"],
                "right",
            )

    def test_fetch_previous_skips_unavailable_historical_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            output = root / "previous.json"
            right = {
                "lineage": {"channel": "dev", "sourceRef": "dev"},
                "identity": {"buildId": "older-valid"},
            }

            buffer = io.BytesIO()
            with zipfile.ZipFile(buffer, "w") as bundle:
                bundle.writestr(
                    "build/package-metadata/build-manifest.json",
                    json.dumps(right),
                )
            valid_archive = buffer.getvalue()

            def fake_json(url: str, token: str) -> dict:
                if "/actions/workflows/" in url:
                    return {"workflow_runs": [{"id": 9}, {"id": 8}]}
                if "/actions/runs/9/artifacts" in url:
                    return {
                        "artifacts": [
                            {"id": 90, "name": module.METADATA_ARTIFACT_NAME, "expired": False}
                        ]
                    }
                if "/actions/runs/8/artifacts" in url:
                    return {
                        "artifacts": [
                            {"id": 80, "name": module.METADATA_ARTIFACT_NAME, "expired": False}
                        ]
                    }
                raise AssertionError(url)

            def fake_bytes(url: str, token: str) -> bytes:
                if "/90/" in url:
                    raise urllib.error.HTTPError(url, 410, "Gone", None, None)
                return valid_archive

            args = argparse.Namespace(
                repository="owner/repo",
                workflow="package.yml",
                current_run_id="10",
                channel="dev",
                source_ref="dev",
                token_env="TEST_TOKEN",
                output=str(output),
            )
            with mock.patch.dict("os.environ", {"TEST_TOKEN": "secret"}), mock.patch.object(
                module, "request_json", side_effect=fake_json
            ), mock.patch.object(module, "request_bytes", side_effect=fake_bytes):
                self.assertEqual(module.fetch_previous(args), 0)

            self.assertEqual(
                json.loads(output.read_text(encoding="utf-8"))["identity"]["buildId"],
                "older-valid",
            )


if __name__ == "__main__":
    unittest.main()
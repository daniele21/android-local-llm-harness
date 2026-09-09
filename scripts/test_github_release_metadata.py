#!/usr/bin/env python3

from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

SCRIPT = Path(__file__).with_name("github_release_metadata.py")
SPEC = importlib.util.spec_from_file_location("github_release_metadata", SCRIPT)
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


def write(root: Path, relative: str, content: str | bytes) -> Path:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(content, bytes):
        path.write_bytes(content)
    else:
        path.write_text(content, encoding="utf-8")
    return path


class GithubReleaseMetadataTest(unittest.TestCase):
    def fixture_root(self, root: Path) -> Path:
        write(root, "VERSION", "0.5.0-rc.1\n")
        write(
            root,
            "CHANGELOG.md",
            (
                "# Changelog\n\n"
                "## [Unreleased]\n\n"
                "## [0.5.0-rc.1]\n\n"
                "### Added\n\n"
                "- First release.\n\n"
                "## Release history\n\n"
                "This must not leak into release notes.\n"
            ),
        )
        write(
            root,
            "apps/local-llm-phone-test/version.properties",
            "versionCode=33\nversionName=1.0.0\n",
        )
        write(
            root,
            "transports/android-binder-client/version.properties",
            "version=0.1.0-alpha.11\n",
        )
        write(
            root,
            "transports/android-binder-contract/src/main/kotlin/io/github/daniele21/localllm/transport/binder/contract/ProtocolModels.kt",
            "object BinderProtocolV1 { const val MAJOR = 1; const val MINOR = 4 }\n",
        )
        write(root, "third_party/llama.cpp/README.md", "fixture\n")
        return write(root, "dist/harnex-v0.5.0-rc.1-android-arm64.apk", b"apk-bytes")

    def test_prepare_records_exact_release_identity(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk = self.fixture_root(root)
            manifest = root / "dist/release-manifest.json"
            checksums = root / "dist/SHA256SUMS"
            notes = root / "dist/release-notes.md"

            def fake_git(cwd: Path, *args: str) -> str:
                if args == ("rev-parse", "HEAD"):
                    return "b" * 40 if cwd.name == "llama.cpp" else "a" * 40
                if args == ("status", "--porcelain", "--untracked-files=no"):
                    return ""
                raise AssertionError((cwd, args))

            args = argparse.Namespace(
                root=str(root),
                version="0.5.0-rc.1",
                source_revision="a" * 40,
                apk=str(apk),
                signer_sha256="c" * 64,
                manifest_output=str(manifest),
                checksums_output=str(checksums),
                notes_output=str(notes),
            )
            with mock.patch.object(module, "git", side_effect=fake_git):
                self.assertEqual(module.prepare(args), 0)

            data = json.loads(manifest.read_text(encoding="utf-8"))
            self.assertEqual(data["release"]["tag"], "v0.5.0-rc.1")
            self.assertTrue(data["release"]["prerelease"])
            self.assertEqual(data["release"]["sourceRevision"], "a" * 40)
            self.assertEqual(data["host"]["packageName"], module.HOST_PACKAGE)
            self.assertEqual(data["host"]["distributionChannel"], "github")
            self.assertEqual(data["host"]["signerCertificateSha256"], "c" * 64)
            self.assertEqual(data["consumerSdk"]["version"], "0.1.0-alpha.11")
            self.assertEqual(data["binderProtocol"], {"major": 1, "minor": 4})
            self.assertEqual(data["backend"]["revision"], "b" * 40)
            self.assertEqual(data["artifacts"][0]["sha256"], module.sha256_file(apk))
            self.assertIn(apk.name, checksums.read_text(encoding="utf-8"))
            notes_text = notes.read_text(encoding="utf-8")
            self.assertIn("## [0.5.0-rc.1]", notes_text)
            self.assertNotIn("## Release history", notes_text)
            self.assertNotIn("must not leak", notes_text)

    def test_verify_rejects_changed_candidate_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk = self.fixture_root(root)
            manifest = root / "manifest.json"
            manifest.write_text(
                json.dumps(
                    {
                        "release": {"version": "0.5.0-rc.1", "sourceRevision": "a" * 40},
                        "artifacts": [
                            {
                                "name": apk.name,
                                "sha256": module.sha256_file(apk),
                                "sizeBytes": apk.stat().st_size,
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            apk.write_bytes(b"changed")
            args = argparse.Namespace(
                version="0.5.0-rc.1",
                source_revision="a" * 40,
                apk=str(apk),
                manifest=str(manifest),
            )
            with self.assertRaises(SystemExit):
                module.verify_candidate(args)

    def test_finalize_requires_evidence_identity(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = write(
                root,
                "manifest.json",
                json.dumps({"release": {"version": "0.5.0-rc.1"}, "evidence": {}}),
            )
            output = root / "release-record.json"
            args = argparse.Namespace(
                manifest=str(manifest),
                evidence_ref="device-evidence://2026-09-09/rc1",
                evidence_sha256="d" * 64,
                candidate_run_id="123",
                output=str(output),
            )
            self.assertEqual(module.finalize(args), 0)
            data = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(data["evidence"]["status"], "REVIEWED_FOR_PUBLICATION")
            self.assertEqual(data["evidence"]["candidateRunId"], "123")
            self.assertEqual(data["evidence"]["sha256"], "d" * 64)


if __name__ == "__main__":
    unittest.main()

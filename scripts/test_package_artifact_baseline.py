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
import urllib.request
import zipfile

SCRIPT = Path(__file__).with_name("package_artifact_baseline.py")
SPEC = importlib.util.spec_from_file_location("package_artifact_baseline", SCRIPT)
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


def archive(manifest: dict) -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as bundle:
        bundle.writestr("build/package-metadata/build-manifest.json", json.dumps(manifest))
    return buffer.getvalue()


def manifest(build_id: str, channel: str = "pr-dev", source_ref: str = "ux/test") -> dict:
    return {
        "schemaVersion": 1,
        "identity": {"buildId": build_id, "sourceRevision": build_id},
        "lineage": {"channel": channel, "sourceRef": source_ref},
        "artifacts": [],
    }


class PackageArtifactBaselineTest(unittest.TestCase):
    def test_cross_origin_redirect_strips_sensitive_github_headers(self) -> None:
        handler = module.GitHubArtifactRedirectHandler()
        request = urllib.request.Request(
            "https://api.github.com/repos/o/r/actions/artifacts/1/zip",
            headers=module._github_headers("secret"),
        )
        redirected = handler.redirect_request(
            request,
            None,
            302,
            "Found",
            {},
            "https://artifactcache.example.net/signed?token=abc",
        )
        assert redirected is not None
        lowered = {key.lower(): value for key, value in redirected.header_items()}
        self.assertNotIn("authorization", lowered)
        self.assertNotIn("x-github-api-version", lowered)
        self.assertNotIn("accept", lowered)
        self.assertEqual(lowered.get("user-agent"), "android-local-llm-harness-package-baseline")

    def test_same_origin_redirect_keeps_authorization(self) -> None:
        handler = module.GitHubArtifactRedirectHandler()
        request = urllib.request.Request(
            "https://api.github.com/repos/o/r/actions/artifacts/1/zip",
            headers=module._github_headers("secret"),
        )
        redirected = handler.redirect_request(
            request,
            None,
            302,
            "Found",
            {},
            "https://api.github.com/repos/o/r/actions/artifacts/1/archive",
        )
        assert redirected is not None
        lowered = {key.lower(): value for key, value in redirected.header_items()}
        self.assertEqual(lowered.get("authorization"), "Bearer secret")

    def test_https_downgrade_is_rejected(self) -> None:
        handler = module.GitHubArtifactRedirectHandler()
        request = urllib.request.Request(
            "https://api.github.com/repos/o/r/actions/artifacts/1/zip",
            headers=module._github_headers("secret"),
        )
        with self.assertRaises(urllib.error.HTTPError) as raised:
            handler.redirect_request(request, None, 302, "Found", {}, "http://example.net/file")
        self.assertEqual(raised.exception.code, 403)

    def test_valid_comparable_manifest_is_written(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "previous.json"
            previous = manifest("older")
            args = argparse.Namespace(
                repository="owner/repo",
                workflow="package.yml",
                current_run_id="10",
                channel="pr-dev",
                source_ref="ux/test",
                token_env="TEST_TOKEN",
                output=str(output),
            )
            with mock.patch.dict("os.environ", {"TEST_TOKEN": "secret"}), mock.patch.object(
                module, "_workflow_runs", return_value=[{"id": 9}]
            ), mock.patch.object(
                module,
                "_artifacts_for_run",
                return_value=[{"id": 90, "name": module.METADATA_ARTIFACT_NAME, "expired": False}],
            ), mock.patch.object(
                module, "request_artifact_bytes", return_value=archive(previous)
            ):
                self.assertEqual(module.fetch_previous(args), 0)
            self.assertEqual(json.loads(output.read_text())["identity"]["buildId"], "older")

    def test_deleted_historical_artifact_falls_back_to_older_run(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "previous.json"
            older = manifest("older-valid")
            args = argparse.Namespace(
                repository="owner/repo",
                workflow="package.yml",
                current_run_id="10",
                channel="pr-dev",
                source_ref="ux/test",
                token_env="TEST_TOKEN",
                output=str(output),
            )

            def artifacts(api: str, run_id: object, token: str) -> list[dict]:
                return [{"id": int(run_id) * 10, "name": module.METADATA_ARTIFACT_NAME, "expired": False}]

            def download(url: str, token: str) -> bytes:
                if "/90/" in url:
                    raise urllib.error.HTTPError(url, 410, "Gone", None, None)
                return archive(older)

            with mock.patch.dict("os.environ", {"TEST_TOKEN": "secret"}), mock.patch.object(
                module, "_workflow_runs", return_value=[{"id": 9}, {"id": 8}]
            ), mock.patch.object(module, "_artifacts_for_run", side_effect=artifacts), mock.patch.object(
                module, "request_artifact_bytes", side_effect=download
            ):
                self.assertEqual(module.fetch_previous(args), 0)
            self.assertEqual(json.loads(output.read_text())["identity"]["buildId"], "older-valid")

    def test_auth_failure_is_not_hidden_as_missing_baseline(self) -> None:
        args = argparse.Namespace(
            repository="owner/repo",
            workflow="package.yml",
            current_run_id="10",
            channel="pr-dev",
            source_ref="ux/test",
            token_env="TEST_TOKEN",
            output="unused.json",
        )
        auth_error = urllib.error.HTTPError("https://api.github.com/a", 401, "Unauthorized", None, None)
        with mock.patch.dict("os.environ", {"TEST_TOKEN": "secret"}), mock.patch.object(
            module, "_workflow_runs", return_value=[{"id": 9}]
        ), mock.patch.object(
            module,
            "_artifacts_for_run",
            return_value=[{"id": 90, "name": module.METADATA_ARTIFACT_NAME, "expired": False}],
        ), mock.patch.object(module, "request_artifact_bytes", side_effect=auth_error):
            with self.assertRaises(urllib.error.HTTPError):
                module.fetch_previous(args)

    def test_malformed_metadata_artifact_fails_closed(self) -> None:
        with self.assertRaises(module.BaselineLookupError):
            module._manifest_from_archive(b"not-a-zip", 9)

    def test_retryable_http_error_is_bounded(self) -> None:
        opener = mock.Mock()
        headers = {"Retry-After": "0"}
        error = urllib.error.HTTPError("https://api.github.com/a", 503, "Unavailable", headers, None)
        opener.open.side_effect = error
        request = urllib.request.Request("https://api.github.com/a")
        with mock.patch.object(module.time, "sleep") as sleep:
            with self.assertRaises(urllib.error.HTTPError):
                module._open_with_retry(opener, request, timeout=1)
        self.assertEqual(opener.open.call_count, module.MAX_ATTEMPTS)
        self.assertEqual(sleep.call_count, module.MAX_ATTEMPTS - 1)


if __name__ == "__main__":
    unittest.main()

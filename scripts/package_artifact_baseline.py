#!/usr/bin/env python3
"""Fetch a previous comparable Android package manifest from GitHub Actions.

This module intentionally owns only the remote baseline lookup. Package manifest
creation and delta generation remain in package_artifact_metadata.py.
"""

from __future__ import annotations

import argparse
import io
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

METADATA_ARTIFACT_NAME = "package-build-metadata"
MANIFEST_SUFFIX = "build-manifest.json"
RETRYABLE_HTTP_STATUS = frozenset({429, 500, 502, 503, 504})
MAX_ATTEMPTS = 3
MAX_RUN_PAGES = 4
RUNS_PER_PAGE = 50
SENSITIVE_REDIRECT_HEADERS = frozenset(
    {"authorization", "x-github-api-version", "accept"}
)


class BaselineLookupError(RuntimeError):
    """Raised when historical baseline integrity or transport cannot be trusted."""


def _origin(url: str) -> tuple[str, str, int | None]:
    parsed = urllib.parse.urlsplit(url)
    return parsed.scheme.lower(), (parsed.hostname or "").lower(), parsed.port


def _strip_sensitive_redirect_headers(request: urllib.request.Request) -> None:
    for header, _ in tuple(request.header_items()):
        if header.lower() in SENSITIVE_REDIRECT_HEADERS:
            request.remove_header(header)


class GitHubArtifactRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Follow artifact redirects without leaking GitHub credentials cross-origin."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):  # type: ignore[no-untyped-def]
        old_scheme, _, _ = _origin(req.full_url)
        new_scheme, _, _ = _origin(newurl)
        if old_scheme == "https" and new_scheme != "https":
            raise urllib.error.HTTPError(
                newurl,
                403,
                "Refusing HTTPS artifact redirect to a non-HTTPS destination",
                headers,
                fp,
            )

        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        if redirected is None:
            return None

        if _origin(req.full_url) != _origin(newurl):
            _strip_sensitive_redirect_headers(redirected)
        return redirected


def _github_headers(token: str) -> dict[str, str]:
    return {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "android-local-llm-harness-package-baseline",
    }


def _retry_delay(exc: urllib.error.HTTPError, attempt: int) -> float:
    retry_after = exc.headers.get("Retry-After") if exc.headers else None
    if retry_after:
        try:
            return min(float(retry_after), 5.0)
        except ValueError:
            pass
    return min(float(2 ** (attempt - 1)), 5.0)


def _open_with_retry(opener, request: urllib.request.Request, timeout: int):  # type: ignore[no-untyped-def]
    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            return opener.open(request, timeout=timeout)
        except urllib.error.HTTPError as exc:
            if exc.code not in RETRYABLE_HTTP_STATUS or attempt == MAX_ATTEMPTS:
                raise
            time.sleep(_retry_delay(exc, attempt))
    raise AssertionError("retry loop exhausted unexpectedly")


def request_json(url: str, token: str) -> dict:
    request = urllib.request.Request(url, headers=_github_headers(token))
    opener = urllib.request.build_opener()
    with _open_with_retry(opener, request, timeout=30) as response:
        payload = json.load(response)
    if not isinstance(payload, dict):
        raise BaselineLookupError(f"GitHub API returned a non-object payload for {url}")
    return payload


def request_artifact_bytes(url: str, token: str) -> bytes:
    request = urllib.request.Request(url, headers=_github_headers(token))
    opener = urllib.request.build_opener(GitHubArtifactRedirectHandler())
    with _open_with_retry(opener, request, timeout=60) as response:
        return response.read()


def _workflow_runs(api: str, workflow: str, token: str) -> list[dict]:
    collected: list[dict] = []
    for page in range(1, MAX_RUN_PAGES + 1):
        query = urllib.parse.urlencode(
            {"status": "success", "per_page": str(RUNS_PER_PAGE), "page": str(page)}
        )
        payload = request_json(f"{api}/actions/workflows/{workflow}/runs?{query}", token)
        runs = payload.get("workflow_runs")
        if not isinstance(runs, list):
            raise BaselineLookupError("GitHub workflow-runs response is missing workflow_runs[]")
        if any(not isinstance(run, dict) or "id" not in run for run in runs):
            raise BaselineLookupError("GitHub workflow-runs response contains an invalid run")
        collected.extend(runs)
        if len(runs) < RUNS_PER_PAGE:
            break
    return collected


def _artifacts_for_run(api: str, run_id: object, token: str) -> list[dict]:
    payload = request_json(f"{api}/actions/runs/{run_id}/artifacts?per_page=100", token)
    artifacts = payload.get("artifacts")
    if not isinstance(artifacts, list):
        raise BaselineLookupError(f"GitHub artifacts response for run {run_id} is invalid")
    if any(not isinstance(item, dict) for item in artifacts):
        raise BaselineLookupError(f"GitHub artifacts response for run {run_id} contains invalid data")
    return artifacts


def _manifest_from_archive(archive: bytes, run_id: object) -> dict:
    try:
        with zipfile.ZipFile(io.BytesIO(archive)) as bundle:
            names = [name for name in bundle.namelist() if name.endswith(MANIFEST_SUFFIX)]
            if len(names) != 1:
                raise BaselineLookupError(
                    f"Package metadata artifact from run {run_id} must contain exactly one "
                    f"{MANIFEST_SUFFIX}; found {len(names)}"
                )
            payload = json.loads(bundle.read(names[0]).decode("utf-8"))
    except BaselineLookupError:
        raise
    except (zipfile.BadZipFile, KeyError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise BaselineLookupError(
            f"Package metadata artifact from run {run_id} is malformed: {type(exc).__name__}"
        ) from exc

    if not isinstance(payload, dict):
        raise BaselineLookupError(f"Package manifest from run {run_id} is not a JSON object")
    identity = payload.get("identity")
    lineage = payload.get("lineage")
    artifacts = payload.get("artifacts")
    if not isinstance(identity, dict) or not isinstance(lineage, dict) or not isinstance(artifacts, list):
        raise BaselineLookupError(
            f"Package manifest from run {run_id} is missing identity, lineage, or artifacts"
        )
    return payload


def comparable(previous: dict, channel: str, source_ref: str) -> bool:
    lineage = previous.get("lineage") or {}
    return lineage.get("channel") == channel and lineage.get("sourceRef") == source_ref


def fetch_previous(args: argparse.Namespace) -> int:
    token = os.environ.get(args.token_env, "")
    if not token:
        print(f"{args.token_env} is empty; previous package metadata unavailable.")
        return 0

    api = f"https://api.github.com/repos/{args.repository}"
    for run in _workflow_runs(api, args.workflow, token):
        run_id = run["id"]
        if str(run_id) == str(args.current_run_id):
            continue

        metadata = next(
            (
                item
                for item in _artifacts_for_run(api, run_id, token)
                if item.get("name") == METADATA_ARTIFACT_NAME and not item.get("expired")
            ),
            None,
        )
        if metadata is None:
            continue
        artifact_id = metadata.get("id")
        if artifact_id is None:
            raise BaselineLookupError(f"Package metadata artifact from run {run_id} has no id")

        try:
            archive = request_artifact_bytes(
                f"{api}/actions/artifacts/{artifact_id}/zip", token
            )
        except urllib.error.HTTPError as exc:
            if exc.code in (404, 410):
                print(
                    f"Previous package metadata artifact from run {run_id} is unavailable "
                    f"(HTTP {exc.code}); trying older successful runs."
                )
                continue
            raise

        previous = _manifest_from_archive(archive, run_id)
        if not comparable(previous, args.channel, args.source_ref):
            continue

        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(previous, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(f"Using previous comparable package build run {run_id}.")
        return 0

    print("No previous successful comparable package manifest found.")
    return 0


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--repository", required=True)
    result.add_argument("--workflow", default="package.yml")
    result.add_argument("--current-run-id", required=True)
    result.add_argument("--channel", required=True)
    result.add_argument("--source-ref", required=True)
    result.add_argument("--token-env", default="GITHUB_TOKEN")
    result.add_argument("--output", required=True)
    return result


def main() -> int:
    return fetch_previous(parser().parse_args())


if __name__ == "__main__":
    raise SystemExit(main())

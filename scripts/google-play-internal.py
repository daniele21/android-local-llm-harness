#!/usr/bin/env python3
"""Minimal Google Play Internal Testing helper using Android Publisher REST v3."""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen

API_ROOT = "https://androidpublisher.googleapis.com/androidpublisher/v3"
UPLOAD_ROOT = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"
TIMEOUT_SECONDS = 600


def token() -> str:
    value = os.environ.get("GOOGLE_PLAY_ACCESS_TOKEN", "").strip()
    if not value:
        raise SystemExit("GOOGLE_PLAY_ACCESS_TOKEN is required")
    return value


def request_json(method: str, url: str, *, body: object | None = None, raw: bytes | None = None) -> dict:
    headers = {"Authorization": f"Bearer {token()}"}
    data = raw
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    elif raw is not None:
        headers["Content-Type"] = "application/octet-stream"

    request = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            payload = response.read()
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Google Play API {method} {url} failed with HTTP {exc.code}: {detail}") from exc

    if not payload:
        return {}
    return json.loads(payload.decode("utf-8"))


def edit_base(package_name: str) -> str:
    return f"{API_ROOT}/applications/{quote(package_name, safe='')}/edits"


def create_edit(package_name: str) -> str:
    response = request_json("POST", edit_base(package_name), body={})
    edit_id = str(response.get("id", "")).strip()
    if not edit_id:
        raise RuntimeError("Google Play did not return an edit id")
    return edit_id


def delete_edit(package_name: str, edit_id: str) -> None:
    request_json("DELETE", f"{edit_base(package_name)}/{quote(edit_id, safe='')}")


def list_version_codes(package_name: str, edit_id: str) -> list[int]:
    base = f"{edit_base(package_name)}/{quote(edit_id, safe='')}"
    codes: list[int] = []
    for collection, key in (("bundles", "bundles"), ("apks", "apks")):
        response = request_json("GET", f"{base}/{collection}")
        for artifact in response.get(key, []):
            value = artifact.get("versionCode")
            if value is not None:
                codes.append(int(value))
    return codes


def next_version_code(package_name: str) -> int:
    edit_id = create_edit(package_name)
    try:
        current = list_version_codes(package_name, edit_id)
        return (max(current) + 1) if current else 1
    finally:
        delete_edit(package_name, edit_id)


def publish(package_name: str, aab_path: Path, track: str, release_name: str, expected_version_code: int | None) -> int:
    if not aab_path.is_file():
        raise SystemExit(f"AAB not found: {aab_path}")

    edit_id = create_edit(package_name)
    committed = False
    try:
        encoded_package = quote(package_name, safe="")
        encoded_edit = quote(edit_id, safe="")
        upload_url = (
            f"{UPLOAD_ROOT}/applications/{encoded_package}/edits/{encoded_edit}/bundles?uploadType=media"
        )
        bundle = request_json("POST", upload_url, raw=aab_path.read_bytes())
        uploaded_version_code = int(bundle["versionCode"])
        if expected_version_code is not None and uploaded_version_code != expected_version_code:
            raise RuntimeError(
                f"Uploaded versionCode {uploaded_version_code} does not match expected {expected_version_code}"
            )

        track_url = f"{edit_base(package_name)}/{encoded_edit}/tracks/{quote(track, safe='')}"
        request_json(
            "PUT",
            track_url,
            body={
                "track": track,
                "releases": [
                    {
                        "name": release_name,
                        "status": "completed",
                        "versionCodes": [str(uploaded_version_code)],
                    }
                ],
            },
        )
        request_json("POST", f"{edit_base(package_name)}/{encoded_edit}:commit", body={})
        committed = True
        return uploaded_version_code
    finally:
        if not committed:
            try:
                delete_edit(package_name, edit_id)
            except Exception as cleanup_error:  # noqa: BLE001
                print(f"warning: failed to delete uncommitted Play edit: {cleanup_error}", file=sys.stderr)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    next_parser = subparsers.add_parser("next-version-code")
    next_parser.add_argument("--package", required=True)

    publish_parser = subparsers.add_parser("publish")
    publish_parser.add_argument("--package", required=True)
    publish_parser.add_argument("--aab", required=True, type=Path)
    publish_parser.add_argument("--track", default="internal")
    publish_parser.add_argument("--release-name", required=True)
    publish_parser.add_argument("--expected-version-code", type=int)

    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.command == "next-version-code":
        print(next_version_code(args.package))
        return

    version_code = publish(
        args.package,
        args.aab,
        args.track,
        args.release_name,
        args.expected_version_code,
    )
    print(json.dumps({"package": args.package, "track": args.track, "versionCode": version_code}))


if __name__ == "__main__":
    main()

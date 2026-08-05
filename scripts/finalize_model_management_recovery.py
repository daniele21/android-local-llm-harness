#!/usr/bin/env python3
"""Finalize the temporary phone model-management recovery branch."""

from __future__ import annotations

import base64
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "apps/local-llm-phone-test/src"


def apply_base_compile_fixes() -> None:
    workflow = ROOT / ".github/workflows/fix-model-management-compile.yml"
    payload = re.search(
        r"echo '([^']+)' \| base64 -d > /tmp/fix.py",
        workflow.read_text(),
    )
    if payload is None:
        raise RuntimeError("Base compile fixer payload not found")
    source = base64.b64decode(payload.group(1))
    exec(compile(source, str(workflow), "exec"), {"__name__": "__main__"})


def publish_management_state() -> None:
    controller = (
        SOURCE_ROOT
        / "main/kotlin/io/github/daniele21/localllm/phonetest/PhoneModelDistributionController.kt"
    )
    text = controller.read_text()
    pattern = re.compile(
        r"(?P<indent>\s*)detail = runtime\?\.detail,\n"
        r"\s*installedModel = installedMetadata,\n"
        r"\s*\)",
    )
    replacement = (
        "            detail = managementDetails[stableId] ?: runtime?.detail,\n"
        "            installedModel = installedMetadata,\n"
        "            removalConfirmationPending = pendingRemovalStableId == stableId,\n"
        "        )"
    )
    text, replacements = pattern.subn(replacement, text, count=1)
    if replacements != 1:
        raise RuntimeError("Controller management-state anchor not found")
    controller.write_text(text)


def make_management_test_double_stateful() -> None:
    test = (
        SOURCE_ROOT
        / "test/kotlin/io/github/daniele21/localllm/phonetest/PhoneModelDistributionControllerTest.kt"
    )
    text = test.read_text()
    if 'detail = "verified",' not in text:
        raise RuntimeError("Verification detail anchor not found")
    text = text.replace(
        'detail = "verified",',
        'detail = "Model integrity verified",',
        1,
    )

    remove_pattern = re.compile(
        r"(?P<indent>\s*)override fun remove\(digest: ModelDigest\): "
        r"PhoneModelManagementOutcome =\n"
        r"\s*PhoneModelManagementOutcome\(\n"
        r"\s*operation = PhoneModelManagementOperation\.REMOVE,\n"
        r"\s*digest = digest,\n"
        r"\s*success = true,\n"
        r"\s*detail = \"removed\",\n"
        r"\s*\)",
    )
    replacement = (
        "\n                override fun remove(digest: ModelDigest): PhoneModelManagementOutcome {\n"
        "                    installedDigests.remove(digest)\n"
        "                    metadataRepository.remove(digest)\n"
        "                    return PhoneModelManagementOutcome(\n"
        "                        operation = PhoneModelManagementOperation.REMOVE,\n"
        "                        digest = digest,\n"
        "                        success = true,\n"
        "                        detail = \"Model removed\",\n"
        "                    )\n"
        "                }"
    )
    text, replacements = remove_pattern.subn(replacement, text, count=1)
    if replacements != 1:
        raise RuntimeError("Stateful management test-double anchor not found")
    test.write_text(text)


def main() -> int:
    apply_base_compile_fixes()
    publish_management_state()
    make_management_test_double_stateful()
    print("Model-management recovery source fixes applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

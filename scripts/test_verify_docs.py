#!/usr/bin/env python3
"""Deterministic tests for documentation governance validation."""

from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT_PATH = Path(__file__).with_name("verify-docs.py")
SPEC = importlib.util.spec_from_file_location("verify_docs", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("cannot load verify-docs.py")
verify_docs = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = verify_docs
SPEC.loader.exec_module(verify_docs)


def policy() -> dict[str, object]:
    return {
        "schema_version": 1,
        "estimated_token_characters": 4,
        "required_active_metadata": [
            "Status",
            "Document type",
            "Owner",
            "Canonical scope",
            "Read when",
            "Last reviewed",
        ],
        "document_types": {
            "feature-specification": {"max_lines": 10, "max_estimated_tokens": 1000},
            "current-state": {"max_lines": 10, "max_estimated_tokens": 1000},
            "workstream-state": {"max_lines": 10, "max_estimated_tokens": 1000},
        },
        "agent_guides": {
            "root": {"max_lines": 10, "max_estimated_tokens": 1000},
            "scoped": {"max_lines": 10, "max_estimated_tokens": 1000},
        },
        "historical_redirect_max_lines": 10,
        "duplicate_min_words": 10,
        "duplicate_min_characters": 40,
        "near_duplicate_threshold": 0.99,
        "oversize_baseline": {},
    }


def document(path: str, text: str, scope: str = "scope.one") -> object:
    return verify_docs.Document(
        path=Path(path),
        text=text,
        status="active",
        document_type="feature-specification",
        owner="test",
        canonical_scope=scope,
        read_when="testing",
        estimated_tokens=(len(text) + 3) // 4,
        line_count=len(text.splitlines()),
    )


class VerifyDocsTest(unittest.TestCase):
    def test_active_metadata_is_parsed(self) -> None:
        text = """# Feature

Status: active
Document type: feature-specification
Owner: test
Canonical scope: test.feature
Read when: testing metadata
Last reviewed: 2026-08-06
"""
        values = verify_docs.metadata(text)
        self.assertEqual("test.feature", values["Canonical scope"])
        self.assertEqual("testing metadata", values["Read when"])

    def test_budget_ratchet_allows_only_shrink(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            test_policy = policy()
            test_policy["oversize_baseline"] = {
                "docs/large.md": {"max_lines": 12, "max_estimated_tokens": 1000}
            }
            shrinking = document("docs/large.md", "\n".join(["line"] * 11))
            errors: list[str] = []
            verify_docs.validate_budgets(root, test_policy, {shrinking.path: shrinking}, errors)
            self.assertEqual([], errors)

            growing = document("docs/large.md", "\n".join(["line"] * 13))
            errors = []
            verify_docs.validate_budgets(root, test_policy, {growing.path: growing}, errors)
            self.assertTrue(any("reading budget exceeded" in error for error in errors))

    def test_policy_budget_cannot_be_relaxed(self) -> None:
        old = policy()
        current = policy()
        current["document_types"]["feature-specification"]["max_lines"] = 11
        errors = verify_docs.policy_ratchet_errors(old, current)
        self.assertTrue(any("relaxes document_types" in error for error in errors))

    def test_duplicate_canonical_scope_fails(self) -> None:
        first = document("docs/first.md", "first", "shared.scope")
        second = document("docs/second.md", "second", "shared.scope")
        errors: list[str] = []
        verify_docs.validate_scopes({first.path: first, second.path: second}, errors)
        self.assertTrue(any("already owned" in error for error in errors))

    def test_unreachable_active_document_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "docs").mkdir()
            (root / "docs/README.md").write_text("# Map\n\n[Visible](visible.md)\n", encoding="utf-8")
            (root / "docs/visible.md").write_text("# Visible\n", encoding="utf-8")
            (root / "docs/orphan.md").write_text("# Orphan\n", encoding="utf-8")
            visible = document("docs/visible.md", "visible", "visible.scope")
            orphan = document("docs/orphan.md", "orphan", "orphan.scope")
            errors: list[str] = []
            verify_docs.validate_reachability(
                root,
                {visible.path: visible, orphan.path: orphan},
                set(),
                errors,
            )
            self.assertTrue(any("docs/orphan.md" in error for error in errors))
            self.assertFalse(any("docs/visible.md" in error for error in errors))

    def test_long_exact_duplicate_paragraph_fails(self) -> None:
        paragraph = (
            "This deliberately long paragraph contains enough distinct words to cross the configured "
            "duplicate threshold and prove that active documents cannot repeat the same canonical explanation."
        )
        first = document("docs/first.md", paragraph, "first.scope")
        second = document("docs/second.md", paragraph, "second.scope")
        errors: list[str] = []
        warnings: list[str] = []
        verify_docs.validate_duplicates(policy(), {first.path: first, second.path: second}, errors, warnings)
        self.assertTrue(any("duplicated long paragraph" in error for error in errors))

    def test_historical_redirect_budget_is_enforced(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "docs").mkdir()
            content = """# Old plan

Status: historical
Document type: historical-plan
Owner: test
Last reviewed: 2026-08-06

[Replacement](replacement.md)
""" + "\n".join(["extra"] * 10)
            (root / "docs/old.md").write_text(content, encoding="utf-8")
            errors: list[str] = []
            verify_docs.parse_documents(root, policy(), errors)
            self.assertTrue(any("historical compatibility redirect" in error for error in errors))

    def test_duplicate_adr_number_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            adr_root = root / "docs/adr"
            adr_root.mkdir(parents=True)
            (adr_root / "README.md").write_text(
                "[One](0001-one.md)\n[Two](0001-two.md)\n",
                encoding="utf-8",
            )
            template = "# ADR 0001: Decision\n\n- Status: Accepted\n- Date: 2026-08-06\n"
            (adr_root / "0001-one.md").write_text(template, encoding="utf-8")
            (adr_root / "0001-two.md").write_text(template, encoding="utf-8")
            errors: list[str] = []
            verify_docs.validate_adrs(root, errors)
            self.assertTrue(any("duplicate ADR number" in error for error in errors))


if __name__ == "__main__":
    unittest.main()

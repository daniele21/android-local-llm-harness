#!/usr/bin/env python3
"""Disambiguate the second legacy roadmap checklist in the one-time migration."""

from pathlib import Path

path = Path(__file__).with_name("apply_dev_integration_docs_v2.py")
text = path.read_text()
old = '''    base.replace_once(
        "docs/roadmap.md",
        "- [ ] rebase or retarget onto `main` after PR #31 is merged",
        "- [ ] close PR #34 as superseded after the focused PR #53 recovery is merged into `dev`",
    )'''
new = '''    replace_first(
        "docs/roadmap.md",
        "- [ ] rebase or retarget onto `main` after PR #31 is merged",
        "- [ ] close PR #34 as superseded after the focused PR #53 recovery is merged into `dev`",
    )'''
if text.count(old) != 1:
    raise RuntimeError(f"Expected one v2 migration anchor, found {text.count(old)}")
path.write_text(text.replace(old, new, 1))
print("Roadmap migration ambiguity resolved.")

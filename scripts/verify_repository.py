#!/usr/bin/env python3
"""Structural checks for the adopted repo-template-sw baseline."""
from __future__ import annotations
import argparse, json, sys
from pathlib import Path

CORE_SKILLS = (
    "plan-workstream", "structured-change", "design-product-experience",
    "validate-change", "preflight-change", "remote-preflight",
    "finalize-workstream", "review-reference-quality",
)
REQUIRED = (
    "README.md", "AGENTS.md", "CONTRIBUTING.md", "SECURITY.md",
    "EXECUTION-CAPABILITY-CONTRACT.md", "E2E-ENVIRONMENT-CONTRACT.md",
    ".engineering/baseline.json", ".engineering/documentation-policy.json",
    ".engineering/commands.json", ".engineering/e2e.json",
    ".github/workflows/repository-health.yml", "docs/README.md",
    "docs/architecture.md", "docs/current-state.md", "docs/features/README.md",
    "docs/adr/README.md", "docs/workstreams/README.md",
    "scripts/verify_operations.py", "scripts/verify_e2e.py",
    "scripts/verify_product_experience.py", "scripts/detect_ci_scope.py",
)
PR_TEMPLATES = (".github/PULL_REQUEST_TEMPLATE.md", ".github/pull_request_template.md")
PLACEHOLDERS = ("<PROJECT_NAME>", "<REPLACE_WITH_", "<DESCRIBE_", "<LIST_")

def main() -> int:
    parser = argparse.ArgumentParser(); parser.add_argument("--root", default="."); parser.add_argument("--template-mode", action="store_true")
    args = parser.parse_args(); root = Path(args.root).resolve(); errors=[]; warnings=[]
    for rel in REQUIRED:
        if not (root / rel).is_file(): errors.append(f"missing required file: {rel}")
    if not any((root / rel).is_file() for rel in PR_TEMPLATES): errors.append("missing required pull request template under .github/")
    for name in CORE_SKILLS:
        if not (root / "skills" / name / "SKILL.md").is_file(): errors.append(f"missing core skill: skills/{name}/SKILL.md")
    path = root / ".engineering" / "baseline.json"
    try: baseline = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc: errors.append(f"invalid baseline.json: {exc}"); baseline={}
    if baseline:
        standard = baseline.get("standard", {})
        if baseline.get("schema_version") != 1: errors.append("baseline schema_version must be 1")
        if standard.get("source") != "daniele21/repo-template-sw": errors.append("baseline standard.source must identify daniele21/repo-template-sw")
        if standard.get("version") != "0.9.1": errors.append("baseline standard.version must be 0.9.1")
        if baseline.get("target_level") not in {"L0", "L1", "L2"}: errors.append("target_level must be L0, L1 or L2")
        if not isinstance(baseline.get("profiles"), list): errors.append("profiles must be a list")
        skills = baseline.get("skills", {})
        for name in CORE_SKILLS:
            entry = skills.get(name)
            if not isinstance(entry, dict): errors.append(f"baseline missing skill metadata: {name}"); continue
            if not entry.get("source_version"): errors.append(f"skill {name} missing source_version")
            if not isinstance(entry.get("customized"), bool): errors.append(f"skill {name} customized must be boolean")
    if not args.template_mode:
        for rel in ("README.md", "AGENTS.md", "docs/architecture.md", "SECURITY.md"):
            p=root/rel
            if p.is_file():
                text=p.read_text(encoding="utf-8")
                for marker in PLACEHOLDERS:
                    if marker in text: errors.append(f"unresolved adopter placeholder {marker} in {rel}")
    present=[name for name in ("node_modules", ".venv", "build", "dist", "__pycache__") if (root/name).exists()]
    if present: warnings.append("generated/local directories present in worktree: " + ", ".join(present))
    print("Repository baseline check"); print(f"root: {root}")
    for warning in warnings: print(f"WARN: {warning}")
    for error in errors: print(f"FAIL: {error}")
    if errors: print(f"RESULT: FAIL ({len(errors)} error(s), {len(warnings)} warning(s))"); return 1
    print(f"RESULT: PASS ({len(warnings)} warning(s))"); return 0

if __name__ == "__main__": sys.exit(main())

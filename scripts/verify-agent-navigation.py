#!/usr/bin/env python3
"""Validate coding-agent routing and repository module discoverability."""
from __future__ import annotations
import re, sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
ROOT_GUIDE = ROOT / "AGENTS.md"
README = ROOT / "README.md"
SETTINGS = ROOT / "settings.gradle.kts"
IGNORED_PARTS = {".git", ".gradle", ".idea", "build", "third_party"}
REQUIRED_HEADINGS = {
    "## Read only what the task requires",
    "## Durable invariants",
    "## Ownership routing",
    "## Delivery model",
    "## Validation model",
    "## Evidence reuse",
    "## E2E / fidelity",
    "## Parallel development",
    "## Documentation",
    "## Failure discipline",
}
REQUIRED_ROUTING_TARGETS = {
    ".engineering/commands.json",
    ".engineering/e2e.json",
    "skills/validate-change/SKILL.md",
    "skills/preflight-change/SKILL.md",
    "skills/remote-preflight/SKILL.md",
    "docs/current-state.md",
}
MARKDOWN_LINK = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
GRADLE_MODULE = re.compile(r'"(:[^"\n]+)"')

def discover_guides() -> list[Path]:
    return sorted(path for path in ROOT.rglob("AGENTS.md") if not any(part in IGNORED_PARTS for part in path.relative_to(ROOT).parts))

def local_link_target(raw_target: str) -> str | None:
    target = raw_target.strip().split(maxsplit=1)[0].strip("<>")
    if not target or target.startswith(("#", "http://", "https://", "mailto:")): return None
    return unquote(target.split("#", maxsplit=1)[0])

def validate_links(guide: Path, errors: list[str]) -> None:
    text = guide.read_text(encoding="utf-8")
    for raw_target in MARKDOWN_LINK.findall(text):
        target = local_link_target(raw_target)
        if target is None: continue
        resolved = (guide.parent / target).resolve()
        try: resolved.relative_to(ROOT)
        except ValueError:
            errors.append(f"{guide.relative_to(ROOT)}: link escapes repository: {raw_target}"); continue
        if not resolved.exists(): errors.append(f"{guide.relative_to(ROOT)}: missing link target: {raw_target}")

def validate_root_guide(errors: list[str]) -> None:
    if not ROOT_GUIDE.is_file(): errors.append("missing root AGENTS.md"); return
    text = ROOT_GUIDE.read_text(encoding="utf-8")
    for heading in sorted(REQUIRED_HEADINGS):
        if heading not in text: errors.append(f"AGENTS.md: missing required heading: {heading}")
    for target in sorted(REQUIRED_ROUTING_TARGETS):
        if target not in text: errors.append(f"AGENTS.md: missing canonical routing target: {target}")
    for principle in ("ITERATION", "INTEGRATION", "RELEASE", "risk dimensions", "required gates", "stacked publication"):
        if principle not in text: errors.append(f"AGENTS.md: missing 0.9 delivery principle: {principle}")

def validate_module_discoverability(guides: list[Path], errors: list[str]) -> None:
    if not SETTINGS.is_file(): errors.append("missing settings.gradle.kts"); return
    if not README.is_file(): errors.append("missing README.md"); return
    modules = sorted(set(GRADLE_MODULE.findall(SETTINGS.read_text(encoding="utf-8"))))
    if not modules: errors.append("settings.gradle.kts: no Gradle modules discovered"); return
    navigation_text = "\n".join([README.read_text(encoding="utf-8")] + [guide.read_text(encoding="utf-8") for guide in guides])
    for module in modules:
        module_path = module.lstrip(":").replace(":", "/")
        if f"`{module_path}`" not in navigation_text and module_path not in navigation_text:
            errors.append(f"configured Gradle module is not discoverable in README or an agent guide: {module} -> {module_path}")

def validate_filename_casing(errors: list[str]) -> None:
    for path in ROOT.rglob("*.md"):
        relative = path.relative_to(ROOT)
        if any(part in IGNORED_PARTS for part in relative.parts): continue
        if path.name.lower() == "agents.md" and path.name != "AGENTS.md": errors.append(f"use exact AGENTS.md casing instead of {relative.as_posix()}")
        if path.name.lower() == "agent.md": errors.append(f"remove competing guide {relative.as_posix()}; use AGENTS.md")

def main() -> int:
    errors: list[str] = []
    validate_root_guide(errors); validate_filename_casing(errors)
    guides = discover_guides()
    for guide in guides: validate_links(guide, errors)
    validate_module_discoverability(guides, errors)
    if errors:
        print("Agent navigation validation failed:", file=sys.stderr)
        for error in errors: print(f"- {error}", file=sys.stderr)
        return 1
    print(f"Agent navigation is valid: {len(guides)} guide(s), all configured modules discoverable.")
    return 0

if __name__ == "__main__": raise SystemExit(main())

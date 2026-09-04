#!/usr/bin/env python3
"""Validate documentation lifecycle, progressive disclosure and reading cost."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from collections import defaultdict, deque
from dataclasses import dataclass
from datetime import date
from difflib import SequenceMatcher
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
ARCHIVE_PARTS = ("docs", "archive")
POLICY_PATH = Path("docs/documentation-policy.json")

MARKDOWN_ROOTS = (
    Path("README.md"),
    Path("BRANCHING.md"),
    Path("AGENTS.md"),
)

REQUIRED_ACTIVE_SOURCES = (
    Path("docs/README.md"),
    Path("docs/current-state.md"),
    Path("docs/roadmap.md"),
    Path("docs/implementation-plan.md"),
    Path("docs/definition-of-done.md"),
    Path("docs/releases/harness-0.5.md"),
)

VOLATILE_ACTIVE_MARKERS = (
    "Active implementation branch:",
    "Active pull request:",
    "local integration candidate",
    "review PR pending",
    "origin/dev` a `2850d03",
    "Current remote integration baseline:\n\n```text\n2850d03",
)

LINK_PATTERN = re.compile(r"!?(?:\[[^\]]*\])\(([^)]+)\)")
METADATA_PATTERN = re.compile(r"^([A-Z][A-Za-z ]+):\s*(.+?)\s*$", re.MULTILINE)
ADR_FILE_PATTERN = re.compile(r"^(\d{4})-[a-z0-9-]+\.md$")
ADR_HEADING_PATTERN = re.compile(r"^# ADR (\d{4})(?:\s*[:—-])")
ADR_STATUS_PATTERN = re.compile(r"^- Status:\s*(Proposed|Accepted|Superseded|Deprecated)\s*$", re.MULTILINE)
ADR_DATE_PATTERN = re.compile(r"^- Date:\s*\d{4}-\d{2}-\d{2}\s*$", re.MULTILINE)
SHA_PATTERN = re.compile(r"^[0-9a-fA-F]{7,40}$")
SCOPE_PATTERN = re.compile(r"^[a-z0-9]+(?:[.-][a-z0-9]+)+$")


@dataclass(frozen=True)
class Document:
    path: Path
    text: str
    status: str
    document_type: str
    owner: str
    canonical_scope: str
    read_when: str
    estimated_tokens: int
    line_count: int


def relative(path: Path, root: Path) -> Path:
    return path.resolve().relative_to(root.resolve())


def is_archive_path(path: Path) -> bool:
    parts = path.parts
    return len(parts) >= 2 and parts[:2] == ARCHIVE_PARTS


def is_adr_record(path: Path) -> bool:
    return path.parent == Path("docs/adr") and ADR_FILE_PATTERN.fullmatch(path.name) is not None


def repository_agent_guides(root: Path) -> list[Path]:
    return sorted(
        path
        for path in root.rglob("AGENTS.md")
        if not {".git", "build", "third_party"}.intersection(path.relative_to(root).parts)
    )


def markdown_files(root: Path) -> list[Path]:
    files = [root / path for path in MARKDOWN_ROOTS if (root / path).exists()]
    files.extend(sorted((root / "docs").rglob("*.md")))
    files.extend(repository_agent_guides(root))
    return sorted(set(files))


def load_policy(root: Path) -> dict[str, object]:
    path = root / POLICY_PATH
    if not path.is_file():
        raise ValueError(f"missing documentation policy: {POLICY_PATH}")
    try:
        policy = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"invalid documentation policy: {error}") from error
    if policy.get("schema_version") != 1:
        raise ValueError("unsupported documentation policy schema_version")
    return policy


def content_without_fenced_code(text: str) -> str:
    output: list[str] = []
    in_fence = False
    for line in text.splitlines():
        if line.lstrip().startswith("```"):
            in_fence = not in_fence
            continue
        if not in_fence:
            output.append(line)
    return "\n".join(output)


def metadata(text: str) -> dict[str, str]:
    return {key: value for key, value in METADATA_PATTERN.findall(content_without_fenced_code(text))}


def estimated_tokens(text: str, policy: dict[str, object]) -> int:
    characters = int(policy.get("estimated_token_characters", 4))
    return (len(text) + characters - 1) // characters


def local_link_target(source: Path, raw_target: str, root: Path) -> Path | None:
    target = raw_target.strip()
    if target.startswith("<") and target.endswith(">"):
        target = target[1:-1]
    target = target.split(maxsplit=1)[0]
    parsed = urlsplit(target)
    if parsed.scheme or parsed.netloc or target.startswith("#"):
        return None
    path_text = unquote(parsed.path)
    if not path_text:
        return None
    if path_text.startswith("/"):
        return root / path_text.lstrip("/")
    return (source.parent / path_text).resolve()


def markdown_links(source: Path, text: str, root: Path) -> list[Path]:
    targets: list[Path] = []
    for match in LINK_PATTERN.finditer(content_without_fenced_code(text)):
        target = local_link_target(source, match.group(1), root)
        if target is not None:
            targets.append(target)
    return targets


def validate_links(path: Path, text: str, root: Path) -> list[str]:
    errors: list[str] = []
    for target in markdown_links(path, text, root):
        try:
            target.relative_to(root.resolve())
        except ValueError:
            errors.append(f"{relative(path, root)}: link escapes repository: {target}")
            continue
        if not target.exists():
            errors.append(f"{relative(path, root)}: missing local link target: {relative(target, root)}")
    return errors


def parse_documents(
    root: Path,
    policy: dict[str, object],
    errors: list[str],
) -> dict[Path, Document]:
    documents: dict[Path, Document] = {}
    required_metadata = [str(value) for value in policy.get("required_active_metadata", [])]
    allowed_types = policy.get("document_types", {})
    if not isinstance(allowed_types, dict):
        errors.append("documentation policy: document_types must be an object")
        allowed_types = {}

    for absolute in sorted((root / "docs").rglob("*.md")):
        path = relative(absolute, root)
        text = absolute.read_text(encoding="utf-8")
        values = metadata(text)

        if is_archive_path(path):
            if values.get("Status", "").casefold() != "historical":
                errors.append(f"{path}: archived document must contain 'Status: historical'")
            missing = [key for key in ("Document type", "Owner", "Last reviewed") if not values.get(key)]
            if missing:
                errors.append(f"{path}: missing historical metadata: {', '.join(missing)}")
            elif not valid_iso_date(values["Last reviewed"]):
                errors.append(f"{path}: Last reviewed must be a valid YYYY-MM-DD date")
            continue

        if is_adr_record(path):
            continue

        status = values.get("Status", "")
        if not status:
            errors.append(f"{path}: missing Status metadata")
            continue

        if status == "historical":
            missing = [key for key in ("Document type", "Owner", "Last reviewed") if not values.get(key)]
            if missing:
                errors.append(f"{path}: missing historical metadata: {', '.join(missing)}")
            elif not valid_iso_date(values["Last reviewed"]):
                errors.append(f"{path}: Last reviewed must be a valid YYYY-MM-DD date")
            max_lines = int(policy.get("historical_redirect_max_lines", 30))
            line_count = len(text.splitlines())
            if line_count > max_lines:
                errors.append(
                    f"{path}: historical compatibility redirect has {line_count} lines; maximum is {max_lines}"
                )
            if not markdown_links(absolute, text, root):
                errors.append(f"{path}: historical compatibility redirect must link to an active replacement or archive record")
            continue

        if status != "active":
            errors.append(f"{path}: unsupported Status metadata: {status!r}")
            continue

        missing = [key for key in required_metadata if not values.get(key)]
        if missing:
            errors.append(f"{path}: missing active metadata: {', '.join(missing)}")
            continue

        document_type = values["Document type"]
        if document_type not in allowed_types:
            errors.append(f"{path}: unsupported active Document type: {document_type!r}")
            continue

        if SCOPE_PATTERN.fullmatch(values["Canonical scope"]) is None:
            errors.append(f"{path}: Canonical scope must be a lowercase dotted identifier")
            continue
        if not valid_iso_date(values["Last reviewed"]):
            errors.append(f"{path}: Last reviewed must be a valid YYYY-MM-DD date")
            continue

        document = Document(
            path=path,
            text=text,
            status=status,
            document_type=document_type,
            owner=values["Owner"],
            canonical_scope=values["Canonical scope"],
            read_when=values["Read when"],
            estimated_tokens=estimated_tokens(text, policy),
            line_count=len(text.splitlines()),
        )
        documents[path] = document

    return documents


def valid_iso_date(value: str) -> bool:
    try:
        date.fromisoformat(value)
    except ValueError:
        return False
    return re.fullmatch(r"\d{4}-\d{2}-\d{2}", value) is not None


def validate_adrs(root: Path, errors: list[str]) -> set[Path]:
    adr_root = root / "docs/adr"
    index_path = adr_root / "README.md"
    index_text = index_path.read_text(encoding="utf-8") if index_path.exists() else ""
    seen_numbers: dict[str, Path] = {}
    accepted_paths: set[Path] = set()

    for absolute in sorted(adr_root.glob("*.md")):
        path = relative(absolute, root)
        match = ADR_FILE_PATTERN.fullmatch(path.name)
        if match is None:
            continue
        number = match.group(1)
        text = absolute.read_text(encoding="utf-8")
        heading = ADR_HEADING_PATTERN.match(text)
        if heading is None or heading.group(1) != number:
            errors.append(f"{path}: ADR heading number must match filename number {number}")
        if number in seen_numbers:
            errors.append(f"{path}: duplicate ADR number {number}; first used by {seen_numbers[number]}")
        else:
            seen_numbers[number] = path
        status = ADR_STATUS_PATTERN.search(text)
        if status is None:
            errors.append(f"{path}: ADR must declare a supported '- Status:' value")
        elif status.group(1) in {"Accepted", "Proposed"}:
            accepted_paths.add(path)
        if ADR_DATE_PATTERN.search(text) is None:
            errors.append(f"{path}: ADR must declare '- Date: YYYY-MM-DD'")
        if f"]({path.name})" not in index_text:
            errors.append(f"{path}: ADR is not listed in docs/adr/README.md")

    return accepted_paths


def validate_scopes(documents: dict[Path, Document], errors: list[str]) -> None:
    scopes: dict[str, Path] = {}
    repository_states: list[Path] = []
    for document in documents.values():
        scope = document.canonical_scope.casefold()
        if scope in scopes:
            errors.append(
                f"{document.path}: canonical scope {document.canonical_scope!r} is already owned by {scopes[scope]}"
            )
        else:
            scopes[scope] = document.path
        if document.document_type == "current-state" and document.owner == "repository":
            repository_states.append(document.path)
        if document.document_type == "workstream-state" and "current-state.md" not in document.text:
            errors.append(f"{document.path}: workstream-state must link to docs/current-state.md")

    if repository_states != [Path("docs/current-state.md")]:
        errors.append(
            "documentation graph must have exactly one repository current-state owner at docs/current-state.md"
        )


def validate_budgets(
    root: Path,
    policy: dict[str, object],
    documents: dict[Path, Document],
    errors: list[str],
) -> None:
    type_policies = policy.get("document_types", {})
    baselines = policy.get("oversize_baseline", {})
    if not isinstance(type_policies, dict) or not isinstance(baselines, dict):
        return

    for document in documents.values():
        limits = type_policies.get(document.document_type, {})
        if not isinstance(limits, dict):
            continue
        max_lines = int(limits.get("max_lines", 0))
        max_tokens = int(limits.get("max_estimated_tokens", 0))
        over_lines = max_lines > 0 and document.line_count > max_lines
        over_tokens = max_tokens > 0 and document.estimated_tokens > max_tokens
        if not over_lines and not over_tokens:
            continue

        baseline = baselines.get(document.path.as_posix())
        if isinstance(baseline, dict):
            baseline_lines = int(baseline.get("max_lines", 0))
            baseline_tokens = int(baseline.get("max_estimated_tokens", 0))
            if document.line_count <= baseline_lines and document.estimated_tokens <= baseline_tokens:
                continue

        errors.append(
            f"{document.path}: reading budget exceeded: {document.line_count}/{max_lines} lines, "
            f"{document.estimated_tokens}/{max_tokens} estimated tokens"
        )

    guide_limits = policy.get("agent_guides", {})
    if not isinstance(guide_limits, dict):
        return
    guides = repository_agent_guides(root)
    for guide in guides:
        if not guide.exists():
            continue
        kind = "root" if guide == root / "AGENTS.md" else "scoped"
        limits = guide_limits.get(kind, {})
        if not isinstance(limits, dict):
            continue
        text = guide.read_text(encoding="utf-8")
        line_count = len(text.splitlines())
        token_count = estimated_tokens(text, policy)
        max_lines = int(limits.get("max_lines", 0))
        max_tokens = int(limits.get("max_estimated_tokens", 0))
        if line_count > max_lines or token_count > max_tokens:
            errors.append(
                f"{relative(guide, root)}: agent-guide budget exceeded: {line_count}/{max_lines} lines, "
                f"{token_count}/{max_tokens} estimated tokens"
            )


def validate_reachability(
    root: Path,
    documents: dict[Path, Document],
    adr_paths: set[Path],
    errors: list[str],
) -> None:
    markdown = markdown_files(root)
    graph: dict[Path, set[Path]] = defaultdict(set)
    for absolute in markdown:
        source = relative(absolute, root)
        text = absolute.read_text(encoding="utf-8")
        for target in markdown_links(absolute, text, root):
            if target.suffix.casefold() != ".md" or not target.exists():
                continue
            graph[source].add(relative(target, root))

    entrypoints = {
        Path("README.md"),
        Path("AGENTS.md"),
        Path("docs/README.md"),
    }
    entrypoints.update(relative(path, root) for path in repository_agent_guides(root))

    reached: set[Path] = set()
    queue: deque[Path] = deque(entrypoints)
    while queue:
        path = queue.popleft()
        if path in reached:
            continue
        reached.add(path)
        queue.extend(graph.get(path, set()) - reached)

    required = set(documents) | adr_paths
    for path in sorted(required - reached):
        errors.append(f"{path}: active document is not reachable from a documentation entrypoint")


def normalized_paragraphs(document: Document, policy: dict[str, object]) -> list[str]:
    minimum_words = int(policy.get("duplicate_min_words", 40))
    minimum_characters = int(policy.get("duplicate_min_characters", 240))
    text = content_without_fenced_code(document.text)
    paragraphs: list[str] = []
    for raw in re.split(r"\n\s*\n", text):
        stripped = raw.strip()
        if not stripped or stripped.startswith(("#", "|")):
            continue
        if all(line.lstrip().startswith(("-", "*", ">")) for line in stripped.splitlines()):
            continue
        normalized = re.sub(r"[`*_\[\]()]", "", stripped)
        normalized = re.sub(r"\s+", " ", normalized).strip().casefold()
        if len(normalized) < minimum_characters or len(normalized.split()) < minimum_words:
            continue
        paragraphs.append(normalized)
    return paragraphs


def validate_duplicates(
    policy: dict[str, object],
    documents: dict[Path, Document],
    errors: list[str],
    warnings: list[str],
) -> None:
    exact: dict[str, list[Path]] = defaultdict(list)
    candidates: list[tuple[Path, str]] = []
    for document in documents.values():
        for paragraph in normalized_paragraphs(document, policy):
            exact[paragraph].append(document.path)
            candidates.append((document.path, paragraph))

    for paths in exact.values():
        unique = sorted(set(paths))
        if len(unique) > 1:
            errors.append("duplicated long paragraph across active documents: " + ", ".join(map(str, unique)))

    threshold = float(policy.get("near_duplicate_threshold", 0.94))
    advisory_limit = 10
    for index, (left_path, left) in enumerate(candidates):
        if len(warnings) >= advisory_limit:
            break
        for right_path, right in candidates[index + 1 :]:
            if left_path == right_path or left == right:
                continue
            length_ratio = min(len(left), len(right)) / max(len(left), len(right))
            if length_ratio < threshold:
                continue
            score = SequenceMatcher(None, left, right, autojunk=False).ratio()
            if score >= threshold:
                warnings.append(
                    f"possible near-duplicate paragraph ({score:.0%}): {left_path}, {right_path}"
                )


def validate_active_hygiene(documents: dict[Path, Document], errors: list[str]) -> None:
    for document in documents.values():
        for marker in VOLATILE_ACTIVE_MARKERS:
            if marker.casefold() in document.text.casefold():
                errors.append(f"{document.path}: obsolete active-state marker: {marker!r}")


def validate_repository(
    root: Path,
    policy: dict[str, object],
) -> tuple[list[str], list[str], dict[Path, Document], set[Path]]:
    errors: list[str] = []
    warnings: list[str] = []

    rgignore = root / ".rgignore"
    if not rgignore.is_file() or "docs/archive/**" not in rgignore.read_text(encoding="utf-8"):
        errors.append(".rgignore must exclude docs/archive/** from normal coding-agent search")

    for required in REQUIRED_ACTIVE_SOURCES:
        if not (root / required).exists():
            errors.append(f"missing required active source: {required}")

    for path in markdown_files(root):
        text = path.read_text(encoding="utf-8")
        errors.extend(validate_links(path, text, root))
        if path.name == "AGENTS.md" and "docs/archive/" in text:
            errors.append(f"{relative(path, root)}: coding-agent guides must not route normal work to docs/archive")

    documents = parse_documents(root, policy, errors)
    adr_paths = validate_adrs(root, errors)
    validate_scopes(documents, errors)
    validate_budgets(root, policy, documents, errors)
    validate_reachability(root, documents, adr_paths, errors)
    validate_duplicates(policy, documents, errors, warnings)
    validate_active_hygiene(documents, errors)
    return sorted(set(errors)), sorted(set(warnings)), documents, adr_paths


def git_output(root: Path, arguments: list[str]) -> str | None:
    result = subprocess.run(
        ["git", *arguments],
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    return result.stdout if result.returncode == 0 else None


def policy_ratchet_errors(old: dict[str, object], current: dict[str, object]) -> list[str]:
    errors: list[str] = []

    old_required = {str(value) for value in old.get("required_active_metadata", [])}
    current_required = {str(value) for value in current.get("required_active_metadata", [])}
    for removed in sorted(old_required - current_required):
        errors.append(f"documentation policy weakens required metadata by removing {removed!r}")

    for section in ("document_types", "agent_guides"):
        old_section = old.get(section, {})
        current_section = current.get(section, {})
        if not isinstance(old_section, dict) or not isinstance(current_section, dict):
            continue
        for name, old_limits in old_section.items():
            current_limits = current_section.get(name)
            if not isinstance(old_limits, dict):
                continue
            if not isinstance(current_limits, dict):
                errors.append(f"documentation policy removes governed {section} entry {name!r}")
                continue
            for limit_name in ("max_lines", "max_estimated_tokens"):
                old_value = int(old_limits.get(limit_name, 0))
                current_value = int(current_limits.get(limit_name, 0))
                if current_value > old_value:
                    errors.append(
                        f"documentation policy relaxes {section}.{name}.{limit_name}: "
                        f"{old_value} -> {current_value}"
                    )

    stricter_when_lower = (
        "estimated_token_characters",
        "historical_redirect_max_lines",
        "duplicate_min_words",
        "duplicate_min_characters",
        "near_duplicate_threshold",
    )
    for name in stricter_when_lower:
        old_value = float(old.get(name, 0))
        current_value = float(current.get(name, 0))
        if current_value > old_value:
            errors.append(f"documentation policy relaxes {name}: {old_value:g} -> {current_value:g}")

    old_baselines = old.get("oversize_baseline", {})
    current_baselines = current.get("oversize_baseline", {})
    if isinstance(old_baselines, dict) and isinstance(current_baselines, dict):
        for path, current_limits in current_baselines.items():
            old_limits = old_baselines.get(path)
            if not isinstance(current_limits, dict):
                continue
            if not isinstance(old_limits, dict):
                errors.append(f"documentation policy adds a new oversize baseline for {path}; split the document instead")
                continue
            for limit_name in ("max_lines", "max_estimated_tokens"):
                if int(current_limits.get(limit_name, 0)) > int(old_limits.get(limit_name, 0)):
                    errors.append(
                        f"documentation policy increases oversize baseline {path}.{limit_name}"
                    )

    return errors


def validate_policy_against_base(
    root: Path,
    base: str | None,
    current: dict[str, object],
) -> list[str]:
    if base is None or SHA_PATTERN.fullmatch(base) is None:
        return []
    raw = git_output(root, ["show", f"{base}:{POLICY_PATH.as_posix()}"])
    if raw is None:
        return []
    try:
        old = json.loads(raw)
    except json.JSONDecodeError:
        return [f"base documentation policy at {base} is invalid JSON"]
    if not isinstance(old, dict):
        return [f"base documentation policy at {base} must be an object"]
    return policy_ratchet_errors(old, current)


def base_document_snapshot(
    root: Path,
    base: str,
    policy: dict[str, object],
) -> tuple[set[Path], int, set[str]] | None:
    if not SHA_PATTERN.fullmatch(base):
        return None
    if git_output(root, ["rev-parse", "--verify", f"{base}^{{commit}}"] ) is None:
        return None
    listing = git_output(root, ["ls-tree", "-r", "--name-only", base, "--", "docs"])
    if listing is None:
        return None

    paths: set[Path] = set()
    tokens = 0
    scopes: set[str] = set()
    for raw_path in listing.splitlines():
        path = Path(raw_path)
        if path.suffix != ".md" or is_archive_path(path):
            continue
        text = git_output(root, ["show", f"{base}:{raw_path}"])
        if text is None:
            continue
        values = metadata(text)
        if values.get("Status") == "historical":
            continue
        paths.add(path)
        tokens += estimated_tokens(text, policy)
        if values.get("Canonical scope"):
            scopes.add(values["Canonical scope"])
    return paths, tokens, scopes


def changed_paths(root: Path, base: str) -> set[Path]:
    output = git_output(root, ["diff", "--name-only", base, "--", "docs", "AGENTS.md", "*/AGENTS.md"])
    if output is None:
        return set()
    return {Path(line) for line in output.splitlines() if line}


def print_cost_report(
    root: Path,
    policy: dict[str, object],
    documents: dict[Path, Document],
    adr_paths: set[Path],
    warnings: list[str],
    base: str | None,
) -> None:
    adr_tokens = sum(
        estimated_tokens((root / path).read_text(encoding="utf-8"), policy)
        for path in adr_paths
    )
    current_tokens = sum(document.estimated_tokens for document in documents.values()) + adr_tokens
    current_paths = set(documents) | adr_paths
    print("Documentation cost report:")
    print(f"- active documents: {len(current_paths)} (including {len(adr_paths)} ADRs)")
    print(f"- active estimated tokens: {current_tokens}")

    if base:
        snapshot = base_document_snapshot(root, base, policy)
        if snapshot is None:
            print(f"- base comparison: unavailable for {base}")
        else:
            old_paths, old_tokens, old_scopes = snapshot
            delta = current_tokens - old_tokens
            sign = "+" if delta >= 0 else ""
            print(f"- active document delta: {len(old_paths)} -> {len(current_paths)}")
            print(f"- estimated token delta: {old_tokens} -> {current_tokens} ({sign}{delta})")
            print(f"- new active documents: {len(current_paths - old_paths)}")
            print(f"- active documents retired or archived: {len(old_paths - current_paths)}")
            scopes = {document.canonical_scope for document in documents.values()}
            print(f"- canonical scopes added: {len(scopes - old_scopes)}")
            changed = changed_paths(root, base)
            changed_active = [document for path, document in documents.items() if path in changed]
            print(f"- changed documentation and agent files: {len(changed)}")
            if changed_active:
                largest = max(changed_active, key=lambda item: item.estimated_tokens)
                print(
                    f"- largest changed active document: {largest.path} "
                    f"({largest.line_count} lines, {largest.estimated_tokens} estimated tokens)"
                )

    largest_documents = sorted(documents.values(), key=lambda item: item.estimated_tokens, reverse=True)[:5]
    print("- largest active documents:")
    for document in largest_documents:
        print(f"  - {document.path}: {document.line_count} lines, {document.estimated_tokens} estimated tokens")

    root_guide = root / "AGENTS.md"
    if root_guide.exists():
        root_tokens = estimated_tokens(root_guide.read_text(encoding="utf-8"), policy)
        print(f"- mandatory root agent guide: {root_tokens} estimated tokens")
        for guide in (path for path in repository_agent_guides(root) if path != root_guide):
            scope_tokens = estimated_tokens(guide.read_text(encoding="utf-8"), policy)
            print(f"  - with {relative(guide, root)}: {root_tokens + scope_tokens} estimated tokens")

    if warnings:
        print("- advisories:")
        for warning in warnings:
            print(f"  - {warning}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", help="base commit SHA used for documentation cost delta reporting")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        policy = load_policy(ROOT)
    except ValueError as error:
        print(f"Documentation validation failed:\n- {error}", file=sys.stderr)
        return 1

    errors, warnings, documents, adr_paths = validate_repository(ROOT, policy)
    errors.extend(validate_policy_against_base(ROOT, args.base, policy))
    errors = sorted(set(errors))
    print_cost_report(ROOT, policy, documents, adr_paths, warnings, args.base)

    if errors:
        print("Documentation validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Documentation validation passed for {len(markdown_files(ROOT))} Markdown files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

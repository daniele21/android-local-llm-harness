#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WRAPPER = ROOT / "scripts" / "run-llrt6-physical-evidence.sh"
RUNNER = ROOT / "scripts" / "run-llama-cpp-kv-cache-evidence.sh"


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise AssertionError(f"{label}: missing {needle!r}")


def main() -> None:
    wrapper = WRAPPER.read_text(encoding="utf-8")
    runner = RUNNER.read_text(encoding="utf-8")

    for needle in (
        'BACKEND_REVISION="aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3"',
        "git diff --quiet --ignore-submodules=dirty --",
        "git diff --cached --quiet --ignore-submodules=dirty --",
        "third_party/llama.cpp/.git",
        "git -C third_party/llama.cpp diff --quiet --",
        "git -C third_party/llama.cpp diff --cached --quiet --",
        "git -C third_party/llama.cpp rev-parse HEAD",
        'require_clean_tracked_worktree "before runner"',
        'require_pinned_backend "before runner"',
        'require_clean_tracked_worktree "after runner"',
        'require_pinned_backend "after runner"',
        "trap finalize EXIT",
        'HARNESS_COMMIT="$(git rev-parse HEAD)"',
        'bash "$RUNNER" "$@"',
    ):
        require(wrapper, needle, "LLRT-6 guard wrapper")

    for case_name in (
        "release-default",
        "k-q8-fa-off",
        "k-q4-fa-off",
        "f16-f16-fa-on",
        "q8-q8-fa-on",
        "q4-q4-fa-on",
    ):
        require(runner, case_name, "LLRT-6 measurement runner")

    require(runner, 'BACKEND_REVISION="aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3"', "LLRT-6 measurement runner")
    require(runner, '"schemaVersion": 5', "LLRT-6 measurement runner")
    print("LLRT-6 physical-evidence guard regression: PASS")


if __name__ == "__main__":
    main()

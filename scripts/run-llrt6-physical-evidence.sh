#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNNER="$ROOT_DIR/scripts/run-llama-cpp-kv-cache-evidence.sh"
BACKEND_REVISION="aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3"

tracked_worktree_status() {
    git status --short --untracked-files=no --ignore-submodules=dirty
}

require_clean_tracked_worktree() {
    stage="$1"
    if ! git diff --quiet --ignore-submodules=dirty -- || ! git diff --cached --quiet --ignore-submodules=dirty --; then
        echo "LLRT-6C evidence requires a clean tracked Harness worktree ($stage)" >&2
        dirty_status="$(tracked_worktree_status)"
        if [[ -n "$dirty_status" ]]; then
            echo "Tracked changes:" >&2
            printf '%s\n' "$dirty_status" >&2
        fi
        return 2
    fi
}

require_pinned_backend() {
    stage="$1"
    if [[ ! -e third_party/llama.cpp/.git ]]; then
        echo "third_party/llama.cpp is not initialized ($stage)" >&2
        return 2
    fi
    if ! git -C third_party/llama.cpp diff --quiet -- || ! git -C third_party/llama.cpp diff --cached --quiet --; then
        echo "LLRT-6C evidence requires a clean llama.cpp submodule worktree ($stage)" >&2
        return 2
    fi
    actual_backend_revision="$(git -C third_party/llama.cpp rev-parse HEAD)"
    if [[ "$actual_backend_revision" != "$BACKEND_REVISION" ]]; then
        echo "Unexpected llama.cpp pin: expected $BACKEND_REVISION, got $actual_backend_revision ($stage)" >&2
        return 2
    fi
}

finalize() {
    original_rc=$?
    trap - EXIT
    set +e
    require_clean_tracked_worktree "after runner"
    worktree_rc=$?
    require_pinned_backend "after runner"
    backend_rc=$?
    set -e
    if ((worktree_rc != 0 || backend_rc != 0)); then
        exit 2
    fi
    exit "$original_rc"
}

command -v git >/dev/null 2>&1 || { echo "git is required" >&2; exit 2; }
[[ -f "$RUNNER" ]] || { echo "Missing LLRT-6 runner: $RUNNER" >&2; exit 2; }

cd "$ROOT_DIR"
require_clean_tracked_worktree "before runner"
require_pinned_backend "before runner"
HARNESS_COMMIT="$(git rev-parse HEAD)"
[[ "$HARNESS_COMMIT" =~ ^[0-9a-f]{40}$ ]] || { echo "Unable to resolve exact Harness commit" >&2; exit 2; }

trap finalize EXIT
bash "$RUNNER" "$@"

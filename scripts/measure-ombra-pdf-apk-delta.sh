#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASELINE_REF="${1:-c0ab1e8b5d4894081e38cd08e2adf4e72633d6be}"
REPORT_DIR="$ROOT_DIR/build/reports/ombra"
REPORT="$REPORT_DIR/omb0-apk-size-delta.txt"
WORKTREE=""

cleanup() {
    if [[ -n "$WORKTREE" && -d "$WORKTREE" ]]; then
        git -C "$ROOT_DIR" worktree remove --force "$WORKTREE" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

apk_path() {
    local checkout="$1"
    local candidate
    candidate="$(find "$checkout/apps/local-llm-console/build/outputs/apk/debug" -maxdepth 1 -type f -name '*.apk' | head -n 1 || true)"
    if [[ -z "$candidate" ]]; then
        echo "Error: debug OMBRA/Console APK was not produced in $checkout" >&2
        return 1
    fi
    printf '%s\n' "$candidate"
}

file_size() {
    local path="$1"
    if stat -f '%z' "$path" >/dev/null 2>&1; then
        stat -f '%z' "$path"
    else
        stat -c '%s' "$path"
    fi
}

cd "$ROOT_DIR"
if ! git rev-parse --verify "$BASELINE_REF^{commit}" >/dev/null 2>&1; then
    echo "Error: baseline ref '$BASELINE_REF' is not available. Fetch repository history first." >&2
    exit 1
fi

CURRENT_COMMIT="$(git rev-parse HEAD)"
BASELINE_COMMIT="$(git rev-parse "$BASELINE_REF^{commit}")"

mkdir -p "$REPORT_DIR"

echo "Building current OMBRA/Console debug APK..."
./gradlew :apps:local-llm-console:assembleDebug --no-configuration-cache
CURRENT_APK="$(apk_path "$ROOT_DIR")"
CURRENT_BYTES="$(file_size "$CURRENT_APK")"

WORKTREE="$(mktemp -d "${TMPDIR:-/tmp}/ombra-apk-baseline.XXXXXX")"
rmdir "$WORKTREE"
git worktree add --detach "$WORKTREE" "$BASELINE_COMMIT" >/dev/null

echo "Building pre-PDF baseline debug APK at $BASELINE_COMMIT..."
(
    cd "$WORKTREE"
    ./gradlew :apps:local-llm-console:assembleDebug --no-configuration-cache
)
BASELINE_APK="$(apk_path "$WORKTREE")"
BASELINE_BYTES="$(file_size "$BASELINE_APK")"

DELTA_BYTES=$((CURRENT_BYTES - BASELINE_BYTES))
DELTA_PERCENT="$(awk -v delta="$DELTA_BYTES" -v base="$BASELINE_BYTES" 'BEGIN { if (base == 0) print "n/a"; else printf "%.2f", (delta / base) * 100 }')"

{
    echo "result=MEASURED"
    echo "baseline_commit=$BASELINE_COMMIT"
    echo "current_commit=$CURRENT_COMMIT"
    echo "baseline_apk_bytes=$BASELINE_BYTES"
    echo "current_apk_bytes=$CURRENT_BYTES"
    echo "delta_bytes=$DELTA_BYTES"
    echo "delta_percent=$DELTA_PERCENT"
} > "$REPORT"

cat "$REPORT"

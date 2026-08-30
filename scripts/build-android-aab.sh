#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
    cat <<'EOF'
Usage:
  bash scripts/build-android-aab.sh build

Build the repository's signed Android App Bundle through the repository-owned
release helper. This is the canonical AAB packaging entrypoint exposed by
.engineering/commands.json; product-specific signing and versioning details
remain owned by the underlying release helper.
EOF
}

case "${1:-help}" in
    build)
        exec bash "${ROOT_DIR}/scripts/build-phone-test-release.sh" build
        ;;
    help|-h|--help)
        usage
        ;;
    *)
        usage >&2
        exit 2
        ;;
esac

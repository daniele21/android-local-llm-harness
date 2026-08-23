#!/usr/bin/env python3
"""Canonical documentation validator entry point using the 0.4 engineering policy owner."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys

_IMPL_PATH = Path(__file__).with_name("verify_docs_impl.py")
_SPEC = importlib.util.spec_from_file_location("_harness_verify_docs_impl", _IMPL_PATH)
if _SPEC is None or _SPEC.loader is None:
    raise RuntimeError(f"cannot load documentation validator implementation: {_IMPL_PATH}")
_IMPL = importlib.util.module_from_spec(_SPEC)
sys.modules[_SPEC.name] = _IMPL
_SPEC.loader.exec_module(_IMPL)
_IMPL.POLICY_PATH = Path(".engineering/documentation-policy.json")

for _name in dir(_IMPL):
    if not _name.startswith("__"):
        globals()[_name] = getattr(_IMPL, _name)

if __name__ == "__main__":
    raise SystemExit(_IMPL.main())

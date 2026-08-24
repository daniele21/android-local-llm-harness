#!/usr/bin/env python3
"""Regression guards for the LLRT-9 width=4 diagnostic tooling."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "scripts" / "run-llrt9-width4-diagnostic.sh"
TEST = (
    ROOT
    / "apps"
    / "device-test-runner"
    / "src"
    / "androidTest"
    / "kotlin"
    / "io"
    / "github"
    / "daniele21"
    / "localllm"
    / "devicetest"
    / "Llrt9Width4DiagnosticInstrumentedTest.kt"
)


def require(text: str, snippet: str, owner: str) -> None:
    if snippet not in text:
        raise AssertionError(f"{owner} is missing required invariant: {snippet}")


def forbid(text: str, snippet: str, owner: str) -> None:
    if snippet in text:
        raise AssertionError(f"{owner} contains forbidden canonical-evidence marker: {snippet}")


def main() -> int:
    runner = RUNNER.read_text(encoding="utf-8")
    test = TEST.read_text(encoding="utf-8")

    require(runner, 'run_case "baseline-quality" "0,1,2,3" "quality"', "diagnostic runner")
    require(runner, 'run_case "swap02-quality" "2,1,0,3" "quality"', "diagnostic runner")
    require(runner, 'run_case "baseline-greedy" "0,1,2,3" "greedy"', "diagnostic runner")
    require(runner, "LOCAL_LLM_LLRT9_DIAGNOSTIC_JSON", "diagnostic runner")
    require(runner, "Diagnostic only: do not mark LLRT-9C DONE", "diagnostic runner")
    require(runner, 'MODEL_RELATIVE_PATH="e2e/model.gguf"', "diagnostic runner")
    require(runner, 'MODEL_APP_DATA_PATH="files/$MODEL_RELATIVE_PATH"', "diagnostic runner")
    require(runner, 'require_clean_tracked_worktree "before build"', "diagnostic runner")
    require(runner, 'require_clean_tracked_worktree "after Gradle device-test build"', "diagnostic runner")
    forbid(runner, "LOCAL_LLM_LLRT9_JSON", "diagnostic runner")

    require(test, 'evidenceType", "LLRT9_WIDTH4_DIAGNOSTIC"', "diagnostic instrumentation")
    require(test, "LOCAL_LLM_LLRT9_DIAGNOSTIC_JSON", "diagnostic instrumentation")
    require(test, "promptSourceIndices", "diagnostic instrumentation")
    require(test, "matchingSlots", "diagnostic instrumentation")
    require(test, "outputTokensMatch", "diagnostic instrumentation")
    require(
        test,
        "temperature = if (config.samplingMode == Llrt9DiagnosticSamplingMode.GREEDY) 0f",
        "diagnostic instrumentation",
    )
    require(test, 'promptOrder.toSet() == (0 until width).toSet()', "diagnostic instrumentation")
    forbid(test, "LOCAL_LLM_LLRT9_JSON", "diagnostic instrumentation")
    forbid(test, '"LLRT-9 deterministic output mismatch between serial and native batch"', "diagnostic instrumentation")

    print("LLRT-9 width=4 diagnostic tooling regression checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

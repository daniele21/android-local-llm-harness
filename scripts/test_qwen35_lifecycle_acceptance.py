#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RUNNER = ROOT / "scripts" / "run-qwen35-lifecycle-acceptance.sh"
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
    / "Qwen35LifecycleAcceptanceInstrumentedTest.kt"
)


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise AssertionError(f"{label}: missing {needle!r}")


def main() -> None:
    runner = RUNNER.read_text(encoding="utf-8")
    test = TEST.read_text(encoding="utf-8")

    for needle in (
        'BACKEND_REVISION="aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3"',
        'EXPECTED_08B_SHA="bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517"',
        'EXPECTED_2B_SHA="aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223"',
        'require_clean_tracked_worktree "before lifecycle evidence"',
        'require_pinned_backend "before lifecycle evidence"',
        'require_clean_tracked_worktree "after evidence capture"',
        'require_pinned_backend "after evidence capture"',
        'Q35_LIFECYCLE_MEMORY_ACCEPTANCE',
        '"automaticProfilePromotion": False',
        'LocalLlmDeviceE2eTest',
        'Qwen35LifecycleAcceptanceInstrumentedTest',
        'memoryRepeatCount "$MEMORY_REPEAT_COUNT"',
        '-e switchOutputTokens 8',
        '-e lowMemoryOutputTokens 256',
        '"switchOutputTokens": 8',
        '"lowMemoryOutputTokens": 256',
        '"memoryRepeatCount": int(sys.argv[11])',
        '"maxPssGrowthKb": int(sys.argv[12])',
        '"timeoutSeconds": int(sys.argv[13])',
        '"08b-low-memory"',
        '"2b-low-memory"',
        '"cross-tier-switch"',
        'Expected exactly three structured lifecycle scenarios',
        'Thermal gate satisfied:',
    ):
        require(runner, needle, "Q35 lifecycle runner")

    for needle in (
        "LOW_MEMORY_ACTIVE_GENERATION",
        "REFERENCE_MODEL_SWITCH",
        "RuntimeMemoryPressure.LOW_MEMORY",
        "RuntimeMemoryAction.CANCEL_AND_RELEASE_ALL",
        "lowMemoryOutputTokens",
        "switchOutputTokens",
        "LOCAL_LLM_Q35_LIFECYCLE_JSON",
        "secondaryModelSha256",
        "harnessCommit",
        "backendRevision",
    ):
        require(test, needle, "Q35 lifecycle instrumentation")

    print("Q35 lifecycle acceptance evidence contract: PASS")


if __name__ == "__main__":
    main()

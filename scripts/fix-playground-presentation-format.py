from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/MainActivity.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


text = PATH.read_text(encoding="utf-8")
text = replace_once(
    text,
    "    private fun PlaygroundPromptCard(state: HarnessUiState, presentation: PlaygroundPresentation, advancedVisible: Boolean, onToggleAdvanced: () -> Unit) {",
    """    private fun PlaygroundPromptCard(
        state: HarnessUiState,
        presentation: PlaygroundPresentation,
        advancedVisible: Boolean,
        onToggleAdvanced: () -> Unit,
    ) {""",
    "PlaygroundPromptCard signature",
)
text = replace_once(
    text,
    """                    color = when (presentation.statusTone) {
                        PlaygroundPresentationTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                        PlaygroundPresentationTone.ACTIVE,
                        PlaygroundPresentationTone.SUCCESS,
                        -> HarnessColors.Secondary
                        PlaygroundPresentationTone.ERROR -> MaterialTheme.colorScheme.error
                        PlaygroundPresentationTone.WARNING -> HarnessColors.Warning
                    },""",
    """                    color = when (presentation.statusTone) {
                        PlaygroundPresentationTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant

                        PlaygroundPresentationTone.ACTIVE,
                        PlaygroundPresentationTone.SUCCESS,
                        -> HarnessColors.Secondary

                        PlaygroundPresentationTone.ERROR -> MaterialTheme.colorScheme.error

                        PlaygroundPresentationTone.WARNING -> HarnessColors.Warning
                    },""",
    "Playground presentation tone spacing",
)
PATH.write_text(text, encoding="utf-8")
print("Playground presentation formatting applied")

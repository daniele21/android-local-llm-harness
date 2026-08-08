from pathlib import Path
import textwrap

path = Path("apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/HarnessUiState.kt")
text = path.read_text()
start = text.index("    private fun reducePlaygroundControl(")
end = text.index("\n\n    private fun HarnessUiState.applyPreset", start)
helper = textwrap.dedent(text[start:end])
text = text[:start] + text[end:]
marker = "internal object HarnessUiReducer {"
if marker not in text:
    raise SystemExit("HarnessUiReducer marker not found")
text = text.replace(marker, helper + "\n\n" + marker, 1)
path.write_text(text)

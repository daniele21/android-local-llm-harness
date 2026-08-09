from pathlib import Path

path = Path("apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/MainActivity.kt")
source = path.read_text(encoding="utf-8")
start_marker = "\n    @Composable\n    private fun PlaygroundResponseCard("
end_marker = "\n    @Composable\n    private fun ModelsScreen("

if source.count(start_marker) != 1:
    raise SystemExit(f"expected one legacy response function, found {source.count(start_marker)}")
if source.count(end_marker) != 1:
    raise SystemExit(f"expected one ModelsScreen marker, found {source.count(end_marker)}")

start = source.index(start_marker)
end = source.index(end_marker, start)
source = source[:start] + source[end:]

# These imports existed for the legacy response renderer. Remove them only when no other usage remains.
for symbol, import_line in (
    ("SelectionContainer", "import androidx.compose.foundation.text.selection.SelectionContainer\n"),
    ("HarnessMetric", "import io.github.daniele21.localllm.ui.designsystem.HarnessMetric\n"),
    ("HarnessMetricRow", "import io.github.daniele21.localllm.ui.designsystem.HarnessMetricRow\n"),
):
    if source.count(symbol) == 1 and import_line in source:
        source = source.replace(import_line, "")

path.write_text(source, encoding="utf-8")

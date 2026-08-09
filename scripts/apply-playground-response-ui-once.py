from pathlib import Path

path = Path("apps/local-llm-phone-test/src/main/kotlin/io/github/daniele21/localllm/phonetest/MainActivity.kt")
source = path.read_text(encoding="utf-8")
old = "item { PlaygroundResponseCard(presentation) }"
new = "item { ModernPlaygroundResponseCard(presentation) }"
count = source.count(old)
if count != 1:
    raise SystemExit(f"expected exactly one playground response call site, found {count}")
path.write_text(source.replace(old, new), encoding="utf-8")

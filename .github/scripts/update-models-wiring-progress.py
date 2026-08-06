from pathlib import Path

path = Path("docs/harness-ux-ui-implementation-progress.md")
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "**Integrated baseline:** `dev` after merged PR #70\n"
    "**Active implementation branch:** `agent/models-udf-foundation`\n"
    "**Active pull request:** PR #71 toward `dev`\n",
    "**Integrated baseline:** `dev` after merged PR #71\n"
    "**Active implementation branch:** `agent/models-udf-wiring`\n"
    "**Active pull request:** PR #72 toward `dev`\n",
    "tracker header",
)
replace_once(
    "| Models | PARTIAL | Import, download/install, explicit verify, confirmation and protected removal are connected. PR #71 adds the unified catalog/import/selection/runtime inventory foundation; controller effects, connected rendering, model details and recovery actions remain. |",
    "| Models | PARTIAL | Import, download/install, explicit verify, confirmation and protected removal are connected. PR #72 routes these operations through `ModelEffects`, renders the unified inventory and removes Activity state mirrors; model details, deterministic recovery and device evidence remain. |",
    "Models summary",
)
replace_once(
    "| Durable multi-model catalog | PARTIAL | Metadata is persisted per digest. PR #71 derives one immutable inventory across catalog releases, external imports, selection and runtime ownership with explicit degraded states; `lastUsedAt`, connected recovery and restart UI tests remain. |",
    "| Durable multi-model catalog | PARTIAL | Metadata is persisted per digest and PR #72 connects its unified inventory to real controller snapshots and runtime ownership. `lastUsedAt`, detail recovery, restart UI tests and physical evidence remain. |",
    "catalog summary",
)
replace_once(
    "| ViewModel and UDF migration | PARTIAL | PR #66 provides the shared immutable state and reducer foundation; PR #67 connects Playground. PR #71 integrates the unified model inventory into reducer events, while Models effects/rendering plus Diagnostics, Overview and Settings remain Activity-owned. |",
    "| ViewModel and UDF migration | PARTIAL | Playground and Models now render from `HarnessUiState` and cross typed effect boundaries. Diagnostics, Overview and Settings still retain Activity-owned state and effects. |",
    "UDF summary",
)
replace_once(
    "| CI and Android build validation | VALIDATION | PR #70 is merged into `dev`; cumulative validation `31078225131` and packaging `31078225481` are green. PR #71 passed focused Spotless, Detekt, JVM, Lint and Kotlin compilation in run `31079690251`. |",
    "| CI and Android build validation | VALIDATION | PR #71 is merged into `dev` after full validation `31081158228`. PR #72 passed focused Spotless, Detekt, JVM, Lint, Kotlin compilation and Activity-state guards in run `31082897050`; cumulative PR validation remains. |",
    "CI summary",
)
replace_once(
    "The controller effect boundary, connected Models rendering, `models/{digest}` and recovery actions remain the next vertical slice.",
    "PR #72 connects this projection to the existing model controllers through an Activity-scoped `ModelEffects` boundary and a ViewModel-owned coordinator. Import, refresh, download, cancellation, installation, installed selection, verification and removal now enter through one typed command surface. The Models screen renders from `HarnessUiState.modelInventory`; Overview, Health, Benchmarks, Validation, Settings and Storage consume the same selected-model state. Activity mirrors for selected model, catalog distribution, removal confirmation and diagnostics selection are removed. Controller, launcher, executor and native runtime ownership remain Activity-scoped deliberately. `models/{digest}` and deterministic recovery actions remain the next vertical slice.",
    "connected Models narrative",
)

start = text.index("## Immediate next block")
end = text.index("## Known technical debt", start)
replacement = "\n".join(
    [
        "## Immediate next block",
        "",
        "### Add model details and deterministic recovery",
        "",
        "Status: `NEXT`",
        "",
        "Completed in PR #72:",
        "",
        "1. [x] introduce an Activity-scoped `ModelEffects` boundary;",
        "2. [x] group catalog mutations into typed commands;",
        "3. [x] publish catalog, selection and runtime ownership snapshots to the reducer;",
        "4. [x] render Models from `HarnessUiState.modelInventory`;",
        "5. [x] route import, refresh, download, cancel, install, select, verify and remove through the ViewModel coordinator;",
        "6. [x] preserve Playground runtime release before model replacement or removal;",
        "7. [x] remove Activity-owned model, catalog, confirmation and diagnostics mirrors;",
        "8. [x] add fake-effects tests for commands, busy guards, selection and removal confirmation;",
        "9. [x] pass focused Spotless, Detekt, JVM, Lint, Kotlin compilation and state-removal guards;",
        "10. [ ] pass cumulative PR validation and merge into `dev`.",
        "",
        "Next implementation slice:",
        "",
        "1. add a URL-safe model-detail route using digest when available and stable catalog identity otherwise;",
        "2. derive one detail presentation for compatibility, integrity, installation, selection and loaded ownership;",
        "3. expose deterministic recovery for runtime/selection mismatch and unknown runtime ownership;",
        "4. keep destructive recovery behind explicit confirmation and runtime release;",
        "5. add route, presentation, reducer and effects tests before connected UI evidence.",
        "",
        "",
    ]
)
text = text[:start] + replacement + text[end:]
replace_once(
    "- Playground is fully wired to ViewModel/UDF and PR #71 adds the Models inventory reducer foundation; Models effects/rendering, Overview, Diagnostics, Settings and developer tools still use Activity-owned state.",
    "- Playground and Models are wired to ViewModel/UDF; Overview, Diagnostics, Settings and developer tools still retain Activity-owned state and effects.",
    "UDF debt",
)
replace_once(
    "- Controllers still use executors and callbacks; Playground now crosses a typed effect boundary, while the remaining controllers have not yet migrated.",
    "- Controllers still use executors and callbacks; Playground and Models cross typed effect boundaries, while diagnostics and settings controllers have not migrated.",
    "controller debt",
)

path.write_text(text)

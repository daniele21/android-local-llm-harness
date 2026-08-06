from pathlib import Path


source_path = Path("scripts/patch_detail_navigation.py")
source = source_path.read_text()
old = '''text = replace_once(
    text,
    """                    onClick = onOpenDiagnostics,
""",
    """                    onClick = onOpenDeveloperTools,
""",
    "developer tools detail action",
)
'''
new = '''text = replace_once(
    text,
    """                    title = "Developer tools",
                    detail = "Logs, diagnostics, and advanced options",
                    trailing = "›",
                    onClick = onOpenDiagnostics,
""",
    """                    title = "Developer tools",
                    detail = "Logs, diagnostics, and advanced options",
                    trailing = "›",
                    onClick = onOpenDeveloperTools,
""",
    "developer tools detail action",
)
'''
if source.count(old) != 1:
    raise RuntimeError("Expected one generic developer-tools replacement block")
source = source.replace(old, new, 1)
exec(compile(source, str(source_path), "exec"))

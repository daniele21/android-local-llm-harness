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

source = source.replace(
    "versionCode = BuildConfig.VERSION_CODE.toString(),",
    "versionCode = appVersionCode(),",
    1,
)
source = source.replace(
    "applicationId = BuildConfig.APPLICATION_ID,",
    "applicationId = packageName,",
    1,
)

write_anchor = "PATH.write_text(text)\n"
metadata_patch = '''text = replace_once(
    text,
    """    private fun appVersionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    }.getOrDefault("0.0.0")
""",
    """    private fun appVersionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    }.getOrDefault("0.0.0")

    private fun appVersionCode(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).longVersionCode.toString()
    }.getOrDefault("0")
""",
    "app version metadata helpers",
)

PATH.write_text(text)
'''
if source.count(write_anchor) != 1:
    raise RuntimeError("Expected one MainActivity write anchor")
source = source.replace(write_anchor, metadata_patch, 1)

exec(compile(source, str(source_path), "exec"))

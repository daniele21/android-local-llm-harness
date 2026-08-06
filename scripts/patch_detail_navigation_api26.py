from pathlib import Path


path = Path(
    "apps/local-llm-phone-test/src/main/kotlin/"
    "io/github/daniele21/localllm/phonetest/MainActivity.kt"
)
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "import android.os.Bundle\n",
    "import android.os.Build\nimport android.os.Bundle\n",
    "Build import",
)
replace_once(
    """    private fun appVersionCode(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).longVersionCode.toString()
    }.getOrDefault("0")
""",
    """    private fun appVersionCode(): String = runCatching {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }
    }.getOrDefault("0")
""",
    "API-compatible version code",
)

path.write_text(text)

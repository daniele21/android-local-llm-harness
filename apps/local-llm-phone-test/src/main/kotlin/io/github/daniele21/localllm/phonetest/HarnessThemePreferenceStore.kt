package io.github.daniele21.localllm.phonetest

import android.content.Context

internal class HarnessThemePreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): HarnessThemePreference = decode(preferences.getString(THEME_KEY, null))

    fun write(preference: HarnessThemePreference) {
        preferences.edit().putString(THEME_KEY, preference.name).apply()
    }

    companion object {
        internal fun decode(value: String?): HarnessThemePreference =
            HarnessThemePreference.entries.firstOrNull { it.name == value } ?: HarnessThemePreference.DARK

        private const val PREFERENCES_NAME = "harness-ui-preferences"
        private const val THEME_KEY = "theme"
    }
}

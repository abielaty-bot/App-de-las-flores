package pe.lasflores.controlinterno.ui

import android.content.Context

/**
 * Apariencia independiente de los registros operativos de Room.
 * Automático sigue el modo día/noche del dispositivo.
 */
internal enum class LasFloresThemeMode(val storedValue: String) {
    AUTOMATIC("automatic"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStoredValue(value: String?): LasFloresThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: AUTOMATIC
    }
}

internal object LasFloresThemeRules {
    fun isDark(mode: LasFloresThemeMode, systemDark: Boolean): Boolean = when (mode) {
        LasFloresThemeMode.AUTOMATIC -> systemDark
        LasFloresThemeMode.LIGHT -> false
        LasFloresThemeMode.DARK -> true
    }
}

/** Preferencia pequeña, privada y persistente; no toca SQLite/Room ni respaldos. */
internal class LasFloresThemeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "lasflores.ui.preferences", Context.MODE_PRIVATE
    )

    fun read(): LasFloresThemeMode =
        LasFloresThemeMode.fromStoredValue(preferences.getString("appearance", null))

    fun write(mode: LasFloresThemeMode) {
        preferences.edit().putString("appearance", mode.storedValue).apply()
    }
}

package org.gymlog.app

import android.content.Context
import androidx.core.content.edit

/** App-private preferences; excluded from backup and device transfer like the workout database. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings = fromMap(prefs.all)

    fun save(settings: Settings) {
        prefs.edit {
            clear()
            for ((key, value) in toMap(settings)) when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
            }
        }
    }

    companion object {
        fun validStep(step: String): Boolean = steppedLoad("0", step, 1)?.toBigDecimalOrNull()?.let { it <= 100.toBigDecimal() } == true
        fun validBar(bar: String): Boolean = bar.trim().replace(',', '.').toBigDecimalOrNull()?.let { it.signum() >= 0 && it <= 100.toBigDecimal() } == true

        /** The stored and exported representation. Unknown or invalid values fall back to defaults. */
        fun toMap(settings: Settings): Map<String, Any> = mapOf(
            "unit" to settings.unit.name,
            "load_step_kg" to settings.loadStepKg,
            "load_step_lb" to settings.loadStepLb,
            "theme" to settings.theme.name,
            "rest_enabled" to settings.restEnabled,
            "rest_seconds" to settings.restSeconds,
            "rest_auto_start" to settings.restAutoStart,
            "rest_sound" to settings.restSound,
            "rest_vibrate" to settings.restVibrate,
            "bar_kg" to settings.barKg,
            "bar_lb" to settings.barLb,
            "plates_kg" to settings.platesKg,
            "plates_lb" to settings.platesLb,
        )

        fun fromMap(map: Map<String, *>): Settings {
            val defaults = Settings()
            fun string(key: String, valid: (String) -> Boolean, fallback: String) = (map[key] as? String)?.takeIf(valid) ?: fallback
            fun flag(key: String, fallback: Boolean) = map[key] as? Boolean ?: fallback
            return Settings(
                unit = WeightUnit.entries.firstOrNull { it.name == map["unit"] } ?: defaults.unit,
                loadStepKg = string("load_step_kg", ::validStep, defaults.loadStepKg),
                loadStepLb = string("load_step_lb", ::validStep, defaults.loadStepLb),
                theme = ThemeMode.entries.firstOrNull { it.name == map["theme"] } ?: defaults.theme,
                restEnabled = flag("rest_enabled", defaults.restEnabled),
                restSeconds = (map["rest_seconds"] as? Number)?.toInt()?.takeIf { it in MIN_REST_SECONDS..MAX_REST_SECONDS } ?: defaults.restSeconds,
                restAutoStart = flag("rest_auto_start", defaults.restAutoStart),
                restSound = flag("rest_sound", defaults.restSound),
                restVibrate = flag("rest_vibrate", defaults.restVibrate),
                barKg = string("bar_kg", ::validBar, defaults.barKg),
                barLb = string("bar_lb", ::validBar, defaults.barLb),
                platesKg = string("plates_kg", { parsePlates(it) != null }, defaults.platesKg),
                platesLb = string("plates_lb", { parsePlates(it) != null }, defaults.platesLb),
            )
        }
    }
}

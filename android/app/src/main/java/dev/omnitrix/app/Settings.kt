package dev.omnitrix.app

import android.content.Context
import androidx.core.content.edit

/** User settings (Omnitrix menu → Settings). Read anywhere: activity, receivers, complication. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    enum class Animations(val label: String) { FULL("FULL"), QUICK("QUICK"), OFF("OFF") }
    enum class Haptics(val label: String, val amplitude: Float) { STRONG("STRONG", 1f), LIGHT("LIGHT", 0.45f), OFF("OFF", 0f) }

    /** Alien time in Standard energy; Master Control triples it. */
    var alienMinutes: Int
        get() = prefs.getInt("alien_minutes", 10)
        set(v) = prefs.edit { putInt("alien_minutes", v) }

    /** Red timeout face after switching back from an alien (timeout or revert); 0 = off. */
    var timeoutSeconds: Int
        get() = prefs.getInt("timeout_seconds", 10)
        set(v) = prefs.edit { putInt("timeout_seconds", v) }

    var animations: Animations
        get() = Animations.entries[prefs.getInt("animations", 0).coerceIn(0, 2)]
        set(v) = prefs.edit { putInt("animations", v.ordinal) }

    var haptics: Haptics
        get() = Haptics.entries[prefs.getInt("haptics", 0).coerceIn(0, 2)]
        set(v) = prefs.edit { putInt("haptics", v.ordinal) }

    /** Omnitrix sounds; only ever play when the watch itself is in Sound mode. */
    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) = prefs.edit { putBoolean("sound", v) }

    /** Battery diagnostics logging (Omnitrix menu → Battery). */
    var diagnostics: Boolean
        get() = prefs.getBoolean("diagnostics", true)
        set(v) = prefs.edit { putBoolean("diagnostics", v) }

    /** DNA Codex locks: off = every alien available. */
    var codex: Boolean
        get() = prefs.getBoolean("codex", true)
        set(v) = prefs.edit { putBoolean("codex", v) }

    /** For Bio-scan heart-rate zones (max HR ≈ 220 − age). */
    var age: Int
        get() = prefs.getInt("age", 30)
        set(v) = prefs.edit { putInt("age", v) }

    fun alienTimeMillis(energy: Energy): Long = when (energy) {
        Energy.RECHARGE -> 0L
        Energy.MASTER -> alienMinutes * 3 * 60_000L
        Energy.STANDARD -> alienMinutes * 60_000L
    }

    companion object {
        val ALIEN_MINUTES = listOf(1, 3, 5, 10, 15)
        val TIMEOUT_SECONDS = listOf(0, 10, 30, 60, 120, 300)

        fun timeoutLabel(seconds: Int) = when {
            seconds == 0 -> "OFF"
            seconds < 60 -> "$seconds S"
            else -> "${seconds / 60} MIN"
        }
        val AGES = (15..75 step 5).toList()
    }
}

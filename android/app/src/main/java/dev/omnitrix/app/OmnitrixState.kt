package dev.omnitrix.app

import android.content.Context
import androidx.core.content.edit

data class Transformation(val alien: Int, val startedAtMillis: Long, val endsAtMillis: Long) {
    val totalMillis get() = endsAtMillis - startedAtMillis
}

/** Persisted transformation state, shared by the app, the timeout alarm and the complication. */
class OmnitrixState(context: Context) {
    private val prefs = context.getSharedPreferences("omnitrix", Context.MODE_PRIVATE)

    fun current(now: Long = System.currentTimeMillis()): Transformation? {
        val alien = prefs.getInt(KEY_ALIEN, -1)
        val endsAt = prefs.getLong(KEY_ENDS_AT, 0)
        if (alien !in Aliens.all.indices || endsAt <= now) return null
        return Transformation(alien, prefs.getLong(KEY_STARTED_AT, now), endsAt)
    }

    /** Last alien used, so the selector opens where you left it. */
    var lastAlien: Int
        get() = prefs.getInt(KEY_LAST, 0).coerceIn(Aliens.all.indices)
        set(value) = prefs.edit { putInt(KEY_LAST, value) }

    fun set(transformation: Transformation?) = prefs.edit {
        if (transformation == null) {
            remove(KEY_ALIEN)
            remove(KEY_STARTED_AT)
            remove(KEY_ENDS_AT)
        } else {
            putInt(KEY_ALIEN, transformation.alien)
            putLong(KEY_STARTED_AT, transformation.startedAtMillis)
            putLong(KEY_ENDS_AT, transformation.endsAtMillis)
            putInt(KEY_LAST, transformation.alien)
        }
    }

    private companion object {
        const val KEY_ALIEN = "alien"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_ENDS_AT = "ends_at"
        const val KEY_LAST = "last_alien"
    }
}

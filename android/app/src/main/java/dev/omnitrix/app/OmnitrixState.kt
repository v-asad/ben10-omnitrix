package dev.omnitrix.app

import android.content.Context
import androidx.core.content.edit

data class Transformation(val alien: Int, val endsAtMillis: Long)

/** Persisted transformation state, shared by the app, the timeout alarm and the complication. */
class OmnitrixState(context: Context) {
    private val prefs = context.getSharedPreferences("omnitrix", Context.MODE_PRIVATE)

    fun current(now: Long = System.currentTimeMillis()): Transformation? {
        val alien = prefs.getInt(KEY_ALIEN, -1)
        val endsAt = prefs.getLong(KEY_ENDS_AT, 0)
        return if (alien >= 0 && endsAt > now) Transformation(alien, endsAt) else null
    }

    fun set(transformation: Transformation?) = prefs.edit {
        if (transformation == null) {
            remove(KEY_ALIEN)
            remove(KEY_ENDS_AT)
        } else {
            putInt(KEY_ALIEN, transformation.alien)
            putLong(KEY_ENDS_AT, transformation.endsAtMillis)
        }
    }

    private companion object {
        const val KEY_ALIEN = "alien"
        const val KEY_ENDS_AT = "ends_at"
    }
}

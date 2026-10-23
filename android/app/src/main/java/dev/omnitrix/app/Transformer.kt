package dev.omnitrix.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import dev.omnitrix.app.complication.OmnitrixStateService

/** Transform / revert / timeout / recharge, keeping the watch face in sync. */
object Transformer {

    fun transform(context: Context, alien: Int, durationMillis: Long) {
        val now = System.currentTimeMillis()
        val state = OmnitrixState(context)
        state.cooldownUntil = 0
        state.set(Transformation(alien, now, now + durationMillis))
        schedule(context, now + durationMillis, TimeoutReceiver.ACTION_TIMEOUT)
        refreshFace(context)
        Diagnostics.log(context, "transform", Aliens.all[alien].slug)
        DnaCodex(context).onTransform(alien)
    }

    /** Switching back by holding the core: back to human, then the red timeout face. */
    fun revert(context: Context) {
        OmnitrixState(context).set(null)
        context.getSystemService(AlarmManager::class.java).cancel(alarm(context, TimeoutReceiver.ACTION_TIMEOUT))
        startTimeout(context)
        refreshFace(context)
        Diagnostics.log(context, "revert")
    }

    /** Red timeout after switching back (length from Settings, 0 = none). */
    private fun startTimeout(context: Context): Long {
        val length = Settings(context).timeoutSeconds * 1000L
        if (length > 0) {
            val state = OmnitrixState(context)
            state.cooldownUntil = System.currentTimeMillis() + length
            schedule(context, state.cooldownUntil, TimeoutReceiver.ACTION_RECHARGED)
        }
        return length
    }

    /** Alien time ran out: four beeps, back to human, then the red timeout face. */
    fun timeout(context: Context) {
        OmnitrixState(context).set(null)
        val timeout = startTimeout(context)
        refreshFace(context)
        DnaCodex(context).onFullTime()        // held alien form to the end: mission progress
        Haptics.timeout(context)
        Sounds.play(context, Sounds.Cue.TIMEOUT)
        Diagnostics.log(context, "timeout", "timeout_s=${timeout / 1000}")
    }

    /** Recharge finished: the Omnitrix is ready again. */
    fun recharged(context: Context) {
        OmnitrixState(context).cooldownUntil = 0
        refreshFace(context)
        Haptics.ready(context)
        Sounds.play(context, Sounds.Cue.READY)
        Diagnostics.log(context, "recharged")
    }

    fun refreshFace(context: Context) {
        ComplicationDataSourceUpdateRequester
            .create(context, ComponentName(context, OmnitrixStateService::class.java))
            .requestUpdateAll()
    }

    private fun schedule(context: Context, atMillis: Long, action: String) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, alarm(context, action))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, alarm(context, action))
        }
    }

    private fun alarm(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(), Intent(context, TimeoutReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

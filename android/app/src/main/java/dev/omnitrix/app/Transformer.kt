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
    }

    /** Voluntary revert (hold the core): no recharge penalty. */
    fun revert(context: Context) {
        OmnitrixState(context).set(null)
        context.getSystemService(AlarmManager::class.java).cancel(alarm(context, TimeoutReceiver.ACTION_TIMEOUT))
        refreshFace(context)
        Diagnostics.log(context, "revert")
    }

    /** Alien time ran out: four beeps, back to human, then the Omnitrix recharges. */
    fun timeout(context: Context) {
        val state = OmnitrixState(context)
        state.set(null)
        val cooldown = Settings(context).cooldownMinutes * 60_000L
        if (cooldown > 0) {
            state.cooldownUntil = System.currentTimeMillis() + cooldown
            schedule(context, state.cooldownUntil, TimeoutReceiver.ACTION_RECHARGED)
        }
        refreshFace(context)
        DnaCodex(context).addBonus()          // a full transformation earns DNA
        Haptics.timeout(context)
        Sounds.play(context, Sounds.Cue.TIMEOUT)
        Diagnostics.log(context, "timeout", "cooldown_min=${cooldown / 60_000}")
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

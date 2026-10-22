package dev.omnitrix.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import dev.omnitrix.app.complication.OmnitrixStateService

/** Transform / revert, and keep the watch face in sync. */
object Transformer {

    fun transform(context: Context, alien: Int, durationMillis: Long) {
        val transformation = Transformation(alien, System.currentTimeMillis() + durationMillis)
        OmnitrixState(context).set(transformation)
        scheduleTimeout(context, transformation.endsAtMillis)
        refreshFace(context)
    }

    fun revert(context: Context) {
        OmnitrixState(context).set(null)
        context.getSystemService(AlarmManager::class.java).cancel(timeoutIntent(context))
        refreshFace(context)
    }

    fun refreshFace(context: Context) {
        ComplicationDataSourceUpdateRequester
            .create(context, ComponentName(context, OmnitrixStateService::class.java))
            .requestUpdateAll()
    }

    private fun scheduleTimeout(context: Context, atMillis: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, timeoutIntent(context))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, timeoutIntent(context))
        }
    }

    private fun timeoutIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, TimeoutReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

package dev.omnitrix.app.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dev.omnitrix.app.Aliens
import dev.omnitrix.app.MainActivity
import dev.omnitrix.app.OmnitrixState
import dev.omnitrix.app.Transformation
import java.time.Instant

/**
 * Tells the Omnitrix face what state you're in, encoded for Watch Face Format expressions:
 *   value = 0 (human) or alien index + 1
 *   text  = live countdown, rendered by the system so it ticks without app wake-ups
 *   title = alien name
 * Tapping the slot (the core) opens the selector.
 */
class OmnitrixStateService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData =
        build(OmnitrixState(this).current())

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        System.currentTimeMillis().let { now -> build(Transformation(alien = 0, startedAtMillis = now, endsAtMillis = now + 10 * 60_000)) }

    private fun build(transformation: Transformation?): ComplicationData {
        val name = transformation?.let { Aliens.all[it.alien].name } ?: "HUMAN"
        val text = if (transformation == null) {
            PlainComplicationText.Builder("READY").build()
        } else {
            TimeDifferenceComplicationText.Builder(
                TimeDifferenceStyle.STOPWATCH,
                CountDownTimeReference(Instant.ofEpochMilli(transformation.endsAtMillis)),
            ).build()
        }
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RangedValueComplicationData.Builder(
            value = (transformation?.alien?.plus(1) ?: 0).toFloat(),
            min = 0f,
            max = Aliens.size.toFloat(),
            contentDescription = PlainComplicationText.Builder("Omnitrix: $name").build(),
        )
            .setText(text)
            .setTitle(PlainComplicationText.Builder(name).build())
            .setTapAction(tap)
            .build()
    }
}

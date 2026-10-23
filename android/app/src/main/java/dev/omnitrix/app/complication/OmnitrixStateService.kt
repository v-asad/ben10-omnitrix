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
 *   value = 0 (human), alien index + 1, or count + 1 while recharging
 *   text  = live countdown, rendered by the system so it ticks without app wake-ups
 *   title = alien name
 * Tapping the slot (the core) opens the selector.
 */
class OmnitrixStateService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val state = OmnitrixState(this)
        return build(state.current(), state.cooldownUntil.takeIf { it > System.currentTimeMillis() })
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        System.currentTimeMillis().let { now -> build(Transformation(alien = 0, startedAtMillis = now, endsAtMillis = now + 10 * 60_000), null) }

    private fun build(transformation: Transformation?, cooldownUntil: Long?): ComplicationData {
        val count = Aliens.size
        // value: 0 human · 1..count alien n-1 · count+1 recharging (max = count+1 so the face can tell it's us)
        val (value, title, endsAt) = when {
            transformation != null -> Triple(transformation.alien + 1, Aliens.all[transformation.alien].name, transformation.endsAtMillis)
            cooldownUntil != null -> Triple(count + 1, "RECHARGING", cooldownUntil)
            else -> Triple(0, "HUMAN", null)
        }
        val text = if (endsAt == null) PlainComplicationText.Builder("READY").build()
        else TimeDifferenceComplicationText.Builder(TimeDifferenceStyle.STOPWATCH, CountDownTimeReference(Instant.ofEpochMilli(endsAt))).build()
        // Fresh task on every tap: the dial always pops up, never a leftover screen (e.g. Bio-scan).
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RangedValueComplicationData.Builder(
            value = value.toFloat(),
            min = 0f,
            max = (count + 1).toFloat(),
            contentDescription = PlainComplicationText.Builder("Omnitrix: $title").build(),
        )
            .setText(text)
            .setTitle(PlainComplicationText.Builder(title).build())
            .setTapAction(tap)
            .build()
    }
}

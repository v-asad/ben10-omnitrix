package dev.omnitrix.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import java.io.File
import java.time.Instant

/**
 * Battery diagnostics: a CSV of battery samples (every ~15 min, piggy-backing on wake-ups the watch
 * makes anyway: the alarm never wakes the device itself) plus app events. Pull it with
 * tools/battery_report.py, or see the summary in Omnitrix menu → Battery.
 *
 *   epoch_ms,iso,event,battery_pct,charging,screen_on,detail
 */
object Diagnostics {
    private const val HEADER = "epoch_ms,iso,event,battery_pct,charging,screen_on,detail"

    fun file(context: Context) = File(context.getExternalFilesDir(null), "omnitrix-diagnostics.csv")

    @Synchronized
    fun log(context: Context, event: String, detail: String = "") {
        if (!Settings(context).diagnostics) return
        runCatching {
            val f = file(context)
            if (!f.exists()) f.writeText("$HEADER\n")
            val (pct, charging) = battery(context)
            val screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive == true
            val now = System.currentTimeMillis()
            f.appendText("$now,${Instant.ofEpochMilli(now)},$event,$pct,${if (charging) 1 else 0},${if (screenOn) 1 else 0},$detail\n")
        }
    }

    fun battery(context: Context): Pair<Int, Boolean> {
        val b = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        return (if (level < 0) -1 else level * 100 / scale) to plugged
    }

    /** (Re)starts the ~15-minute sampler (battery log + DNA Codex steps); cheap to call repeatedly. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = sampler(context)
        val settings = Settings(context)
        if (!settings.diagnostics && !settings.codex) { alarms.cancel(intent); return }
        alarms.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES, intent)
    }

    private fun sampler(context: Context) = PendingIntent.getBroadcast(context, 7, Intent(context, SampleReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    class SampleReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            log(context, "sample")
            val pending = goAsync()
            DnaCodex(context).sampleSteps { pending.finish() }
        }
    }

    /** Summary for the in-app Battery screen. */
    class Summary(val batteryPct: Int, val charging: Boolean, val drainPerHour: Float?, val sinceUnplugHours: Float?,
                  val appMinutesToday: Int, val transformationsToday: Int, val samples: Int)

    fun summary(context: Context): Summary {
        val (pct, charging) = battery(context)
        val rows = runCatching { file(context).readLines().drop(1).mapNotNull { line ->
            val c = line.split(","); if (c.size < 7) null else c
        } }.getOrDefault(emptyList())
        val startOfDay = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        // discharge since the last time it came off the charger
        val unplugIndex = rows.indexOfLast { it[4] == "1" }
        val since = rows.drop(unplugIndex + 1).filter { it[3].toIntOrNull()?.let { v -> v >= 0 } == true }
        val (drain, hours) = if (since.size >= 2) {
            val first = since.first(); val last = since.last()
            val h = (last[0].toLong() - first[0].toLong()) / 3_600_000f
            val d = first[3].toInt() - last[3].toInt()
            (if (h > 0.25f) d / h else null) to h
        } else null to null
        val today = rows.filter { (it[0].toLongOrNull() ?: 0) >= startOfDay }
        val appMs = today.filter { it[2] == "app_close" }.sumOf { it[6].removePrefix("ms=").toLongOrNull() ?: 0 }
        return Summary(pct, charging, drain, hours, (appMs / 60_000).toInt(), today.count { it[2] == "transform" }, rows.size)
    }
}

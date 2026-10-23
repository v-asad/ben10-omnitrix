package dev.omnitrix.app

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.edit
import kotlin.math.max
import kotlin.math.min

/**
 * The DNA Codex: you start with the original ten aliens and unlock the rest, in roster order
 * (original series → Alien Force → Ultimate Alien → Ultimates), by collecting DNA.
 *
 *  - 1 DNA per 1,000 steps (from the hardware step counter, sampled with the ~15-min sampler)
 *  - +1 DNA for every transformation that runs its full time
 *  - each new alien costs [DNA_PER_ALIEN]
 *  - Master Control (battery > 90%) unlocks everyone while it lasts
 */
class DnaCodex(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("codex", Context.MODE_PRIVATE)

    /** Fractional DNA collected (1.0 = 1,000 steps). */
    var dna: Float
        get() = prefs.getFloat(KEY_DNA, 0f)
        private set(v) = prefs.edit { putFloat(KEY_DNA, v) }

    val unlockedCount: Int get() = min(Aliens.size, STARTERS + (dna / DNA_PER_ALIEN).toInt())

    /** DNA progress toward the next alien, 0..1 (1 when everything is unlocked). */
    val progressToNext: Float get() = if (unlockedCount >= Aliens.size) 1f else (dna % DNA_PER_ALIEN) / DNA_PER_ALIEN

    /** Steps still needed for the next alien. */
    val stepsToNext: Int get() = if (unlockedCount >= Aliens.size) 0 else ((DNA_PER_ALIEN - dna % DNA_PER_ALIEN) * 1000).toInt()

    fun isUnlocked(index: Int, energy: Energy): Boolean =
        !Settings(app).codex || energy == Energy.MASTER || index < unlockedCount

    /** Aliens unlocked since the user last saw the codex (for the "NEW DNA" splash). */
    fun newlyUnlocked(): List<Int> {
        val seen = prefs.getInt(KEY_SEEN, STARTERS)
        return (seen until unlockedCount).toList()
    }

    fun markSeen() = prefs.edit { putInt(KEY_SEEN, unlockedCount) }

    fun addBonus(amount: Float = 1f) {
        dna += amount
        dev.omnitrix.app.tiles.TileKit.refresh(app, dev.omnitrix.app.tiles.CodexTileService::class.java)
    }

    /**
     * Folds new steps from the hardware counter into DNA. The counter is cumulative since boot, so
     * a smaller value than last time means the watch rebooted.
     */
    fun foldSteps(counter: Float) {
        val last = prefs.getFloat(KEY_LAST_COUNTER, -1f)
        val delta = when {
            last < 0f -> 0f                      // first reading: start counting from now
            counter < last -> counter            // rebooted
            else -> counter - last
        }
        prefs.edit { putFloat(KEY_LAST_COUNTER, counter) }
        if (delta > 0f) {
            dna = max(0f, dna + delta / 1000f)
            dev.omnitrix.app.tiles.TileKit.refresh(app, dev.omnitrix.app.tiles.CodexTileService::class.java)
        }
    }

    /** Reads the step counter once (registering briefly) and folds it in; [done] runs either way. */
    fun sampleSteps(done: () -> Unit) {
        if (!Settings(app).codex || app.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            done(); return
        }
        val sensors = app.getSystemService(SensorManager::class.java)
        val counter = sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensors == null || counter == null) { done(); return }
        val listener = object : SensorEventListener {
            var finished = false
            override fun onSensorChanged(event: SensorEvent) {
                if (finished) return
                finished = true
                sensors.unregisterListener(this)
                foldSteps(event.values[0])
                done()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, counter, SensorManager.SENSOR_DELAY_NORMAL)
        // give up after a few seconds so a receiver never hangs
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!listener.finished) { listener.finished = true; sensors.unregisterListener(listener); done() }
        }, 4000)
    }

    companion object {
        /** The original ten: Heatblast … Ghostfreak (first ten in the roster). */
        const val STARTERS = 10
        const val DNA_PER_ALIEN = 5f
        private const val KEY_DNA = "dna"
        private const val KEY_LAST_COUNTER = "last_step_counter"
        private const val KEY_SEEN = "seen_unlocked"
    }
}

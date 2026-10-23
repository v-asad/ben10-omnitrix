package dev.omnitrix.app

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.edit
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/** What a mission asks you to do. */
enum class Objective(val unit: String, private val label: (Int) -> String) {
    STEPS("STEPS", { "WALK %,d STEPS".format(it) }),
    TRANSFORMS("TRANSFORMS", { "TRANSFORM $it×" }),
    FULL_TIME("TIMEOUTS", { "HOLD ALIEN FORM TO TIMEOUT $it×" }),
    VARIETY("ALIENS", { "USE $it DIFFERENT ALIENS" }),
    BIOSCANS("BIO-SCANS", { if (it == 1) "COMPLETE A BIO-SCAN" else "COMPLETE $it BIO-SCANS" }),
    STREAK("ACTIVE DAYS", { if (it == 1) "1 ACTIVE DAY · 5K STEPS" else "$it ACTIVE DAYS · 5K STEPS" });

    fun describe(target: Int) = label(target)
}

class Goal(val type: Objective, val target: Int)

class Mission(val number: Int, val chapter: Chapter, val reward: Int, val title: String, val goals: List<Goal>, val xp: Int) {
    val isFinale get() = goals.size > 1
}

class Chapter(val number: Int, val title: String, val aliens: List<Int>)

enum class Rank(val title: String, val xp: Int) {
    ROOKIE("ROOKIE", 0), HERO("HERO", 400), PLUMBER("PLUMBER", 1200), MAGISTER("MAGISTER", 3000),
    MASTER_CONTROL("MASTER CONTROL", 5000), BEN_10000("BEN 10,000", Int.MAX_VALUE),
}

/**
 * The mission campaign. Six chapters follow the shows; Alien X is the final mission.
 * Chapter 1 (the original ten) is unlocked from the start.
 */
object Missions {
    private fun c(type: Objective, target: Int) = Goal(type, target)
    private val S = Objective.STEPS; private val T = Objective.TRANSFORMS; private val F = Objective.FULL_TIME
    private val V = Objective.VARIETY; private val B = Objective.BIOSCANS; private val D = Objective.STREAK

    /** Goals per mission, chapter by chapter; the last entry of each chapter is its two-part finale. */
    private val plans: List<List<List<Goal>>> = listOf(
        listOf(listOf(c(S, 3000)), listOf(c(T, 2)), listOf(c(S, 4000)), listOf(c(V, 3)), listOf(c(S, 4000)), listOf(c(F, 1)),
            listOf(c(S, 5000)), listOf(c(B, 1)), listOf(c(S, 5000)), listOf(c(T, 3)), listOf(c(D, 1)), listOf(c(S, 6000), c(V, 4))),
        listOf(listOf(c(S, 4500)), listOf(c(T, 3)), listOf(c(S, 5500)), listOf(c(V, 4)), listOf(c(S, 6000)), listOf(c(F, 1)),
            listOf(c(S, 7000)), listOf(c(B, 2)), listOf(c(S, 7000)), listOf(c(T, 4)), listOf(c(D, 2)), listOf(c(S, 8000), c(F, 2))),
        listOf(listOf(c(S, 6000)), listOf(c(T, 4)), listOf(c(S, 7000)), listOf(c(V, 5)), listOf(c(S, 8000)), listOf(c(F, 2)),
            listOf(c(B, 2)), listOf(c(S, 9000)), listOf(c(D, 2)), listOf(c(S, 10000), c(V, 6))),
        listOf(listOf(c(S, 10000)), listOf(c(T, 6)), listOf(c(D, 3)), listOf(c(F, 3)), listOf(c(S, 12000), c(B, 3))),
        listOf(listOf(c(S, 20000), c(T, 10), c(D, 3))),
    )
    private val xpPerChapter = listOf(100, 150, 200, 300, 1000)

    lateinit var chapters: List<Chapter>; private set
    /** Roster indices in unlock order (Alien X last). */
    lateinit var unlockOrder: List<Int>; private set
    lateinit var all: List<Mission>; private set

    fun build() {
        if (::all.isInitialized) return
        val roster = Aliens.all
        val alienX = roster.indexOfFirst { it.slug == "alien-x" }
        fun era(e: String) = roster.indices.filter { roster[it].era == e && it != alienX }
        val os = era("OS")
        val groups = listOf(
            "ORIGINAL TEN" to os.take(10), "SUMMER VACATION" to os.drop(10), "ALIEN FORCE" to era("AF"),
            "ULTIMATE ALIEN" to era("UA"), "ULTIMATE EVOLUTION" to era("ULTIMATE"), "FINAL MISSION" to listOf(alienX),
        )
        chapters = groups.mapIndexed { i, (title, aliens) -> Chapter(i + 1, title, aliens) }
        unlockOrder = chapters.flatMap { it.aliens }
        var n = 0
        all = chapters.drop(1).flatMapIndexed { ci, chapter ->
            chapter.aliens.mapIndexed { ai, alien ->
                val goals = plans[ci][ai]
                val species = roster[alien].species.uppercase()
                val title = when {
                    chapter.title == "FINAL MISSION" -> "CELESTIALSAPIEN TRIAL"
                    goals.size > 1 -> "FINALE · $species"
                    else -> "DNA TRACE · $species"
                }
                Mission(++n, chapter, alien, title, goals, xpPerChapter[ci])
            }
        }
    }
}

/**
 * Mission progress: counters for everything missions can ask for, a baseline per counter taken when
 * a mission starts (progress = counter − baseline), XP and rank. Steps come from the hardware step
 * counter sampled by the existing ~15-min sampler (no extra wake-ups).
 */
class DnaCodex(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("codex", Context.MODE_PRIVATE)

    init {
        Aliens.load(app)
        Missions.build()
        if (!prefs.contains(KEY_BASELINED)) startMission()      // first run: progress starts now
    }

    // ---- counters
    private var steps: Float
        get() = prefs.getFloat(KEY_STEPS, prefs.getFloat("dna", 0f) * 1000f)   // migrates v0.3 "dna"
        set(v) = prefs.edit { putFloat(KEY_STEPS, v) }

    private fun count(type: Objective): Int = when (type) {
        Objective.STEPS -> steps.toInt()
        Objective.TRANSFORMS -> prefs.getInt("n_transforms", 0)
        Objective.FULL_TIME -> prefs.getInt("n_full_time", 0)
        Objective.VARIETY -> prefs.getStringSet("aliens_used", emptySet())!!.size
        Objective.BIOSCANS -> prefs.getInt("n_bioscans", 0)
        Objective.STREAK -> prefs.getStringSet("active_days", emptySet())!!.size
    }

    private fun base(type: Objective) = prefs.getInt("base_${type.name}", 0)

    // ---- campaign state
    val completed: Int get() = prefs.getInt(KEY_COMPLETED, 0).coerceIn(0, Missions.all.size)
    val unlockedCount: Int get() = STARTERS + completed
    val xp: Int get() = prefs.getInt(KEY_XP, 0)
    val active: Mission? get() = Missions.all.getOrNull(completed)
    val allDone: Boolean get() = active == null

    val rank: Rank get() = if (allDone) Rank.BEN_10000 else Rank.entries.last { it != Rank.BEN_10000 && xp >= it.xp }
    val nextRank: Rank? get() = if (allDone) null else Rank.entries.firstOrNull { it.xp > xp }

    fun progress(goal: Goal): Int = (count(goal.type) - base(goal.type)).coerceIn(0, goal.target)

    /** Overall progress of the active mission, 0..1 (average over its goals). */
    val missionProgress: Float
        get() = active?.let { m -> m.goals.sumOf { progress(it).toDouble() / it.target }.toFloat() / m.goals.size } ?: 1f

    fun isUnlocked(index: Int, energy: Energy): Boolean =
        !Settings(app).codex || energy == Energy.MASTER || Missions.unlockOrder.indexOf(index) < unlockedCount

    /** Aliens unlocked since the user last saw them (roster indices, in unlock order). */
    fun newlyUnlocked(): List<Int> {
        val seen = prefs.getInt(KEY_SEEN, STARTERS).coerceAtMost(unlockedCount)
        return Missions.unlockOrder.subList(seen, unlockedCount)
    }

    fun markSeen() = prefs.edit { putInt(KEY_SEEN, unlockedCount) }

    // ---- events
    fun onTransform(alien: Int) {
        prefs.edit {
            putInt("n_transforms", prefs.getInt("n_transforms", 0) + 1)
            putStringSet("aliens_used", prefs.getStringSet("aliens_used", emptySet())!! + Aliens.all[alien].slug)
        }
        evaluate()
    }

    fun onFullTime() { prefs.edit { putInt("n_full_time", prefs.getInt("n_full_time", 0) + 1) }; evaluate() }

    fun onBioscan() { prefs.edit { putInt("n_bioscans", prefs.getInt("n_bioscans", 0) + 1) }; evaluate() }

    /**
     * Folds new steps from the hardware counter (cumulative since boot: a smaller value than last
     * time means the watch rebooted) into the totals and today's count for active days.
     */
    fun foldSteps(counter: Float) {
        val last = prefs.getFloat(KEY_LAST_COUNTER, -1f)
        val delta = when {
            last < 0f -> 0f
            counter < last -> counter
            else -> counter - last
        }
        prefs.edit { putFloat(KEY_LAST_COUNTER, counter) }
        if (delta <= 0f) return
        steps = max(0f, steps + delta)
        val today = LocalDate.now().toString()
        val todaySteps = (if (prefs.getString("today", "") == today) prefs.getFloat("today_steps", 0f) else 0f) + delta
        prefs.edit {
            putString("today", today)
            putFloat("today_steps", todaySteps)
            if (todaySteps >= ACTIVE_DAY_STEPS) putStringSet("active_days", prefs.getStringSet("active_days", emptySet())!! + today)
        }
        evaluate()
    }

    /** Completes every mission whose goals are all met (one event can finish more than one). */
    private fun evaluate() {
        var changed = false
        while (true) {
            val m = active ?: break
            if (m.goals.any { progress(it) < it.target }) break
            prefs.edit {
                putInt(KEY_COMPLETED, completed + 1)
                putInt(KEY_XP, xp + m.xp)
            }
            startMission()
            changed = true
        }
        dev.omnitrix.app.tiles.TileKit.refresh(app, dev.omnitrix.app.tiles.CodexTileService::class.java)
        if (changed) Diagnostics.log(app, "mission_complete", "completed=$completed")
    }

    /** Snapshots every counter so the newly active mission counts from now. */
    private fun startMission() = prefs.edit {
        Objective.entries.forEach { putInt("base_${it.name}", count(it)) }
        putBoolean(KEY_BASELINED, true)
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
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!listener.finished) { listener.finished = true; sensors.unregisterListener(listener); done() }
        }, 4000)
    }

    companion object {
        /** The original ten: Heatblast … Ghostfreak. */
        const val STARTERS = 10
        const val ACTIVE_DAY_STEPS = 5000f
        private const val KEY_STEPS = "steps_total"
        private const val KEY_LAST_COUNTER = "last_step_counter"
        private const val KEY_SEEN = "seen_unlocked"
        private const val KEY_COMPLETED = "missions_completed"
        private const val KEY_XP = "xp"
        private const val KEY_BASELINED = "baselined"

        @Suppress("unused") private fun clamp(v: Int, lo: Int, hi: Int) = min(hi, max(lo, v))
    }
}

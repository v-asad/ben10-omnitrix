package dev.omnitrix.app

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/**
 * Omnitrix sound cues (synthesized by tools/build_sounds.py).
 *
 * They play only when the watch itself is in Sound mode (never on Vibrate or Mute), not in Do Not
 * Disturb, and with a non-zero system volume. They use the system sounds volume, so the watch's
 * own volume level scales them.
 */
object Sounds {
    enum class Cue(val res: Int) {
        DIAL_UP(R.raw.sfx_dial_up), TICK(R.raw.sfx_tick), SLAM(R.raw.sfx_slam), TIMEOUT(R.raw.sfx_timeout),
        REVERT(R.raw.sfx_revert), DENIED(R.raw.sfx_denied), READY(R.raw.sfx_ready),
    }

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var pool: SoundPool? = null
    private val ids = IntArray(Cue.entries.size)
    private val loaded = BooleanArray(Cue.entries.size)
    private val pending = HashSet<Cue>()

    /** Loads the cues so the first play is instant (call when the app opens). */
    fun preload(context: Context) { ensure(context.applicationContext) }

    fun play(context: Context, cue: Cue) {
        if (!allowed(context)) return
        val p = ensure(context.applicationContext)
        synchronized(this) {
            if (loaded[cue.ordinal]) p.play(ids[cue.ordinal], 1f, 1f, 1, 0, 1f) else pending += cue
        }
    }

    /** Sound mode on, no DND, volume above zero, and the user hasn't turned Omnitrix sounds off. */
    fun allowed(context: Context): Boolean {
        if (!Settings(context).sound) return false
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        if (audio.getStreamVolume(AudioManager.STREAM_SYSTEM) == 0) return false
        val dnd = context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
        return dnd == null || dnd == NotificationManager.INTERRUPTION_FILTER_ALL || dnd == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    @Synchronized
    private fun ensure(context: Context): SoundPool = pool ?: SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(attributes)
        .build()
        .also { p ->
            p.setOnLoadCompleteListener { pool, sampleId, status ->
                val cue = Cue.entries.firstOrNull { ids[it.ordinal] == sampleId } ?: return@setOnLoadCompleteListener
                synchronized(this) {
                    loaded[cue.ordinal] = status == 0
                    if (pending.remove(cue) && status == 0) pool.play(sampleId, 1f, 1f, 1, 0, 1f)
                }
            }
            Cue.entries.forEach { ids[it.ordinal] = p.load(context, it.res, 1) }
            pool = p
        }
}

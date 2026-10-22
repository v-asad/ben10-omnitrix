package dev.omnitrix.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/** Alien time is up, or the recharge finished. Stays alive long enough for the sound to play. */
class TimeoutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        when (intent.action) {
            ACTION_RECHARGED -> Transformer.recharged(context)
            else -> Transformer.timeout(context)
        }
        Handler(Looper.getMainLooper()).postDelayed({ pending.finish() }, 2600)
    }

    companion object {
        const val ACTION_TIMEOUT = "dev.omnitrix.app.TIMEOUT"
        const val ACTION_RECHARGED = "dev.omnitrix.app.RECHARGED"
    }
}

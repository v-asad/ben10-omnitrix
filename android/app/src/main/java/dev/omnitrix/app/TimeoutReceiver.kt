package dev.omnitrix.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alien time is up: back to human. */
class TimeoutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        OmnitrixState(context).set(null)
        Transformer.refreshFace(context)
        Haptics.timeout(context)
    }
}

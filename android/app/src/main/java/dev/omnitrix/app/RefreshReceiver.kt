package dev.omnitrix.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-sends the face's complication data after an app update or a reboot.
 *
 * The face keeps the last data it received, including the tap PendingIntent. Reinstalling the
 * app cancels that PendingIntent, so without a refresh the core tap silently does nothing
 * (Samsung logs PendingIntent$CanceledException) until the next transform.
 */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Transformer.refreshFace(context)
            Diagnostics.schedule(context)
            Diagnostics.log(context, if (intent.action == Intent.ACTION_BOOT_COMPLETED) "boot" else "app_updated", intent.action?.substringAfterLast('.') ?: "")
        }
    }
}

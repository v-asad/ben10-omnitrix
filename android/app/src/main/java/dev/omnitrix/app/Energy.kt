package dev.omnitrix.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.ui.graphics.Color

/** The Omnitrix's energy core follows the watch battery, same thresholds as the face. */
enum class Energy(
    val main: Color,
    val bright: Color,
    val dim: Color,
    val ring: Color,
) {
    MASTER(Color(0xFFD6FF6A), Color(0xFFF4FFC0), Color(0xFF4A5A22), Color(0xFFC9B46A)),
    STANDARD(Color(0xFF7DFF4A), Color(0xFFD8FFC8), Color(0xFF2A4A24), Color(0xFF3A403C)),
    RECHARGE(Color(0xFFFF4A3A), Color(0xFFFFB8A8), Color(0xFF4A1A16), Color(0xFF3D2A2A));

    /** Transforming is locked when the energy core (battery) is low. */
    val locked get() = this == RECHARGE

    companion object {
        fun of(batteryPercent: Int) = when {
            batteryPercent > 90 -> MASTER
            batteryPercent < 20 -> RECHARGE
            else -> STANDARD
        }

        fun read(context: Context): Energy {
            // Sticky broadcast: returns the last battery state without registering a receiver.
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            return of(if (level < 0) 50 else level * 100 / scale)
        }
    }
}

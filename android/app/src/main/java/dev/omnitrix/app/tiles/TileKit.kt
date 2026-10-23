package dev.omnitrix.app.tiles

import android.content.Context
import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dev.omnitrix.app.MainActivity

/** Shared look and plumbing for the Omnitrix tiles: black, Omnitrix green, system font (tiles can't load custom fonts). */
internal object TileKit {
    const val GREEN = 0xFF7DFF4A.toInt()
    const val DIM = 0xFF6F9A66.toInt()
    const val FAINT = 0xFF1C2A1A.toInt()
    const val WHITE = 0xFFEAFFE0.toInt()
    const val RESOURCES_VERSION = "1"

    fun <T> done(value: T): ListenableFuture<T> = ResolvableFuture.create<T>().apply { set(value) }

    fun tile(root: LayoutElementBuilders.LayoutElement, freshnessMs: Long = 0): TileBuilders.Tile =
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(freshnessMs)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()

    fun resources(): ListenableFuture<ResourceBuilders.Resources> =
        done(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())

    fun text(value: String, size: Float, color: Int, bold: Boolean = true, spacing: Float = 0.05f) =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .setLetterSpacing(androidx.wear.protolayout.DimensionBuilders.em(spacing))
                    .build(),
            )
            .build()

    fun spacer(height: Float) = LayoutElementBuilders.Spacer.Builder().setHeight(dp(height)).build()

    /** Opens the Omnitrix app directly on a screen (bioscan, codex, settings). */
    fun open(context: Context, screen: String, id: String = "open_$screen") = ModifiersBuilders.Clickable.Builder()
        .setId(id)
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName(MainActivity::class.java.name)
                        .addKeyToExtraMapping(MainActivity.EXTRA_SCREEN, ActionBuilders.stringExtra(screen))
                        .build(),
                )
                .build(),
        )
        .build()

    /** Re-renders the tile in place (used by the settings toggles). */
    fun reload(id: String) = ModifiersBuilders.Clickable.Builder()
        .setId(id)
        .setOnClick(ActionBuilders.LoadAction.Builder().build())
        .build()

    fun clickable(click: ModifiersBuilders.Clickable) =
        ModifiersBuilders.Modifiers.Builder().setClickable(click).build()

    /** Asks the system to redraw a tile (e.g. after new DNA or a settings change in the app). */
    fun refresh(context: Context, tile: Class<out TileService>) = TileService.getUpdater(context).requestUpdate(tile)
}

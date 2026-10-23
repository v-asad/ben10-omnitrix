package dev.omnitrix.app.tiles

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileService
import dev.omnitrix.app.Diagnostics
import dev.omnitrix.app.Settings
import dev.omnitrix.app.tiles.TileKit.DIM
import dev.omnitrix.app.tiles.TileKit.FAINT
import dev.omnitrix.app.tiles.TileKit.GREEN
import dev.omnitrix.app.tiles.TileKit.text

/** Quick settings, toggled right in the tile (each tap re-renders it); MORE opens the full screen. */
class SettingsTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): com.google.common.util.concurrent.ListenableFuture<androidx.wear.tiles.TileBuilders.Tile> {
        val settings = Settings(this)
        when (requestParams.currentState.lastClickableId) {
            ID_SOUND -> settings.sound = !settings.sound
            ID_HAPTICS -> settings.haptics = next(Settings.Haptics.entries, settings.haptics)
            ID_CODEX -> { settings.codex = !settings.codex; Diagnostics.schedule(this); TileKit.refresh(this, CodexTileService::class.java) }
            ID_TIME -> settings.alienMinutes = next(Settings.ALIEN_MINUTES, settings.alienMinutes)
        }
        return TileKit.done(TileKit.tile(layout(settings)))
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) = TileKit.resources()

    private fun <T> next(list: List<T>, current: T) = list[(list.indexOf(current) + 1) % list.size]

    private fun chip(id: String, label: String, value: String, click: ModifiersBuilders.Clickable = TileKit.reload(id)) =
        Box.Builder()
            .setWidth(dp(156f)).setHeight(dp(34f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(click)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(FAINT))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(17f)).build())
                            .build(),
                    )
                    .setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(14f)).setEnd(dp(14f)).build())
                    .build(),
            )
            .addContent(
                Row.Builder()
                    .setWidth(expand())
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(Box.Builder().setWidth(expand()).setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                        .addContent(text(label, 11f, DIM, spacing = 0.1f)).build())
                    .addContent(text(value, 12f, GREEN))
                    .build(),
            )
            .build()

    private fun layout(s: Settings): LayoutElementBuilders.LayoutElement = Box.Builder()
        .setWidth(expand()).setHeight(expand())
        .addContent(
            Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(text("OMNITRIX", 11f, GREEN, spacing = 0.2f))
                .addContent(TileKit.spacer(6f))
                .addContent(chip(ID_SOUND, "SOUND", if (s.sound) "ON" else "OFF"))
                .addContent(TileKit.spacer(4f))
                .addContent(chip(ID_HAPTICS, "HAPTICS", s.haptics.label))
                .addContent(TileKit.spacer(4f))
                .addContent(chip(ID_CODEX, "DNA LOCKS", if (s.codex) "ON" else "OFF"))
                .addContent(TileKit.spacer(4f))
                .addContent(chip(ID_TIME, "ALIEN TIME", "${s.alienMinutes} MIN"))
                .addContent(TileKit.spacer(6f))
                .addContent(
                    Box.Builder().setModifiers(TileKit.clickable(TileKit.open(this, "settings")))
                        .addContent(text("ALL SETTINGS ›", 10f, DIM, spacing = 0.15f)).build(),
                )
                .build(),
        )
        .build()

    private companion object {
        const val ID_SOUND = "toggle_sound"
        const val ID_HAPTICS = "toggle_haptics"
        const val ID_CODEX = "toggle_codex"
        const val ID_TIME = "toggle_time"
    }
}

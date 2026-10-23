package dev.omnitrix.app.tiles

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Arc
import androidx.wear.protolayout.LayoutElementBuilders.ArcLine
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileService
import dev.omnitrix.app.Aliens
import dev.omnitrix.app.DnaCodex
import dev.omnitrix.app.Settings
import dev.omnitrix.app.tiles.TileKit.DIM
import dev.omnitrix.app.tiles.TileKit.FAINT
import dev.omnitrix.app.tiles.TileKit.GREEN
import dev.omnitrix.app.tiles.TileKit.WHITE
import dev.omnitrix.app.tiles.TileKit.text

/** DNA Codex at a glance: aliens unlocked, a ring of progress to the next, steps to go. */
class CodexTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) =
        TileKit.done(TileKit.tile(layout(), freshnessMs = 30 * 60_000L))

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) = TileKit.resources()

    private fun layout(): LayoutElementBuilders.LayoutElement {
        Aliens.load(this)
        val codex = DnaCodex(this)
        val total = Aliens.size
        val unlocked = codex.unlockedCount
        val all = unlocked >= total || !Settings(this).codex
        val sweep = if (all) 360f else 360f * codex.progressToNext
        val ring = Arc.Builder()
            .setAnchorAngle(degrees(0f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(ArcLine.Builder().setLength(degrees(sweep.coerceAtLeast(1f))).setThickness(dp(8f)).setColor(argb(GREEN)).build())
            .addContent(ArcLine.Builder().setLength(degrees((360f - sweep).coerceAtLeast(0f))).setThickness(dp(8f)).setColor(argb(FAINT)).build())
            .build()
        val column = Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("DNA CODEX", 11f, DIM, spacing = 0.2f))
            .addContent(text(if (unlocked >= total) "BEN 10,000" else "$unlocked / $total", 34f, WHITE))
            .addContent(TileKit.spacer(6f))
        if (all) {
            column.addContent(text(if (Settings(this).codex) "EVERY ALIEN UNLOCKED" else "LOCKS OFF · ALL ALIENS", 10f, GREEN, spacing = 0.1f))
        } else {
            column.addContent(text("NEXT: ${Aliens.all[unlocked].name}", 11f, GREEN, spacing = 0.1f))
            column.addContent(text("%,d STEPS".format(codex.stepsToNext), 11f, DIM, spacing = 0.1f))
        }
        return Box.Builder()
            .setWidth(expand()).setHeight(expand())
            .setModifiers(TileKit.clickable(TileKit.open(this, "codex")))
            .addContent(ring)
            .addContent(column.build())
            .build()
    }
}

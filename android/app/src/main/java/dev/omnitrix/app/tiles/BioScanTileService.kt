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
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat
import androidx.wear.protolayout.expression.PlatformHealthSources
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileService
import dev.omnitrix.app.tiles.TileKit.DIM
import dev.omnitrix.app.tiles.TileKit.GREEN
import dev.omnitrix.app.tiles.TileKit.WHITE
import dev.omnitrix.app.tiles.TileKit.text

/** Live heart rate streamed into the tile by the platform (no app wake-ups); tap for the full Bio-scan. */
class BioScanTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) = TileKit.done(TileKit.tile(layout()))

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) = TileKit.resources()

    private fun layout(): LayoutElementBuilders.LayoutElement {
        val bpm = TypeBuilders.StringProp.Builder("--")
            .setDynamicValue(
                PlatformHealthSources.heartRateBpm()
                    .format(DynamicFloat.FloatFormatter.Builder().setMaxFractionDigits(0).build()),
            )
            .build()
        val reading = LayoutElementBuilders.Text.Builder()
            .setText(bpm)
            .setLayoutConstraintsForDynamicText(TypeBuilders.StringLayoutConstraint.Builder("000").build())
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(androidx.wear.protolayout.DimensionBuilders.sp(52f))
                    .setColor(argb(WHITE))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .build(),
            )
            .build()
        val gauge = Arc.Builder()                                        // zone gauge decoration, calm → max
            .setAnchorAngle(degrees(-130f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(ArcLine.Builder().setLength(degrees(110f)).setThickness(dp(6f)).setColor(argb(0xFF2F8F2A.toInt())).build())
            .addContent(ArcLine.Builder().setLength(degrees(60f)).setThickness(dp(6f)).setColor(argb(0xFF9DFF2E.toInt())).build())
            .addContent(ArcLine.Builder().setLength(degrees(45f)).setThickness(dp(6f)).setColor(argb(0xFFFFB02E.toInt())).build())
            .addContent(ArcLine.Builder().setLength(degrees(45f)).setThickness(dp(6f)).setColor(argb(0xFFFF4A2E.toInt())).build())
            .build()
        return Box.Builder()
            .setWidth(expand()).setHeight(expand())
            .setModifiers(TileKit.clickable(TileKit.open(this, "bioscan")))
            .addContent(gauge)
            .addContent(
                Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(text("BIO-SCAN", 11f, DIM, spacing = 0.2f))
                    .addContent(reading)
                    .addContent(text("BPM", 12f, DIM, spacing = 0.3f))
                    .addContent(TileKit.spacer(10f))
                    .addContent(text("TAP TO SCAN", 10f, GREEN, spacing = 0.15f))
                    .build(),
            )
            .build()
    }
}

package dev.omnitrix.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

/** Transformation effect: family (fire, ice, electric…), two colours and an optional variant. */
class FxSpec(val family: String, val primary: Color, val secondary: Color, val variant: String?)

class Alien(
    val slug: String,
    val name: String,
    /** OS, AF, UA or ULTIMATE. */
    val era: String,
    val species: String,
    val planet: String,
    val powers: List<String>,
    val weakness: String,
    /** Fan ratings out of 10: STR, SPD, INT, DEF. */
    val stats: IntArray,
    val fx: FxSpec,
) {
    val eraLabel get() = when (era) {
        "OS" -> "ORIGINAL SERIES"
        "AF" -> "ALIEN FORCE"
        "UA" -> "ULTIMATE ALIEN"
        else -> "ULTIMATE FORM"
    }
}

/** The roster, in carousel order, from assets/aliens.json (data/aliens.json in the repo). */
object Aliens {
    val STAT_LABELS = listOf("STR", "SPD", "INT", "DEF")

    @Volatile private var roster: List<Alien>? = null

    val all: List<Alien> get() = checkNotNull(roster) { "Aliens.load(context) not called" }
    val size get() = all.size

    fun load(context: Context): List<Alien> = roster ?: synchronized(this) {
        roster ?: parse(context.assets.open("aliens.json").bufferedReader().use { it.readText() }).also { roster = it }
    }

    private fun parse(json: String): List<Alien> {
        val array = JSONObject(json).getJSONArray("aliens")
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            val powers = o.getJSONArray("powers")
            val stats = o.getJSONArray("stats")
            val fx = o.getJSONArray("fx")
            Alien(
                slug = o.getString("slug"),
                name = o.getString("name"),
                era = o.getString("era"),
                species = o.getString("species"),
                planet = o.getString("planet"),
                powers = List(powers.length()) { powers.getString(it) },
                weakness = o.getString("weakness"),
                stats = IntArray(stats.length()) { stats.getInt(it) },
                fx = FxSpec(
                    family = fx.getString(0),
                    primary = Color(android.graphics.Color.parseColor(fx.getString(1))),
                    secondary = Color(android.graphics.Color.parseColor(fx.getString(2))),
                    variant = if (fx.length() > 3) fx.getString(3) else null,
                ),
            )
        }
    }
}

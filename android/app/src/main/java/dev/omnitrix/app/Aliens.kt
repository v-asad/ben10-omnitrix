package dev.omnitrix.app

import androidx.annotation.DrawableRes

class Alien(
    val name: String,
    val species: String,
    val planet: String,
    val powers: List<String>,
    val weakness: String,
    val tool: String,
    /** Fan ratings out of 10: STR, SPD, INT, DEF. */
    val stats: IntArray,
    @DrawableRes val emblem: Int,
    @DrawableRes val glow: Int,
)

/** The starting roster, in carousel order (mirrors design/omnitrix.js). */
object Aliens {
    val STAT_LABELS = listOf("STR", "SPD", "INT", "DEF")

    val all = listOf(
        Alien("HEATBLAST", "Pyronite", "Pyros", listOf("Pyrokinesis", "Fire blasts", "Flame-jet flight"), "Water & extreme cold", "Flashlight", intArrayOf(6, 6, 5, 6), R.drawable.emblem_0, R.drawable.emblem_glow_0),
        Alien("XLR8", "Kineceleran", "Kinet", listOf("Super speed", "Hyper reflexes", "Visor shield"), "Low-friction surfaces", "Stopwatch", intArrayOf(4, 10, 5, 4), R.drawable.emblem_1, R.drawable.emblem_glow_1),
        Alien("FOUR ARMS", "Tetramand", "Khoros", listOf("Super strength", "Shockwave clap", "Huge leaps"), "Big, slow target", "Workout", intArrayOf(10, 4, 4, 8), R.drawable.emblem_2, R.drawable.emblem_glow_2),
        Alien("GREY MATTER", "Galvan", "Galvan Prime", listOf("Genius intellect", "Tiny size", "Tech know-how"), "Physically fragile", "Calculator", intArrayOf(1, 3, 10, 1), R.drawable.emblem_3, R.drawable.emblem_glow_3),
        Alien("DIAMONDHEAD", "Petrosapien", "Petropia", listOf("Crystal shards", "Diamond armor", "Light refraction"), "Sonic vibrations", "Brightness", intArrayOf(7, 4, 5, 10), R.drawable.emblem_4, R.drawable.emblem_glow_4),
        Alien("RIPJAWS", "Piscciss Volann", "Piscciss", listOf("Aquatic speed", "Crushing bite", "Deep-sea vision"), "Dries out on land", "Water lock", intArrayOf(6, 7, 4, 5), R.drawable.emblem_5, R.drawable.emblem_glow_5),
        Alien("UPGRADE", "Galvanic Mechamorph", "Galvan B", listOf("Merges with tech", "Upgrades machines", "Shapeshift"), "Water & EMP", "Settings", intArrayOf(3, 5, 8, 4), R.drawable.emblem_6, R.drawable.emblem_glow_6),
        Alien("GHOSTFREAK", "Ectonurite", "Anur Phaetos", listOf("Intangibility", "Invisibility", "Possession"), "Direct sunlight", "Do Not Disturb", intArrayOf(4, 6, 7, 7), R.drawable.emblem_7, R.drawable.emblem_glow_7),
        Alien("STINKFLY", "Lepidopterran", "Lepidopterra", listOf("Flight", "Slime spit", "Stink spray"), "Wet wings", "Weather", intArrayOf(3, 7, 4, 3), R.drawable.emblem_8, R.drawable.emblem_glow_8),
        Alien("WILDMUTT", "Vulpimancer", "Vulpin", listOf("Super senses", "Agility", "Echolocation"), "Loud noise, no eyes", "Find my phone", intArrayOf(6, 7, 3, 5), R.drawable.emblem_9, R.drawable.emblem_glow_9),
        Alien("CANNONBOLT", "Arburian Pelarota", "Arburia", listOf("Armored shell", "Rolling charge", "Ricochet"), "Hard to steer", "Steps", intArrayOf(7, 8, 3, 9), R.drawable.emblem_10, R.drawable.emblem_glow_10),
        Alien("ECHO ECHO", "Sonorosian", "Sonorosia", listOf("Sonic scream", "Self-cloning", "Sound waves"), "Fragile suit", "Music", intArrayOf(3, 4, 6, 3), R.drawable.emblem_11, R.drawable.emblem_glow_11),
        Alien("CLOCKWORK", "Chronosapien", "Unknown", listOf("Time rays", "Rewind", "Glimpse the future"), "Slow, heavy body", "Timer", intArrayOf(5, 2, 8, 7), R.drawable.emblem_12, R.drawable.emblem_glow_12),
    )

    val names get() = all.map { it.name }
    val size get() = all.size
}

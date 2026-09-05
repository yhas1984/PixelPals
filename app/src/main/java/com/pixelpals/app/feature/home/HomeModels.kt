package com.pixelpals.app.feature.home

import androidx.annotation.StringRes
import com.pixelpals.app.R
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.domain.PetType

enum class HomeEnvironment(@param:StringRes val title: Int) {
    COZY(R.string.home_cozy), GARDEN(R.string.home_garden), NIGHT(R.string.home_night)
}

enum class DecorationKind { BED, TOY, BOWL, PLANT, DISPLAY, LAMP }

data class Decoration(
    val id: String,
    @param:StringRes val title: Int,
    val kind: DecorationKind,
    val color: Int,
    val price: Int,
    val expedition: String? = null,
) {
    val action: CareSceneAction? get() = when (kind) {
        DecorationKind.BED -> CareSceneAction.REST
        DecorationKind.TOY -> CareSceneAction.PLAY
        DecorationKind.BOWL -> CareSceneAction.FEED
        else -> null
    }
}

object DecorationCatalog {
    val all: List<Decoration> = listOf(
        Decoration("linen_bed", R.string.decor_linen_bed, DecorationKind.BED, 0xFFE0A78FL.toInt(), 0),
        Decoration("moss_bed", R.string.decor_moss_bed, DecorationKind.BED, 0xFF99B88CL.toInt(), 60),
        Decoration("cloud_bed", R.string.decor_cloud_bed, DecorationKind.BED, 0xFFC1B8DDL.toInt(), 90),
        Decoration("moon_bed", R.string.decor_moon_bed, DecorationKind.BED, 0xFF8B93C6L.toInt(), 0, "night"),
        Decoration("ball", R.string.decor_ball, DecorationKind.TOY, 0xFFE7A75AL.toInt(), 0),
        Decoration("yarn", R.string.decor_yarn, DecorationKind.TOY, 0xFFC78EA8L.toInt(), 40),
        Decoration("pinwheel", R.string.decor_pinwheel, DecorationKind.TOY, 0xFF91B9BAL.toInt(), 55),
        Decoration("star_toy", R.string.decor_star_toy, DecorationKind.TOY, 0xFFE3C476L.toInt(), 75),
        Decoration("ceramic_bowl", R.string.decor_ceramic_bowl, DecorationKind.BOWL, 0xFF91B9BAL.toInt(), 0),
        Decoration("wood_bowl", R.string.decor_wood_bowl, DecorationKind.BOWL, 0xFFB98E6DL.toInt(), 35),
        Decoration("flower_bowl", R.string.decor_flower_bowl, DecorationKind.BOWL, 0xFFD9A1B0L.toInt(), 55),
        Decoration("moon_bowl", R.string.decor_moon_bowl, DecorationKind.BOWL, 0xFFB0AED2L.toInt(), 70),
        Decoration("fern", R.string.decor_fern, DecorationKind.PLANT, 0xFF7EAA86L.toInt(), 30),
        Decoration("sunflower", R.string.decor_sunflower, DecorationKind.PLANT, 0xFFE4BE60L.toInt(), 45),
        Decoration("mushrooms", R.string.decor_mushrooms, DecorationKind.PLANT, 0xFFCB8B78L.toInt(), 0, "forest"),
        Decoration("wildflowers", R.string.decor_wildflowers, DecorationKind.PLANT, 0xFFC79AB6L.toInt(), 0, "meadow"),
        Decoration("oak_shelf", R.string.decor_oak_shelf, DecorationKind.DISPLAY, 0xFFBD9978L.toInt(), 0),
        Decoration("glass_case", R.string.decor_glass_case, DecorationKind.DISPLAY, 0xFF9BC4C2L.toInt(), 80),
        Decoration("treasure_box", R.string.decor_treasure_box, DecorationKind.DISPLAY, 0xFFB58C6FL.toInt(), 60),
        Decoration("star_shelf", R.string.decor_star_shelf, DecorationKind.DISPLAY, 0xFFD8BA7FL.toInt(), 95),
        Decoration("paper_lamp", R.string.decor_paper_lamp, DecorationKind.LAMP, 0xFFF0C88CL.toInt(), 40),
        Decoration("lantern", R.string.decor_lantern, DecorationKind.LAMP, 0xFFDDA575L.toInt(), 60),
        Decoration("fireflies", R.string.decor_fireflies, DecorationKind.LAMP, 0xFFBED390L.toInt(), 80),
        Decoration("moon_lamp", R.string.decor_moon_lamp, DecorationKind.LAMP, 0xFFDDD1A3L.toInt(), 100),
    )
    fun find(id: String): Decoration? = all.firstOrNull { it.id == id }
    val starters: List<Decoration> = all.filter { it.price == 0 && it.expedition == null }
}

enum class ExpeditionDestination(
    val id: String,
    @param:StringRes val title: Int,
    @param:StringRes val story: Int,
    val durationMs: Long,
    val environment: HomeEnvironment,
    val treasureIndex: Int,
) {
    MEADOW("meadow", R.string.adventure_meadow, R.string.encounter_meadow, 5 * 60_000L, HomeEnvironment.GARDEN, 0),
    FOREST("forest", R.string.adventure_forest, R.string.encounter_forest, 15 * 60_000L, HomeEnvironment.COZY, 1),
    NIGHT("night", R.string.adventure_night, R.string.encounter_night, 30 * 60_000L, HomeEnvironment.NIGHT, 2);
    companion object {
        fun find(id: String): ExpeditionDestination? = entries.firstOrNull { it.id == id }
    }
}

/** Monotonic time wins within a boot; offline wall time is used only across boots. */
object ExpeditionClock {
    fun advance(elapsed: Long, remaining: Long, wallDelta: Long, uptimeDelta: Long, sameBoot: Boolean): Long {
        val delta: Long = if (sameBoot) uptimeDelta.coerceAtLeast(0) else wallDelta.coerceAtLeast(0)
        return elapsed + delta.coerceAtMost(remaining.coerceAtLeast(0))
    }
}

object HomeGrid {
    const val COLUMNS: Int = 5
    const val ROWS: Int = 3
    fun isValid(column: Int, row: Int): Boolean = column in 0 until COLUMNS && row in 0 until ROWS
}

data class CompanionProfile(val pet: PetType, val curiosity: Float, val tempo: Float, @param:StringRes val description: Int)

object CompanionProfiles {
    fun forPet(pet: PetType): CompanionProfile = CompanionProfile(pet,
        (pet.agility / 2f).coerceIn(.2f, 1f),
        com.pixelpals.app.core.care.scene.PetCareProfile.forPet(pet).tempo,
        when (pet) {
            PetType.CORGI -> R.string.companion_corgi
            PetType.TARO -> R.string.companion_taro
            PetType.BLOOP -> R.string.companion_bloop
            PetType.NUBE_MICHI -> R.string.companion_nube_michi
            PetType.JELLY -> R.string.companion_jelly
            PetType.GINGER -> R.string.companion_ginger
            PetType.ANGEL -> R.string.companion_angel
            PetType.PATITO -> R.string.companion_patito
            PetType.DIABLILLO -> R.string.companion_diablillo
            PetType.MOKI -> R.string.companion_moki
            PetType.YUKI -> R.string.companion_yuki
            PetType.PIRU -> R.string.companion_piru
            PetType.MENTA -> R.string.companion_menta
            PetType.TELA -> R.string.companion_tela
            PetType.LUMI -> R.string.companion_lumi
        })
}

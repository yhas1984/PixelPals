package com.pixelpals.app.core.care.scene

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Offsets are fractions of actor size, never screen pixels or frame counts. */
data class SpeciesCarePose(
    val x: Float = 0f,
    val y: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val alpha: Float = 1f,
)

object SpeciesCareMotion {
    fun sample(profile: PetCareProfile, action: CareSceneAction, progress: Float, reduced: Boolean,
               variation: CarePlayVariation = CarePlayVariation.DIRECT): SpeciesCarePose {
        if (reduced) return SpeciesCarePose()
        if (action == CareSceneAction.PET && profile.touch == CareTouchStyle.MISCHIEF)
            return SpeciesCarePose(rotation = ImpCareMotion.samplePetting(progress, false).leanDegrees)
        if (action == CareSceneAction.PLAY && profile.play == CarePlayStyle.BALLOON_POP) {
            val game: ImpBalloonPlayPose = ImpBalloonPlayMotion.sample(progress, false)
            return SpeciesCarePose(x = game.thrust * .025f, rotation = -game.thrust * 2.5f,
                y = -game.celebration * .018f)
        }
        val p: Float = progress.coerceIn(0f, 1f)
        val envelope: Float = sin(p * PI).toFloat()
        val wave: Float = sin(p * PI * 6 * profile.tempo).toFloat() * envelope
        return when (action) {
            CareSceneAction.FEED, CareSceneAction.MEDICINE -> feeding(profile, wave, envelope)
            CareSceneAction.PLAY -> {
                val beat: CarePlayBeat = CarePlayChoreography.sample(p, variation)
                playing(profile, beat.travel * envelope, beat.lift)
            }
            CareSceneAction.PET -> touching(profile, wave, envelope)
            CareSceneAction.REST -> resting(profile, wave, p)
            CareSceneAction.CLEAN -> when (profile.wash) {
                CareWashStyle.SPLASH -> SpeciesCarePose(y = -.025f * abs(wave), rotation = wave * 4f)
                CareWashStyle.BRUSH -> SpeciesCarePose(scaleY = 1f + .018f * wave)
                CareWashStyle.MIST -> SpeciesCarePose(scaleX = 1f + .014f * wave)
                CareWashStyle.SPARKLES -> SpeciesCarePose(y = -.015f * envelope, rotation = wave * 2f)
                CareWashStyle.SNOW -> SpeciesCarePose(rotation = wave * 3f)
                CareWashStyle.SPONGE -> SpeciesCarePose(rotation = wave * 2f)
            }
        }
    }

    private fun feeding(profile: PetCareProfile, wave: Float, envelope: Float): SpeciesCarePose = when (profile.feeding) {
        CareFeedingStyle.NIBBLE -> SpeciesCarePose(scaleY = 1f - .025f * abs(wave))
        CareFeedingStyle.ABSORB -> SpeciesCarePose(scaleX = 1f + .045f * envelope, scaleY = 1f + .025f * wave)
        CareFeedingStyle.PECK -> SpeciesCarePose(y = .025f * abs(wave), rotation = wave * 4f)
        CareFeedingStyle.HANDS -> SpeciesCarePose(rotation = wave * 2.5f, y = -.012f * abs(wave))
        CareFeedingStyle.TONGUE -> SpeciesCarePose(x = .015f * wave)
        CareFeedingStyle.SWALLOW -> SpeciesCarePose(scaleX = 1f + .035f * envelope, scaleY = 1f - .02f * envelope)
        CareFeedingStyle.WEB -> SpeciesCarePose(y = -.025f * wave)
    }

    private fun playing(profile: PetCareProfile, wave: Float, envelope: Float): SpeciesCarePose = when (profile.play) {
        CarePlayStyle.FETCH -> SpeciesCarePose(x = .07f * envelope)
        CarePlayStyle.FLOAT -> SpeciesCarePose(x = .035f * wave, y = -.06f * envelope)
        CarePlayStyle.PAW -> SpeciesCarePose(x = .04f * wave, rotation = wave * 4f)
        CarePlayStyle.BOUNCE -> SpeciesCarePose(y = -.09f * abs(wave), scaleX = 1f + .08f * wave, scaleY = 1f - .08f * wave)
        CarePlayStyle.GLIDE -> SpeciesCarePose(x = .05f * wave, y = -.06f * envelope, rotation = wave * 3f)
        CarePlayStyle.PADDLE -> SpeciesCarePose(x = .07f * wave, rotation = wave * 5f)
        CarePlayStyle.BALLOON_POP -> SpeciesCarePose()
        CarePlayStyle.PEEK -> SpeciesCarePose(x = .045f * wave, rotation = wave * 3f)
        CarePlayStyle.TWIRL -> SpeciesCarePose(rotation = wave * 9f)
        CarePlayStyle.SLIDE -> SpeciesCarePose(x = .11f * wave, scaleY = 1f - .05f * envelope, rotation = wave * 7f)
        CarePlayStyle.FOLLOW -> SpeciesCarePose(x = .035f * envelope)
        CarePlayStyle.SLITHER -> SpeciesCarePose(x = .065f * wave, scaleX = 1f + .04f * wave)
        CarePlayStyle.WEB -> SpeciesCarePose(y = -.07f * abs(wave), rotation = wave * 5f)
        CarePlayStyle.MAGIC_CHASE -> SpeciesCarePose(x = .09f * wave, y = -.085f * envelope, rotation = -wave * 4f)
        CarePlayStyle.CLOUD_DRIFT -> SpeciesCarePose(x = .075f * wave, y = -.095f * envelope,
            scaleX = 1f + .07f * envelope, scaleY = 1f - .04f * envelope, alpha = 1f - .23f * envelope)
    }

    private fun touching(profile: PetCareProfile, wave: Float, envelope: Float): SpeciesCarePose = when (profile.touch) {
        CareTouchStyle.NUZZLE -> SpeciesCarePose(x = .018f * wave, rotation = wave * 3f)
        CareTouchStyle.FADE -> SpeciesCarePose(y = -.03f * envelope, alpha = 1f - .22f * abs(wave))
        CareTouchStyle.SQUISH -> SpeciesCarePose(scaleX = 1f + .09f * abs(wave), scaleY = 1f - .09f * abs(wave))
        CareTouchStyle.WINGS -> SpeciesCarePose(y = -.035f * abs(wave), rotation = wave * 2f)
        CareTouchStyle.MISCHIEF -> SpeciesCarePose(rotation = wave * 7f)
        CareTouchStyle.SHIMMER -> SpeciesCarePose(scaleY = 1f + .012f * wave)
        CareTouchStyle.FLURRY -> SpeciesCarePose(rotation = wave * 3f, y = -.012f * abs(wave))
        CareTouchStyle.SHELL -> SpeciesCarePose(scaleY = 1f - .035f * abs(wave))
        CareTouchStyle.COIL -> SpeciesCarePose(scaleX = 1f - .04f * abs(wave), scaleY = 1f + .025f * abs(wave))
        CareTouchStyle.SILK -> SpeciesCarePose(y = -.035f * wave)
        CareTouchStyle.GLOW -> SpeciesCarePose(scaleX = 1f + .025f * wave, scaleY = 1f + .025f * wave, alpha = 1f - .12f * abs(wave))
        CareTouchStyle.CLOUD_PUFF -> SpeciesCarePose(y = -.065f * envelope, scaleX = 1f + .08f * envelope,
            scaleY = 1f - .04f * envelope, alpha = 1f - .18f * envelope)
    }

    private fun resting(profile: PetCareProfile, wave: Float, progress: Float): SpeciesCarePose = when (profile.bed) {
        CareBed.MOON_MIST, CareBed.CLOUD, CareBed.CLOUD_CRADLE, CareBed.STARLIGHT -> SpeciesCarePose(y = -.025f * wave, alpha = if (profile.bed == CareBed.MOON_MIST) 1f - .15f * progress else 1f)
        CareBed.WING_WRAP -> SpeciesCarePose(scaleY = 1f + .006f * sin(progress * PI * 4).toFloat())
        CareBed.WEB, CareBed.BRANCH -> SpeciesCarePose(rotation = wave * 3f)
        CareBed.PUDDLE -> SpeciesCarePose(scaleX = 1f + .07f * progress, scaleY = 1f - .07f * progress)
        CareBed.WARM_LEAF -> SpeciesCarePose(scaleX = 1f - .025f * progress, scaleY = 1f + .015f * wave)
        else -> SpeciesCarePose(scaleY = 1f + .008f * wave)
    }
}

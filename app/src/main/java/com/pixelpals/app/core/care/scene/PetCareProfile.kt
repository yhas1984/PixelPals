package com.pixelpals.app.core.care.scene

import com.pixelpals.app.core.domain.PetType

enum class CareFood { BOWL, MIST, DEWDROPS, FRUIT, FISH, STAR, SEEDS, CHILI, FLY, SNOWFLAKE, LITTLE_FISH, LETTUCE, EGG, CRICKET, BERRIES }
enum class CareToy { BALL, BUBBLE, RAINBOW, SPRING, FEATHER, HALO, LEAF, BALLOONS, DANCING_LEAF, CRYSTAL, PUCK, PINWHEEL, HOOP, SILK, MAGIC_ORB }
enum class CareBed { CUSHION, MOON_MIST, CLOUD, PUDDLE, BASKET, CLOUD_CRADLE, NEST, WING_WRAP, BRANCH, SNOW, ICE, MOSS, WARM_LEAF, WEB, STARLIGHT }
enum class CareFeedingStyle { NIBBLE, ABSORB, PECK, HANDS, TONGUE, SWALLOW, WEB }
enum class CarePlayStyle { FETCH, FLOAT, PAW, BOUNCE, GLIDE, PADDLE, BALLOON_POP, PEEK, TWIRL, SLIDE, FOLLOW, SLITHER, WEB, MAGIC_CHASE, CLOUD_DRIFT }
enum class CareTouchStyle { NUZZLE, FADE, SQUISH, WINGS, MISCHIEF, SHIMMER, FLURRY, SHELL, COIL, SILK, GLOW, CLOUD_PUFF }
enum class CareWashStyle { SPONGE, MIST, BRUSH, SPLASH, SPARKLES, SNOW }

/** Species determines anatomy; each pet's established temperament determines its rhythm. */
data class PetCareProfile(
    val food: CareFood,
    val toy: CareToy,
    val bed: CareBed,
    val feeding: CareFeedingStyle,
    val play: CarePlayStyle,
    val touch: CareTouchStyle,
    val wash: CareWashStyle,
    val tempo: Float,
) {
    companion object {
        fun forPet(pet: PetType): PetCareProfile = when (pet) {
            PetType.CORGI -> PetCareProfile(CareFood.BOWL, CareToy.BALL, CareBed.CUSHION, CareFeedingStyle.NIBBLE, CarePlayStyle.FETCH, CareTouchStyle.NUZZLE, CareWashStyle.SPONGE, 1.2f)
            PetType.BLOOP -> PetCareProfile(CareFood.MIST, CareToy.BUBBLE, CareBed.MOON_MIST, CareFeedingStyle.ABSORB, CarePlayStyle.FLOAT, CareTouchStyle.FADE, CareWashStyle.MIST, .65f)
            PetType.NUBE_MICHI -> PetCareProfile(CareFood.DEWDROPS, CareToy.RAINBOW, CareBed.CLOUD, CareFeedingStyle.ABSORB, CarePlayStyle.CLOUD_DRIFT, CareTouchStyle.CLOUD_PUFF, CareWashStyle.MIST, .65f)
            PetType.JELLY -> PetCareProfile(CareFood.FRUIT, CareToy.SPRING, CareBed.PUDDLE, CareFeedingStyle.ABSORB, CarePlayStyle.BOUNCE, CareTouchStyle.SQUISH, CareWashStyle.SPLASH, 1.3f)
            PetType.GINGER -> PetCareProfile(CareFood.FISH, CareToy.FEATHER, CareBed.BASKET, CareFeedingStyle.NIBBLE, CarePlayStyle.PAW, CareTouchStyle.NUZZLE, CareWashStyle.BRUSH, .95f)
            PetType.ANGEL -> PetCareProfile(CareFood.STAR, CareToy.HALO, CareBed.CLOUD_CRADLE, CareFeedingStyle.HANDS, CarePlayStyle.GLIDE, CareTouchStyle.WINGS, CareWashStyle.SPARKLES, .7f)
            PetType.PATITO -> PetCareProfile(CareFood.SEEDS, CareToy.LEAF, CareBed.NEST, CareFeedingStyle.PECK, CarePlayStyle.PADDLE, CareTouchStyle.WINGS, CareWashStyle.SPLASH, 1.1f)
            PetType.DIABLILLO -> PetCareProfile(CareFood.CHILI, CareToy.BALLOONS, CareBed.WING_WRAP, CareFeedingStyle.HANDS, CarePlayStyle.BALLOON_POP, CareTouchStyle.MISCHIEF, CareWashStyle.SPARKLES, 1.6f)
            PetType.MOKI -> PetCareProfile(CareFood.FLY, CareToy.DANCING_LEAF, CareBed.BRANCH, CareFeedingStyle.TONGUE, CarePlayStyle.PEEK, CareTouchStyle.SHIMMER, CareWashStyle.MIST, 1f)
            PetType.YUKI -> PetCareProfile(CareFood.SNOWFLAKE, CareToy.CRYSTAL, CareBed.SNOW, CareFeedingStyle.ABSORB, CarePlayStyle.TWIRL, CareTouchStyle.FLURRY, CareWashStyle.SNOW, .8f)
            PetType.PIRU -> PetCareProfile(CareFood.LITTLE_FISH, CareToy.PUCK, CareBed.ICE, CareFeedingStyle.PECK, CarePlayStyle.SLIDE, CareTouchStyle.WINGS, CareWashStyle.SPLASH, 1.25f)
            PetType.TARO -> PetCareProfile(CareFood.LETTUCE, CareToy.PINWHEEL, CareBed.MOSS, CareFeedingStyle.NIBBLE, CarePlayStyle.FOLLOW, CareTouchStyle.SHELL, CareWashStyle.BRUSH, .45f)
            PetType.MENTA -> PetCareProfile(CareFood.EGG, CareToy.HOOP, CareBed.WARM_LEAF, CareFeedingStyle.SWALLOW, CarePlayStyle.SLITHER, CareTouchStyle.COIL, CareWashStyle.MIST, .6f)
            PetType.TELA -> PetCareProfile(CareFood.CRICKET, CareToy.SILK, CareBed.WEB, CareFeedingStyle.WEB, CarePlayStyle.WEB, CareTouchStyle.SILK, CareWashStyle.MIST, 1.15f)
            PetType.LUMI -> PetCareProfile(CareFood.BERRIES, CareToy.MAGIC_ORB, CareBed.STARLIGHT, CareFeedingStyle.NIBBLE, CarePlayStyle.MAGIC_CHASE, CareTouchStyle.GLOW, CareWashStyle.SPARKLES, .95f)
        }
    }
}

package com.pixelpals.app.core.motion

import kotlin.random.Random

interface PetRandom {
    fun nextFloat(): Float
    fun nextInt(from: Int, until: Int): Int
    fun nextBoolean(): Boolean = nextFloat() < 0.5f
}

class DefaultPetRandom(private val random: Random = Random.Default) : PetRandom {
    override fun nextFloat(): Float = random.nextFloat()
    override fun nextInt(from: Int, until: Int): Int = random.nextInt(from, until)
}

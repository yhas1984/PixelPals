package com.pixelpals.app.debug

internal enum class LumiReviewClip(
    val frames: IntArray,
    val frameDurationsMs: IntArray,
) {
    IDLE(intArrayOf(0, 1, 2, 3), intArrayOf(1000, 1200, 1200, 1000)),
    WALK(intArrayOf(4, 5, 6, 7, 8, 9, 10, 11), IntArray(8) { 170 }),
    TURN(intArrayOf(12, 13, 14, 15), IntArray(4) { 425 }),
    HOP_UP(intArrayOf(16, 17, 18, 19), IntArray(4) { 690 }),
    HOP_DOWN(intArrayOf(20, 21, 22, 23), IntArray(4) { 690 }),
    FRONT_SOCIAL(intArrayOf(24, 25, 26, 27), intArrayOf(900, 1200, 900, 1100)),
    POUNCE(intArrayOf(28, 29, 30, 31), intArrayOf(420, 520, 420, 720)),
    SLEEP(intArrayOf(32, 33, 34, 35), intArrayOf(1500, 1800, 1800, 1500)),
    MAGIC(intArrayOf(36, 37, 38, 39), intArrayOf(520, 720, 900, 700)),
}

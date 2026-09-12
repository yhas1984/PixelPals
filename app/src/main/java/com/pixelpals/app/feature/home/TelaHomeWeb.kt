package com.pixelpals.app.feature.home

import com.pixelpals.app.core.motion.GroundGait
import kotlin.math.*

internal data class WebPoint(val x: Float, val y: Float)

/** Geometry and routes share edges: every step follows a visible silk thread. */
internal class TelaHomeWeb {
    val flies: List<WebPoint> = FLY_NODES.map { NODES[it] }
    var x: Float = CENTER.x; private set
    var y: Float = CENTER.y; private set
    var angleDegrees: Float = 0f; private set
    var distanceTravelled: Float = 0f; private set
    var clip: String = "idle"; private set
    var clipSeconds: Float = 0f; private set
    var isEating: Boolean = false; private set
    var isHunting: Boolean = false; private set
    var currentNodeIndex: Int = 0; private set
    private var next: Int? = null
    private var requestedFly: Int? = null
    private var signalled: Boolean = false
    private var elapsed: Float = 0f
    private var duration: Float = 1f
    private var turnFrom: Float = 0f
    private var turnTo: Float = 0f
    private var turning: Boolean = false
    private var idle: Float = 1.5f
    private var patrolStep: Int = 0
    private val returnIn: FloatArray = FloatArray(flies.size)
    fun isFlyVisible(index: Int): Boolean = index in flies.indices && returnIn[index] <= 0f
    fun hunt(index: Int): Boolean {
        if (isHunting || !isFlyVisible(index)) return false
        requestedFly = index
        isHunting = true
        idle = 0f
        return true
    }
    fun cancelHunt(): Unit {
        requestedFly = null; isHunting = false; isEating = false; signalled = false
        clip = if (next != null && !turning) "walk" else "idle"
        // Finish the current edge before choosing another route; never jump back to a node.
    }
    fun finishMeal(success: Boolean): Unit {
        if (success && isEating) requestedFly?.let { returnIn[it] = 35f }
        cancelHunt()
        idle = 2f
    }
    fun advance(delta: Float, reduced: Boolean = false, resting: Boolean = false,
        tempo: Float = 1f, initiative: Float = 1f): Boolean {
        val dt: Float = delta.coerceIn(0f, .1f)
        for (i: Int in returnIn.indices) returnIn[i] = (returnIn[i] - dt).coerceAtLeast(0f)
        if (resting) { clip = "idle"; return false }
        if (isEating) {
            clip = "feed"; clipSeconds += dt
            if (clipSeconds >= MEAL_SECONDS && !signalled) { signalled = true; return true }
            return false
        }
        if (reduced && !isHunting) { clip = "idle"; return false }
        if (next == null) {
            val destination: Int? = requestedFly?.let { FLY_NODES[it] }
            if (destination == currentNodeIndex) {
                isEating = true; clip = "feed"; clipSeconds = 0f
                return false
            }
            idle -= dt * initiative.coerceIn(.7f, 1.3f)
            if (idle > 0f) { clip = "idle"; return false }
            val target: Int = if (destination != null) route(currentNodeIndex, destination).first()
                else neighbors(currentNodeIndex).let { it[(patrolStep++ / 2) % it.size] }
            beginEdge(target, tempo)
        }
        val target: Int = next ?: return false
        if (turning) {
            elapsed += dt
            angleDegrees = turnFrom + angleDelta(turnFrom, turnTo) * GroundGait.progress(elapsed, .45f)
            clip = "idle"
            if (elapsed >= .45f) { turning = false; elapsed = 0f; angleDegrees = turnTo }
            return false
        }
        elapsed += dt
        val start: WebPoint = NODES[currentNodeIndex]
        val end: WebPoint = NODES[target]
        val progress: Float = GroundGait.progress(elapsed, duration)
        val nextX: Float = start.x + (end.x - start.x) * progress
        val nextY: Float = start.y + (end.y - start.y) * progress
        distanceTravelled += hypot(nextX - x, nextY - y)
        x = nextX; y = nextY
        clip = if (reduced) "idle" else "walk"
        clipSeconds = distanceTravelled / 105f * 1.44f
        if (elapsed >= duration) {
            currentNodeIndex = target; next = null; clip = "idle"; idle = if (isHunting) 0f else 1.2f
        }
        return false
    }
    private fun beginEdge(target: Int, tempo: Float): Unit {
        next = target; elapsed = 0f; turning = true
        val end: WebPoint = NODES[target]
        turnFrom = angleDegrees
        turnTo = Math.toDegrees(atan2((end.y - y).toDouble(), (end.x - x).toDouble())).toFloat()
        duration = GroundGait.duration(hypot(end.x - x, end.y - y), 125f * tempo.coerceIn(.7f, 1.3f), .8f)
    }
    private fun route(start: Int, end: Int): List<Int> {
        // Small graph: breadth-first paths avoid any diagonal shortcuts across the web.
        val paths: ArrayDeque<List<Int>> = ArrayDeque<List<Int>>().apply { add(listOf(start)) }
        val seen: MutableSet<Int> = mutableSetOf(start)
        while (paths.isNotEmpty()) {
            val path: List<Int> = paths.removeFirst()
            if (path.last() == end) return path.drop(1)
            for (neighbor: Int in neighbors(path.last())) if (seen.add(neighbor)) paths.add(path + neighbor)
        }
        error("Disconnected web")
    }
    companion object {
        const val MEAL_SECONDS: Float = 2f
        val CENTER: WebPoint = WebPoint(500f, 380f)
        val BOUNDARY: List<WebPoint> = (0..7).map {
            val a: Double = Math.toRadians(it * 45.0)
            val dx: Float = cos(a).toFloat(); val dy: Float = sin(a).toFloat()
            val radius: Float = minOf(440f / abs(dx).coerceAtLeast(.001f), 310f / abs(dy).coerceAtLeast(.001f))
            WebPoint(CENTER.x + dx * radius, CENTER.y + dy * radius)
        }
        val NODES: List<WebPoint> = listOf(CENTER) + listOf(.32f, .62f, .90f).flatMap { scale ->
            BOUNDARY.map { WebPoint(CENTER.x + (it.x - CENTER.x) * scale, CENTER.y + (it.y - CENTER.y) * scale) }
        }
        val FLY_NODES: List<Int> = listOf(10, 20, 15)
        fun neighbors(index: Int): List<Int> {
            if (index == 0) return (1..8).toList()
            val ring: Int = (index - 1) / 8
            val spoke: Int = (index - 1) % 8
            val around: List<Int> = listOf(1 + ring * 8 + (spoke + 7) % 8, 1 + ring * 8 + (spoke + 1) % 8)
            return around + (if (ring == 0) 0 else index - 8) + if (ring < 2) listOf(index + 8) else emptyList()
        }
        private fun angleDelta(from: Float, to: Float): Float = ((to - from + 540f) % 360f) - 180f
    }
}

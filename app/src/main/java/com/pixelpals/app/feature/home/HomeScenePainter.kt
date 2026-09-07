package com.pixelpals.app.feature.home

import android.graphics.*
import kotlin.math.*

/** Resolution-independent artwork shared by the room, shop, postcards and encounters. */
class HomeScenePainter {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path: Path = Path()
    private var gradient: LinearGradient? = null
    private var gradientColors: Pair<Int, Int>? = null

    private fun oval(canvas: Canvas, color: Int, x: Float, y: Float, width: Float, height: Float): Unit {
        paint.color = color; canvas.drawOval(x, y, x + width, y + height, paint)
    }
    private fun box(canvas: Canvas, color: Int, x: Float, y: Float, width: Float, height: Float, radius: Float = 12f): Unit {
        paint.color = color; canvas.drawRoundRect(x, y, x + width, y + height, radius, radius, paint)
    }

    fun drawBackground(canvas: Canvas, environment: HomeEnvironment, hour: Int): Unit {
        val night: Boolean = environment == HomeEnvironment.NIGHT || hour < 7 || hour >= 20
        val top: Int = if (night) 0xFF283C4D.toInt() else if (environment == HomeEnvironment.GARDEN) 0xFFCDE1D8.toInt() else 0xFFF2E5D0.toInt()
        val bottom: Int = if (night) 0xFF72777E.toInt() else 0xFFF9EDD6.toInt()
        if (gradientColors?.first != top || gradientColors?.second != bottom) {
            gradient = LinearGradient(0f, 0f, 0f, 760f, top, bottom, Shader.TileMode.CLAMP)
            gradientColors = top to bottom
        }
        paint.color = Color.WHITE
        paint.shader = gradient
        canvas.drawRect(0f, 0f, 1000f, 760f, paint); paint.shader = null
        if (environment == HomeEnvironment.COZY) drawRoom(canvas, night) else drawLandscape(canvas, night)
        val floor: Int = if (night) 0xFF7C7974.toInt() else if (environment == HomeEnvironment.GARDEN) 0xFFB4C79D.toInt() else 0xFFDCC5A5.toInt()
        box(canvas, floor, 0f, 460f, 1000f, 300f, 0f)
        paint.color = if (night) 0x22707080 else 0x33796242
        paint.strokeWidth = 2f
        for (i: Int in 0..6) canvas.drawLine(0f, 460f + i * 55f, 1000f, 460f + i * 55f, paint)
        if (environment == HomeEnvironment.COZY) for (i: Int in 0..7) canvas.drawLine(i * 170f - 80f, 460f, i * 190f - 150f, 760f, paint)
        oval(canvas, if (night) 0xFF9C9591.toInt() else 0xFFF3E4C9.toInt(), 105f, 490f, 790f, 210f)
        oval(canvas, if (night) 0xFFADB0A4.toInt() else 0xFFC7D0B4.toInt(), 130f, 500f, 740f, 185f)
        paint.color = if (night) 0x4482877B else 0x44F9F0D9
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f
        canvas.drawOval(158f, 516f, 842f, 670f, paint); paint.style = Paint.Style.FILL
        // Foreground leaves frame the scene without occupying the editing grid.
        drawLeaves(canvas, 20f, 730f, 1.25f, if (night) 0xFF425C56.toInt() else 0xFF79956C.toInt())
        drawLeaves(canvas, 985f, 747f, -1.1f, if (night) 0xFF425C56.toInt() else 0xFF79956C.toInt())
    }

    private fun drawRoom(canvas: Canvas, night: Boolean): Unit {
        paint.color = if (night) 0x184D5860 else 0x20AB9073
        for (x: Int in 0..10) canvas.drawRect(x * 100f, 0f, x * 100f + 2, 460f, paint)
        box(canvas, 0xFFA88B6A.toInt(), 298f, 48f, 404f, 310f, 150f)
        box(canvas, if (night) 0xFF243D53.toInt() else 0xFFD8E5D9.toInt(), 318f, 66f, 364f, 270f, 135f)
        oval(canvas, if (night) 0xFFFFE9B2.toInt() else 0xFFFFF2CA.toInt(), 555f, 93f, 65f, 65f)
        box(canvas, 0xFFB39B79.toInt(), 491f, 64f, 18f, 280f, 0f)
        box(canvas, 0xFFB39B79.toInt(), 316f, 216f, 369f, 14f, 0f)
        box(canvas, 0xFFBD9F7D.toInt(), 279f, 341f, 443f, 24f)
        box(canvas, if (night) 0xFF64716E.toInt() else 0xFFE8D7B7.toInt(), 236f, 51f, 61f, 307f, 22f)
        box(canvas, if (night) 0xFF64716E.toInt() else 0xFFE8D7B7.toInt(), 705f, 51f, 61f, 307f, 22f)
        box(canvas, 0xFFAF906E.toInt(), 786f, 145f, 130f, 162f, 8f)
        box(canvas, 0xFFF8ECD5.toInt(), 797f, 156f, 108f, 139f, 5f)
        drawLeaves(canvas, 850f, 270f, .65f, 0xFF8C9F7A.toInt())
        box(canvas, 0xFFAB8965.toInt(), 0f, 446f, 1000f, 18f, 0f)
    }

    private fun drawLandscape(canvas: Canvas, night: Boolean): Unit {
        oval(canvas, if (night) 0xFFF6E5AC.toInt() else 0xFFFFF2C9.toInt(), 729f, 68f, 101f, 101f)
        if (night) {
            paint.color = 0xFFFFE8B5.toInt()
            for (i: Int in 0..28) canvas.drawCircle((i * 137 % 930 + 30).toFloat(), (i * 71 % 310 + 20).toFloat(), if (i % 3 == 0) 3f else 1.5f, paint)
        } else {
            oval(canvas, 0x99FFFFFF.toInt(), 80f, 90f, 170f, 45f)
            oval(canvas, 0x99FFFFFF.toInt(), 440f, 128f, 220f, 42f)
        }
        oval(canvas, if (night) 0xFF4B6864.toInt() else 0xFFA5BDA0.toInt(), -260f, 228f, 1020f, 480f)
        oval(canvas, if (night) 0xFF5D7668.toInt() else 0xFF8DAB8B.toInt(), 425f, 248f, 1050f, 430f)
        box(canvas, 0xFF897B5D.toInt(), 84f, 224f, 31f, 250f)
        oval(canvas, if (night) 0xFF36574C.toInt() else 0xFF789A76.toInt(), -86f, 47f, 330f, 320f)
        oval(canvas, if (night) 0xFF456958.toInt() else 0xFF91AC7F.toInt(), -24f, 70f, 205f, 175f)
        box(canvas, 0xFF897B5D.toInt(), 915f, 274f, 25f, 200f)
        oval(canvas, if (night) 0xFF36574C.toInt() else 0xFF789A76.toInt(), 822f, 150f, 270f, 244f)
        for (i: Int in 0..9) {
            val x: Float = (i * 97 + 40).toFloat()
            oval(canvas, if (night) 0xFFABB39A.toInt() else 0xFFE7CD9D.toInt(), x, 425f + i % 3 * 9f, 11f, 11f)
        }
    }

    private fun drawLeaves(canvas: Canvas, x: Float, y: Float, scale: Float, color: Int): Unit {
        canvas.save(); canvas.translate(x, y); canvas.scale(scale, abs(scale))
        paint.color = color; paint.strokeWidth = 5f
        canvas.drawLine(0f, 0f, 0f, -125f, paint)
        for (i: Int in 0..3) {
            val height: Float = -24f - i * 28f
            oval(canvas, color, -43f, height - 23f, 46f, 23f)
            oval(canvas, color, 0f, height - 38f, 47f, 23f)
        }
        canvas.restore()
    }

    fun drawObject(canvas: Canvas, item: Decoration, bounds: RectF, treasure: String? = null): Unit {
        canvas.save(); canvas.translate(bounds.left, bounds.top); canvas.scale(bounds.width() / 100f, bounds.height() / 100f)
        val color: Int = item.color
        oval(canvas, 0x22736C54, 8f, 83f, 86f, 13f)
        when (item.kind) {
            DecorationKind.BED -> {
                oval(canvas, 0xFF987B68.toInt(), 3f, 47f, 94f, 42f)
                oval(canvas, color, 3f, 39f, 94f, 42f)
                oval(canvas, 0x55FFFFFF, 15f, 43f, 69f, 27f)
                box(canvas, color, 20f, 28f, 37f, 24f, 12f)
            }
            DecorationKind.TOY -> {
                if (item.id == "pinwheel") {
                    box(canvas, 0xFFA78365.toInt(), 48f, 37f, 4f, 52f, 1f)
                    for (i: Int in 0..3) {
                        canvas.save(); canvas.rotate(i * 90f, 50f, 35f)
                        path.reset(); path.moveTo(50f, 35f); path.lineTo(20f, 8f); path.lineTo(20f, 35f); path.close()
                        paint.color = if (i % 2 == 0) color else 0xFFE4C486.toInt(); canvas.drawPath(path, paint); canvas.restore()
                    }
                } else if (item.id == "star_toy") drawStar(canvas, color, 50f, 57f, 30f)
                else {
                    oval(canvas, color, 25f, 34f, 52f, 52f)
                    paint.color = 0x77FFFFFF; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
                    canvas.drawArc(31f, 34f, 62f, 86f, 85f, 180f, false, paint)
                    if (item.id == "yarn") for (i: Int in 0..3) canvas.drawArc(28f, 39f + i * 7f, 74f, 70f + i * 4f, 10f, 150f, false, paint)
                    paint.style = Paint.Style.FILL
                    oval(canvas, 0x66FFFFFF, 35f, 40f, 10f, 8f)
                }
            }
            DecorationKind.BOWL -> {
                box(canvas, color, 15f, 56f, 70f, 30f, 14f)
                oval(canvas, color, 12f, 45f, 76f, 28f)
                oval(canvas, 0xFF716A5C.toInt(), 21f, 49f, 58f, 19f)
                for (i: Int in 0..4) oval(canvas, 0xFFD6B376.toInt(), 29f + i * 8f, 54f + i % 2 * 3f, 8f, 6f)
            }
            DecorationKind.PLANT -> {
                box(canvas, 0xFFBD8C73.toInt(), 32f, 62f, 37f, 27f, 6f)
                if (item.id == "mushrooms") {
                    box(canvas, 0xFFEADBC0.toInt(), 47f, 37f, 8f, 28f)
                    oval(canvas, color, 22f, 20f, 58f, 30f)
                    oval(canvas, 0xFFF6E7CE.toInt(), 39f, 26f, 8f, 7f)
                } else {
                    drawLeaves(canvas, 50f, 66f, .42f, 0xFF6A946E.toInt())
                    if (item.id != "fern") {
                        for (i: Int in 0..4) oval(canvas, color, 38f + cos(i * 1.25).toFloat() * 12, 15f + sin(i * 1.25).toFloat() * 12, 15f, 15f)
                        oval(canvas, 0xFFEFD695.toInt(), 42f, 19f, 10f, 10f)
                    }
                }
            }
            DecorationKind.DISPLAY -> {
                if (item.id == "glass_case") box(canvas, 0x559BC4C2, 16f, 20f, 70f, 55f, 8f)
                box(canvas, color, 10f, 72f, 82f, if (item.id == "treasure_box") 20f else 10f, 4f)
                box(canvas, color, 19f, 81f, 6f, 12f, 0f); box(canvas, color, 78f, 81f, 6f, 12f, 0f)
                if (treasure == null || !paint.hasGlyph(treasure)) drawStar(canvas, 0xFFE8C77E.toInt(), 52f, 53f, 19f)
                else { paint.color = Color.WHITE; paint.textSize = 35f; paint.textAlign = Paint.Align.CENTER; canvas.drawText(treasure, 50f, 66f, paint); paint.textAlign = Paint.Align.LEFT }
            }
            DecorationKind.LAMP -> {
                oval(canvas, 0x20FFF3B9, 2f, 0f, 96f, 96f)
                box(canvas, 0xFF997D61.toInt(), 47f, 51f, 6f, 34f, 1f)
                oval(canvas, 0xFF997D61.toInt(), 32f, 81f, 36f, 7f)
                oval(canvas, color, 25f, 16f, 50f, 46f)
                oval(canvas, 0x55FFF8DD, 31f, 19f, 26f, 27f)
                if (item.id == "fireflies") for (i: Int in 0..4) oval(canvas, Color.WHITE, 33f + i * 6, 28f + i % 3 * 7, 3f, 3f)
                if (item.id == "moon_lamp") oval(canvas, 0xFFB5B59A.toInt(), 47f, 13f, 32f, 34f)
            }
        }
        canvas.restore()
    }

    private fun drawStar(canvas: Canvas, color: Int, x: Float, y: Float, radius: Float): Unit {
        path.reset()
        for (i: Int in 0..9) {
            val angle: Double = i * PI / 5 - PI / 2
            val length: Float = if (i % 2 == 0) radius else radius * .45f
            val px: Float = x + cos(angle).toFloat() * length
            val py: Float = y + sin(angle).toFloat() * length
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close(); paint.color = color; canvas.drawPath(path, paint)
    }
}

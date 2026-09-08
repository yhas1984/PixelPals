package com.pixelpals.app.feature.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.pixelpals.app.core.domain.PetType

/** One illustration language and a clear foreground for the pet and movable furniture. */
class PetHabitatPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val colors = HashMap<String, Int>()
    private var gradient: LinearGradient? = null
    private var gradientTop: Int = 0
    private var gradientBottom: Int = 0
    private fun color(hex: String): Int = colors.getOrPut(hex) { Color.parseColor(hex) }
    private fun oval(c: Canvas, hex: String, x: Float, y: Float, w: Float, h: Float) {
        paint.color = color(hex); c.drawOval(x, y, x + w, y + h, paint)
    }
    private fun line(c: Canvas, hex: String, width: Float, vararg points: Float) {
        paint.color = color(hex); paint.style = Paint.Style.STROKE
        paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND
        path.reset(); path.moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
        c.drawPath(path, paint); paint.style = Paint.Style.FILL
    }
    private fun hill(c: Canvas, hex: String, horizon: Float, rise: Float) {
        paint.color = color(hex); path.reset(); path.moveTo(0f, horizon)
        path.cubicTo(250f, horizon - rise, 650f, horizon + rise * .3f, 1000f, horizon - 30f)
        path.lineTo(1000f, 760f); path.lineTo(0f, 760f); path.close(); c.drawPath(path, paint)
    }
    private fun cloud(c: Canvas, hex: String, x: Float, y: Float, size: Float) {
        oval(c, hex, x, y, size, size * .3f)
        oval(c, hex, x + size * .13f, y - size * .14f, size * .38f, size * .4f)
        oval(c, hex, x + size * .42f, y - size * .23f, size * .39f, size * .48f)
    }
    private fun tree(c: Canvas, night: Boolean, x: Float, tall: Float, leaf: String) {
        val wood = if (night) "#655F58" else "#A88567"
        line(c, wood, 25f, x, 465f, x - 12f, tall)
        line(c, wood, 17f, x - 3f, 290f, x + 85f, 235f, x + 130f, 238f)
        line(c, wood, 14f, x - 8f, 215f, x - 78f, 168f)
        cloud(c, leaf, x - 130f, tall - 30f, 290f)
        oval(c, leaf, x + 40f, 195f, 160f, 80f)
    }
    private fun snow(c: Canvas, night: Boolean, ice: Boolean) {
        val white = if (night) "#C2D7DF" else "#F5F8F3"
        for (i in 0..3) {
            val x = i * 310f - 100f
            paint.color = color(if (night) "#7496A5" else "#B6D5DE")
            path.reset(); path.moveTo(x, 450f); path.lineTo(x + 160f, 150f + i % 2 * 55f)
            path.lineTo(x + 350f, 450f); path.close(); c.drawPath(path, paint)
            paint.color = color(white); path.reset(); path.moveTo(x + 112f, 240f + i % 2 * 45f)
            path.lineTo(x + 160f, 150f + i % 2 * 55f); path.lineTo(x + 219f, 245f + i % 2 * 42f)
            path.lineTo(x + 169f, 218f + i % 2 * 45f); path.lineTo(x + 151f, 245f + i % 2 * 42f)
            path.close(); c.drawPath(path, paint)
        }
        hill(c, if (night) "#A6BFCA" else "#DCECEF", 445f, 55f)
        hill(c, white, 505f, 35f)
        if (ice) oval(c, if (night) "#7FA5B5" else "#ACD3DD", 90f, 535f, 820f, 165f)
        else for (i in 0..17) oval(c, white, ((i * 173 + 43) % 980).toFloat(), 90f + (i % 5) * 60f, 6f, 6f)
    }
    private fun drawShelter(c: Canvas, environment: HomeEnvironment, night: Boolean) {
        if (environment != HomeEnvironment.COZY) return
        val wood = if (night) "#776A66" else "#B6997C"
        line(c, wood, 13f, 22f, 210f, 22f, 32f, 500f, 12f, 978f, 32f, 978f, 210f)
        for (x in listOf(70f, 930f)) {
            line(c, wood, 3f, x, 31f, x, 100f)
            oval(c, if (night) "#8C8977" else "#D5BE93", x - 22f, 95f, 44f, 56f)
            oval(c, if (night) "#F3DFA3" else "#FFF0C5", x - 15f, 104f, 30f, 34f)
        }
    }

    fun draw(c: Canvas, pet: PetType, environment: HomeEnvironment, hour: Int) {
        val night = environment == HomeEnvironment.NIGHT || hour < 7 || hour >= 20
        val frozen = pet == PetType.YUKI || pet == PetType.PIRU
        val skyPet = pet == PetType.ANGEL || pet == PetType.NUBE_MICHI
        val water = pet == PetType.BLOOP || pet == PetType.PATITO
        val sky = when {
            night -> "#283C4D"
            frozen -> "#BBDDE8"
            skyPet -> "#D7D7EA"
            pet == PetType.DIABLILLO -> "#E4C6B6"
            pet == PetType.JELLY -> "#E1D4DE"
            else -> "#CDE1D8"
        }
        val top = color(sky)
        val bottom = color(if (night) "#66767A" else "#FAEED9")
        if (gradient == null || gradientTop != top || gradientBottom != bottom) {
            gradientTop = top; gradientBottom = bottom
            gradient = LinearGradient(0f, 0f, 0f, 760f, top, bottom, Shader.TileMode.CLAMP)
        }
        paint.shader = gradient
        c.drawRect(0f, 0f, 1000f, 760f, paint); paint.shader = null
        oval(c, if (night) "#F2DFAC" else "#FFF2CC", 755f, 65f, 100f, 100f)
        cloud(c, if (night) "#657C89" else "#F6F2E8", 215f, 110f, 225f)
        if (frozen) { snow(c, night, pet == PetType.PIRU); drawShelter(c, environment, night); return }
        hill(c, if (night) "#496D68" else "#A3BEA1", 420f, 105f)
        hill(c, if (night) "#647D70" else "#C5D3AE", 495f, 50f)
        val leaf = if (night) "#42665D" else "#82A77D"
        when (pet) {
            PetType.GINGER, PetType.MOKI, PetType.LUMI, PetType.TELA -> {
                tree(c, night, 90f, 75f, leaf)
                tree(c, night, 888f, if (pet == PetType.GINGER) 55f else 155f, leaf)
                if (pet == PetType.GINGER) {
                    line(c, if (night) "#887962" else "#B99871", 13f, 125f, 265f, 375f, 305f, 525f, 230f)
                    cloud(c, leaf, 370f, 165f, 250f)
                }
                if (pet == PetType.MOKI) {
                    line(c, "#76976F", 7f, 110f, 75f, 290f, 280f, 430f, 110f)
                    for (i in 0..3) oval(c, "#E5BE72", 130f + i * 17f, 206f + i % 2 * 9f, 18f, 34f)
                }
                if (pet == PetType.TELA) {
                    for (i in 0..4) line(c, if (night) "#C0C9CE" else "#F7EEE0", 2f, 87f, 120f + i * 24, 255f - i * 24, 290f)
                    line(c, "#D6D6CB", 3f, 87f, 120f, 255f, 290f, 87f, 240f)
                }
            }
            PetType.MENTA -> for (i in 0..7) {
                val x = if (i < 4) 25f + i * 35f else 855f + (i - 4) * 35f
                line(c, leaf, 15f, x, 460f, x + 10f, 65f + i % 3 * 40f)
                for (j in 0..4) line(c, "#ABC495", 4f, x - 7f, 160f + j * 60f, x + 13f, 160f + j * 60f)
            }
            PetType.TARO -> {
                for (i in 0..4) oval(c, if (night) "#839580" else "#B8BE94", i * 235f - 60f, 380f + i % 2 * 35f, 130f, 65f)
            }
            PetType.DIABLILLO -> {
                hill(c, if (night) "#665967" else "#AB8F89", 415f, 80f)
                for (x in listOf(30f, 875f)) {
                    oval(c, "#8E7779", x, 300f, 110f, 160f)
                    line(c, "#E7AF78", 7f, x + 25f, 400f, x + 75f, 350f)
                }
            }
            else -> Unit
        }
        if (skyPet) {
            hill(c, if (night) "#A4ADB9" else "#E8E5ED", 475f, 35f)
            cloud(c, if (night) "#BAC3CD" else "#FAF4E9", -75f, 410f, 410f)
            cloud(c, if (night) "#BAC3CD" else "#FAF4E9", 730f, 430f, 350f)
        }
        if (water) {
            oval(c, if (night) "#527F8A" else "#98C5C8", 70f, 475f, 860f, 255f)
            for (i in 0..3) line(c, if (night) "#83ABB0" else "#DCECE0", 3f, 160f + i * 210f, 690f, 235f + i * 210f, 688f)
            if (pet == PetType.PATITO) for (i in 0..4) {
                line(c, leaf, 6f, 50f + i * 17f, 505f, 40f + i * 22f, 340f + i % 2 * 30f)
                oval(c, "#B79874", 34f + i * 22f, 310f + i % 2 * 30f, 14f, 46f)
            } else for (i in 0..7) {
                paint.color = color("#DAEEE7"); paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
                c.drawCircle(50f + i * 127f, 245f + i % 3 * 55f, 9f + i % 3 * 4f, paint); paint.style = Paint.Style.FILL
            }
        }
        if (pet == PetType.JELLY || pet == PetType.LUMI) for (x in listOf(30f, 890f)) {
            line(c, "#DFCCAF", 17f, x + 40f, 455f, x + 40f, 385f)
            oval(c, if (pet == PetType.JELLY) "#C99EAF" else "#C6BE8D", x, 350f, 100f, 55f)
            oval(c, "#FFF0D3", x + 22f, 360f, 12f, 10f)
        }
        if (pet == PetType.ANGEL || pet == PetType.LUMI || pet == PetType.JELLY) for (i in 0..11) {
            val x = ((i * 191 + 65) % 980).toFloat(); val y = 170f + i % 4 * 57f
            line(c, "#EFE2B6", 3f, x - 4f, y, x + 4f, y)
            line(c, "#EFE2B6", 3f, x, y - 4f, x, y + 4f)
        }
        drawShelter(c, environment, night)
    }
}

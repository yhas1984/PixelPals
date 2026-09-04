package com.pixelpals.app.feature.care

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.pixelpals.app.core.care.scene.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Native illustrations share the existing tray's soft outlines, not emoji/font glyphs. */
class SpeciesPropPainter {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path: Path = Path()
    private val impWings: ImpWingPainter = ImpWingPainter()
    private fun fill(color: Int): Paint = paint.apply { this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, width: Float = .055f): Paint = paint.apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND
    }
    private val mint: Int = Color.rgb(124, 184, 143)
    private val gold: Int = Color.rgb(245, 201, 110)
    private val rose: Int = Color.rgb(230, 143, 151)
    private val lilac: Int = Color.rgb(182, 161, 218)
    private val ice: Int = Color.rgb(191, 225, 238)
    private val cream: Int = Color.rgb(255, 245, 213)
    private val ink: Int = Color.rgb(95, 91, 121)

    /** Called in the painter's normalized [-1, 1] space. False retains a common tool. */
    fun draw(canvas: Canvas, profile: PetCareProfile, action: CareSceneAction, amount: Float): Boolean {
        when (action) {
            CareSceneAction.FEED -> {
                if (profile.food == CareFood.BOWL) return false
                canvas.save()
                val scale: Float = (.15f + .85f * amount.coerceIn(0f, 1f))
                canvas.scale(scale, scale)
                if (amount > 0f) food(canvas, profile.food)
                canvas.restore()
            }
            CareSceneAction.PLAY -> { if (profile.toy == CareToy.BALL) return false; toy(canvas, profile.toy) }
            CareSceneAction.REST -> { if (profile.bed == CareBed.CUSHION) return false; bed(canvas, profile.bed) }
            CareSceneAction.CLEAN -> { if (profile.wash == CareWashStyle.SPONGE) return false; wash(canvas, profile.wash) }
            CareSceneAction.PET, CareSceneAction.MEDICINE -> return false
        }
        return true
    }

    private fun food(canvas: Canvas, food: CareFood): Unit {
        when (food) {
            CareFood.MIST -> { cloud(canvas, ice); drop(canvas, 0f, -.6f, .3f, cream) }
            CareFood.DEWDROPS -> {
                drop(canvas, 0f, -.15f, .72f, Color.rgb(86, 169, 224))
                drop(canvas, -.59f, .54f, .34f, ice); drop(canvas, .59f, .54f, .34f, ice)
                canvas.drawOval(-.19f, -.48f, -.04f, -.13f, fill(cream))
            }
            CareFood.FRUIT -> {
                canvas.drawRoundRect(-.65f, -.5f, .65f, .65f, .2f, .2f, fill(rose))
                canvas.drawRoundRect(-.5f, -.4f, .3f, .1f, .15f, .15f, fill(cream))
                leaf(canvas, .15f, -.62f, .4f)
            }
            CareFood.FISH -> fish(canvas, rose)
            CareFood.STAR -> star(canvas, 0f, 0f, .8f, gold)
            CareFood.SEEDS -> repeat(6) { i ->
                canvas.drawOval(-.78f + i % 3 * .53f, -.48f + i / 3 * .5f, -.48f + i % 3 * .53f, -.02f + i / 3 * .5f, fill(if (i % 2 == 0) gold else cream))
            }
            CareFood.CHILI -> drawChili(canvas)
            CareFood.FLY -> fly(canvas)
            CareFood.SNOWFLAKE -> snowflake(canvas)
            CareFood.LITTLE_FISH -> { canvas.save(); canvas.scale(.8f, .8f); fish(canvas, ice); canvas.restore() }
            CareFood.LETTUCE -> { leaf(canvas, -.22f, .06f, .75f); leaf(canvas, .25f, -.2f, .6f) }
            CareFood.EGG -> {
                canvas.drawOval(-.52f, -.8f, .52f, .77f, fill(cream))
                canvas.drawOval(-.37f, -.52f, -.07f, .25f, fill(Color.WHITE))
                canvas.drawCircle(.2f, .4f, .07f, fill(gold))
            }
            CareFood.CRICKET -> drawCricket(canvas)
            CareFood.BERRIES -> drawBerries(canvas)
            CareFood.BOWL -> Unit
        }
    }

    private fun toy(canvas: Canvas, toy: CareToy): Unit {
        when (toy) {
            CareToy.BUBBLE -> {
                canvas.drawCircle(0f, 0f, .73f, stroke(ice, .09f))
                canvas.drawArc(RectF(-.52f, -.52f, .52f, .52f), 195f, 80f, false, stroke(cream, .12f))
            }
            CareToy.RAINBOW -> drawRainbow(canvas)
            CareToy.SPRING -> repeat(4) { i -> canvas.drawOval(-.6f, -.72f + i * .34f, .6f, -.2f + i * .34f, stroke(if (i % 2 == 0) rose else gold, .12f)) }
            CareToy.FEATHER -> {
                canvas.drawLine(.5f, .8f, -.4f, -.7f, stroke(gold, .09f))
                canvas.drawOval(-.7f, -.8f, .25f, .22f, fill(lilac))
                canvas.drawLine(-.35f, -.58f, .08f, .06f, stroke(cream))
            }
            CareToy.HALO -> canvas.drawOval(-.85f, -.48f, .85f, .48f, stroke(gold, .18f))
            CareToy.LEAF -> { canvas.drawOval(-.9f, .3f, .9f, .6f, stroke(ice)); leaf(canvas, 0f, -.15f, .65f) }
            CareToy.BALLOONS -> drawBalloonTridentIcon(canvas)
            CareToy.DANCING_LEAF -> {
                canvas.drawLine(-.6f, .85f, .25f, -.55f, stroke(Color.rgb(134, 99, 65), .08f))
                leaf(canvas, 0f, -.1f, .78f)
                canvas.drawLine(-.2f, -.02f, -.38f, -.42f, stroke(cream, .05f))
                canvas.drawLine(.18f, -.21f, .54f, .15f, stroke(cream, .05f))
            }
            CareToy.CRYSTAL -> {
                path.reset(); path.moveTo(0f, -.9f); path.lineTo(.66f, -.1f); path.lineTo(.3f, .8f)
                path.lineTo(-.45f, .68f); path.lineTo(-.66f, -.1f); path.close()
                canvas.drawPath(path, fill(ice)); canvas.drawLine(0f, -.7f, .12f, .64f, stroke(cream, .1f))
            }
            CareToy.PUCK -> {
                canvas.drawRoundRect(-.83f, -.22f, .83f, .45f, .25f, .25f, fill(lilac))
                canvas.drawOval(-.83f, -.5f, .83f, .12f, fill(ice)); star(canvas, 0f, -.2f, .25f, cream)
            }
            CareToy.PINWHEEL -> {
                canvas.drawLine(0f, 0f, 0f, .98f, stroke(mint, .1f))
                repeat(4) { i -> canvas.save(); canvas.rotate(i * 90f); canvas.drawOval(-.18f, -.8f, .34f, -.02f, fill(if (i % 2 == 0) rose else gold)); canvas.restore() }
                canvas.drawCircle(0f, 0f, .18f, fill(cream))
            }
            CareToy.HOOP -> { canvas.drawOval(-.65f, -.87f, .65f, .87f, stroke(gold, .16f)); leaf(canvas, .42f, -.56f, .3f) }
            CareToy.SILK -> {
                canvas.drawRoundRect(-.4f, -.7f, .4f, .7f, .15f, .15f, fill(cream))
                repeat(5) { i -> canvas.drawLine(-.4f, -.5f + i * .24f, .4f, -.36f + i * .24f, stroke(lilac, .08f)) }
                canvas.drawLine(.4f, .1f, .9f, .6f, stroke(cream, .08f))
            }
            CareToy.MAGIC_ORB -> {
                canvas.drawCircle(0f, 0f, .84f, fill(Color.rgb(218, 202, 247)))
                canvas.drawCircle(0f, 0f, .66f, fill(Color.rgb(133, 99, 209)))
                star(canvas, 0f, .04f, .45f, gold)
                canvas.drawArc(RectF(-.5f, -.52f, .5f, .5f), 200f, 70f, false, stroke(cream, .10f))
                star(canvas, .72f, -.66f, .21f, cream)
            }
            CareToy.BALL -> Unit
        }
    }

    private fun drawBalloonTridentIcon(canvas: Canvas): Unit {
        val balloonColors: List<Int> = listOf(rose, lilac, gold)
        val centers: List<CarePoint> = listOf(CarePoint(-.55f, -.30f), CarePoint(0f, -.58f), CarePoint(.52f, -.25f))
        centers.forEachIndexed { index: Int, center: CarePoint ->
            canvas.drawOval(center.x - .22f, center.y - .28f, center.x + .22f, center.y + .28f,
                fill(balloonColors[index]))
            canvas.drawLine(center.x, center.y + .27f, center.x * .35f, .62f, stroke(ink, .035f))
        }
        canvas.drawLine(-.62f, .85f, .64f, -.68f, stroke(Color.rgb(122, 61, 42), .10f))
        canvas.drawLine(.64f, -.68f, .82f, -.90f, stroke(gold, .08f))
        canvas.drawLine(.55f, -.73f, .62f, -.99f, stroke(gold, .08f))
        canvas.drawLine(.69f, -.60f, .94f, -.68f, stroke(gold, .08f))
    }

    private fun drawChili(canvas: Canvas): Unit {
        path.reset(); path.moveTo(-.5f, -.45f)
        path.cubicTo(.0f, -.95f, .85f, -.1f, .42f, .44f)
        path.quadTo(-.03f, .95f, -.87f, .70f)
        path.cubicTo(-.15f, .51f, -.09f, .04f, -.5f, -.45f); path.close()
        canvas.drawPath(path, fill(Color.rgb(221, 56, 55)))
        canvas.drawArc(RectF(-.35f, -.42f, .3f, .40f), -95f, 110f, false, stroke(Color.rgb(255, 171, 120), .10f))
        canvas.drawOval(-.62f, -.64f, -.15f, -.30f, fill(Color.rgb(71, 151, 81)))
        path.reset(); path.moveTo(-.39f, -.5f); path.quadTo(-.55f, -.94f, -.13f, -.91f)
        canvas.drawPath(path, stroke(Color.rgb(71, 151, 81), .12f))
    }

    private fun drawCricket(canvas: Canvas): Unit {
        val green: Int = Color.rgb(85, 145, 83)
        canvas.drawOval(-.66f, -.28f, .32f, .31f, fill(green))
        canvas.drawCircle(.39f, -.19f, .28f, fill(mint))
        canvas.drawCircle(.51f, -.26f, .055f, fill(ink))
        canvas.drawLine(.45f, -.41f, .75f, -.86f, stroke(green, .05f))
        canvas.drawLine(.28f, -.41f, .20f, -.85f, stroke(green, .05f))
        path.reset(); path.moveTo(-.28f, .1f); path.lineTo(-.66f, -.48f); path.lineTo(-.83f, .63f)
        canvas.drawPath(path, stroke(green, .10f))
        canvas.drawLine(.10f, .23f, .34f, .63f, stroke(green, .08f))
        canvas.drawLine(.4f, .08f, .77f, .5f, stroke(green, .07f))
        canvas.drawLine(-.5f, -.07f, -.04f, .17f, stroke(cream, .05f))
    }

    private fun drawBerries(canvas: Canvas): Unit {
        leaf(canvas, .1f, -.60f, .38f)
        val berry: Int = Color.rgb(89, 71, 169)
        canvas.drawCircle(-.39f, -.07f, .39f, fill(berry))
        canvas.drawCircle(.37f, -.06f, .4f, fill(berry))
        canvas.drawCircle(0f, .44f, .41f, fill(Color.rgb(114, 83, 186)))
        canvas.drawCircle(-.5f, -.22f, .09f, fill(ice))
        canvas.drawCircle(.25f, -.22f, .09f, fill(ice))
        canvas.drawCircle(-.12f, .28f, .09f, fill(ice))
    }

    private fun drawRainbow(canvas: Canvas): Unit {
        canvas.drawArc(RectF(-.88f, -.81f, .88f, .85f), 180f, 180f, false, stroke(rose, .17f))
        canvas.drawArc(RectF(-.66f, -.60f, .66f, .66f), 180f, 180f, false, stroke(gold, .15f))
        canvas.drawArc(RectF(-.46f, -.39f, .46f, .49f), 180f, 180f, false, stroke(ice, .14f))
        cloud(canvas, cream, -.65f, .20f, .36f); cloud(canvas, cream, .65f, .20f, .36f)
    }

    private fun bed(canvas: Canvas, bed: CareBed): Unit {
        when (bed) {
            CareBed.MOON_MIST, CareBed.CLOUD, CareBed.CLOUD_CRADLE -> {
                cloud(canvas, if (bed == CareBed.MOON_MIST) lilac else ice, scale = .95f)
                if (bed != CareBed.CLOUD) star(canvas, -.8f, -.4f, .21f, gold)
            }
            CareBed.PUDDLE -> { canvas.drawOval(-1f, -.15f, 1f, .38f, fill(ice)); canvas.drawOval(-.65f, -.08f, .42f, .08f, fill(cream)) }
            CareBed.BASKET, CareBed.NEST -> {
                canvas.drawArc(RectF(-1f, -.55f, 1f, .45f), 0f, 180f, true, fill(if (bed == CareBed.NEST) gold else lilac))
                repeat(6) { i -> canvas.drawLine(-.8f + i * .3f, -.06f, -.62f + i * .25f, .28f, stroke(cream)) }
                if (bed == CareBed.NEST) leaf(canvas, .7f, -.04f, .3f)
            }
            CareBed.WING_WRAP -> impWings.drawIcon(canvas)
            CareBed.WEB -> {
                canvas.drawLine(-.93f, -.4f, -.93f, .5f, stroke(gold, .08f))
                canvas.drawLine(.93f, -.4f, .93f, .5f, stroke(gold, .08f))
                path.reset(); path.moveTo(-.93f, -.4f); path.quadTo(0f, .67f, .93f, -.4f)
                canvas.drawPath(path, stroke(cream, .16f))
                repeat(5) { i -> canvas.drawLine(-.75f + i * .37f, -.33f, 0f, .13f, stroke(ice, .03f)) }
            }
            CareBed.BRANCH -> { canvas.drawLine(-1f, .13f, 1f, -.02f, stroke(gold, .15f)); leaf(canvas, -.65f, -.08f, .35f); leaf(canvas, .76f, -.18f, .35f) }
            CareBed.SNOW -> { cloud(canvas, cream, scale = .9f); canvas.drawOval(-.55f, .05f, .65f, .3f, fill(ice)) }
            CareBed.ICE -> {
                canvas.drawRoundRect(-1f, -.1f, 1f, .35f, .15f, .15f, fill(ice))
                canvas.drawOval(-1f, -.26f, 1f, .12f, fill(cream))
            }
            CareBed.MOSS -> repeat(5) { i -> canvas.drawOval(-1f + i * .38f, -.14f, -.28f + i * .33f, .3f, fill(if (i % 2 == 0) mint else gold)) }
            CareBed.WARM_LEAF -> { canvas.save(); canvas.scale(1f, .3f); leaf(canvas, 0f, 0f, 1f); canvas.restore() }
            CareBed.STARLIGHT -> { canvas.drawOval(-.9f, -.15f, .9f, .25f, stroke(lilac, .09f)); repeat(3) { i -> star(canvas, -.65f + i * .65f, .02f, .22f, gold) } }
            CareBed.CUSHION -> Unit
        }
    }

    private fun wash(canvas: Canvas, wash: CareWashStyle): Unit {
        when (wash) {
            CareWashStyle.BRUSH -> {
                canvas.drawRoundRect(-.15f, -.1f, .15f, .9f, .1f, .1f, fill(gold))
                canvas.drawRoundRect(-.65f, -.8f, .65f, .15f, .25f, .25f, fill(lilac))
                repeat(4) { i -> canvas.drawLine(-.4f + i * .26f, -.6f, -.4f + i * .26f, -.15f, stroke(cream, .08f)) }
            }
            CareWashStyle.MIST, CareWashStyle.SPLASH -> repeat(3) { i -> drop(canvas, -.6f + i * .6f, if (i == 1) -.38f else .28f, .35f, ice) }
            CareWashStyle.SPARKLES -> { star(canvas, -.38f, -.25f, .45f, gold); star(canvas, .42f, .35f, .37f, cream) }
            CareWashStyle.SNOW -> snowflake(canvas)
            CareWashStyle.SPONGE -> Unit
        }
    }

    private fun fish(canvas: Canvas, color: Int): Unit {
        canvas.drawOval(-.62f, -.4f, .68f, .4f, fill(color))
        path.reset(); path.moveTo(-.45f, 0f); path.lineTo(-.96f, -.45f); path.lineTo(-.96f, .45f); path.close()
        canvas.drawPath(path, fill(color)); canvas.drawCircle(.38f, -.08f, .065f, fill(ink))
        canvas.drawArc(RectF(-.16f, -.28f, .1f, .28f), -75f, 150f, false, stroke(cream))
    }

    private fun fly(canvas: Canvas): Unit {
        canvas.drawOval(-.72f, -.58f, .04f, -.02f, fill(ice)); canvas.drawOval(-.04f, -.58f, .72f, -.02f, fill(ice))
        canvas.drawOval(-.22f, -.3f, .22f, .53f, fill(ink)); canvas.drawCircle(0f, -.31f, .24f, fill(lilac))
    }

    private fun leaf(canvas: Canvas, x: Float, y: Float, size: Float): Unit {
        path.reset(); path.moveTo(x - size, y + size * .5f)
        path.cubicTo(x - size, y - size, x + size * .6f, y - size, x + size, y - size * .5f)
        path.cubicTo(x + size, y + size, x - size * .4f, y + size, x - size, y + size * .5f)
        canvas.drawPath(path, fill(mint)); canvas.drawLine(x - size * .75f, y + size * .35f, x + size * .7f, y - size * .35f, stroke(cream))
    }

    private fun cloud(canvas: Canvas, color: Int, x: Float = 0f, y: Float = 0f, scale: Float = 1f): Unit {
        canvas.drawOval(x - .95f * scale, y - .1f * scale, x + .95f * scale, y + .35f * scale, fill(color))
        canvas.drawOval(x - .72f * scale, y - .36f * scale, x + .13f * scale, y + .3f * scale, fill(color))
        canvas.drawOval(x - .12f * scale, y - .5f * scale, x + .6f * scale, y + .3f * scale, fill(color))
    }

    private fun drop(canvas: Canvas, x: Float, y: Float, size: Float, color: Int): Unit {
        path.reset(); path.moveTo(x, y - size)
        path.cubicTo(x + size * 1.3f, y + size * .55f, x - size * 1.3f, y + size * .55f, x, y - size)
        canvas.drawPath(path, fill(color))
    }

    private fun snowflake(canvas: Canvas): Unit {
        repeat(6) { i ->
            canvas.save(); canvas.rotate(i * 60f)
            canvas.drawLine(0f, 0f, 0f, -.84f, stroke(ice, .09f))
            canvas.drawLine(0f, -.53f, -.23f, -.7f, stroke(ice, .07f))
            canvas.drawLine(0f, -.53f, .23f, -.7f, stroke(ice, .07f)); canvas.restore()
        }
    }

    private fun star(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int): Unit {
        path.reset()
        repeat(10) { i ->
            val angle: Double = -PI / 2 + PI * i / 5
            val r: Float = radius * if (i % 2 == 0) 1f else .46f
            val px: Float = x + cos(angle).toFloat() * r
            val py: Float = y + sin(angle).toFloat() * r
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close(); canvas.drawPath(path, fill(color))
    }
}

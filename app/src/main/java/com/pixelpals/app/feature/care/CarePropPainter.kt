package com.pixelpals.app.feature.care

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.PetCareProfile
import com.pixelpals.app.core.domain.PetType

/** Small illustrated tools, drawn in a common [-1,1] coordinate space. */
class CarePropPainter {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val species: SpeciesPropPainter = SpeciesPropPainter()
    private var lastPet: PetType = PetType.CORGI
    private var profile: PetCareProfile = PetCareProfile.forPet(lastPet)
    private fun fill(color: String): Paint = paint.apply { this.color = Color.parseColor(color); style = Paint.Style.FILL }

    fun draw(canvas: Canvas, action: CareSceneAction, x: Float, y: Float, size: Float, amount: Float = 1f,
             pet: PetType = PetType.CORGI): Unit {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(size / 2f, size / 2f)
        if (pet != lastPet) { lastPet = pet; profile = PetCareProfile.forPet(pet) }
        if (pet != PetType.CORGI && species.draw(canvas, profile, action, amount)) {
            canvas.restore()
            return
        }
        when (action) {
            CareSceneAction.FEED -> drawFood(canvas, amount)
            CareSceneAction.PLAY -> drawBall(canvas)
            CareSceneAction.PET -> drawHand(canvas)
            CareSceneAction.CLEAN -> drawSponge(canvas)
            CareSceneAction.REST -> drawCushion(canvas)
            CareSceneAction.MEDICINE -> drawSpoon(canvas, amount)
        }
        canvas.restore()
    }

    private fun drawFood(canvas: Canvas, amount: Float): Unit {
        canvas.drawRoundRect(-.95f, .10f, .95f, .72f, .30f, .30f, fill("#B8B4ED"))
        canvas.drawOval(-.95f, -.05f, .95f, .30f, fill("#F1ECFF"))
        for (index: Int in 0 until (amount.coerceIn(0f, 1f) * 5f).toInt()) {
            val x: Float = -.60f + index * .29f
            canvas.drawOval(x - .20f, -.50f + (index % 2) * .15f, x + .20f, .12f, fill("#E9866C"))
            canvas.drawOval(x - .03f, -.55f, x + .18f, -.32f, fill("#5EAA83"))
            canvas.drawCircle(x + .07f, -.18f, .035f, fill("#FFF2CF"))
        }
        canvas.drawRoundRect(-.25f, .39f, .25f, .53f, .05f, .05f, fill("#FFF9F1"))
    }

    private fun drawBall(canvas: Canvas): Unit {
        canvas.drawCircle(0f, 0f, .78f, fill("#E78E79"))
        canvas.drawArc(RectF(-.78f, -.78f, .78f, .78f), -80f, 170f, true, fill("#F6D47E"))
        paint.apply { color = Color.parseColor("#FFF3D4"); style = Paint.Style.STROKE; strokeWidth = .09f }
        canvas.drawOval(-.42f, -.74f, .42f, .74f, paint)
        canvas.drawCircle(-.24f, -.36f, .13f, fill("#FFFFFF"))
    }

    private fun drawHand(canvas: Canvas): Unit {
        canvas.drawRoundRect(-.58f, -.08f, .52f, .76f, .3f, .3f, fill("#EBAE8B"))
        for (index: Int in 0..3) {
            val x: Float = -.55f + index * .28f
            canvas.drawRoundRect(x, -.75f + kotlin.math.abs(index - 1) * .10f, x + .24f, .20f, .13f, .13f, fill("#F5C7A7"))
        }
        canvas.drawRoundRect(-.84f, -.10f, -.40f, .50f, .20f, .20f, fill("#F5C7A7"))
        canvas.drawRoundRect(-.53f, .63f, .48f, .97f, .08f, .08f, fill("#A9B4DD"))
    }

    private fun drawSponge(canvas: Canvas): Unit {
        canvas.drawRoundRect(-.92f, -.48f, .92f, .65f, .28f, .28f, fill("#E6C572"))
        canvas.drawRoundRect(-.92f, -.62f, .92f, .37f, .28f, .28f, fill("#F6E2A0"))
        for (index: Int in 0..4) canvas.drawCircle(-.62f + index * .3f, if (index % 2 == 0) -.22f else .06f, .08f, fill("#DDC587"))
        canvas.drawCircle(-.53f, -.64f, .25f, fill("#CCEAE6"))
        canvas.drawCircle(-.13f, -.79f, .30f, fill("#E8F8F4"))
        canvas.drawCircle(.31f, -.68f, .24f, fill("#CCEAE6"))
    }

    private fun drawCushion(canvas: Canvas): Unit {
        canvas.save()
        canvas.scale(1f, .42f)
        canvas.drawRoundRect(-1f, -.43f, 1f, .58f, .32f, .32f, fill("#AAA0D3"))
        canvas.drawRoundRect(-.90f, -.48f, .90f, .40f, .32f, .32f, fill("#D4CBE9"))
        paint.apply { color = Color.parseColor("#F3EDFF"); style = Paint.Style.STROKE; strokeWidth = .035f }
        canvas.drawRoundRect(-.72f, -.33f, .72f, .23f, .20f, .20f, paint)
        canvas.drawCircle(0f, -.05f, .055f, fill("#A79ACB"))
        canvas.restore()
    }

    private fun drawSpoon(canvas: Canvas, amount: Float): Unit {
        canvas.save()
        canvas.rotate(-25f)
        canvas.drawRoundRect(-.12f, -.15f, .13f, 1f, .10f, .10f, fill("#88BAC5"))
        canvas.drawOval(-.42f, -.94f, .43f, .07f, fill("#D9E8E8"))
        if (amount > 0f) {
            canvas.save()
            canvas.scale(amount.coerceIn(0f, 1f), amount.coerceIn(0f, 1f), 0f, -.43f)
            canvas.drawOval(-.31f, -.78f, .32f, -.08f, fill("#E8BB77"))
            canvas.drawOval(-.23f, -.72f, -.08f, -.40f, fill("#FFF0C9"))
            canvas.restore()
        }
        canvas.restore()
    }
}

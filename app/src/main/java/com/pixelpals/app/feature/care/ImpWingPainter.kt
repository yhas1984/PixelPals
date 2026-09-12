package com.pixelpals.app.feature.care

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.pixelpals.app.core.care.scene.CarePoint

/** Attached, scalloped bat wings: spread behind the shoulders, then overlap the torso. */
class ImpWingPainter {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path: Path = Path()
    private val membrane: LinearGradient = LinearGradient(.35f, -.32f, -.05f, .32f,
        intArrayOf(Color.rgb(220, 112, 74), Color.rgb(157, 62, 45), Color.rgb(88, 34, 30)),
        null, Shader.TileMode.CLAMP)
    private val edge: Int = Color.rgb(91, 40, 30)
    private val rib: Int = Color.rgb(236, 146, 94)
    private var resting: Float = 0f

    fun draw(canvas: Canvas, center: CarePoint, size: Float, fold: Float, foreground: Boolean, rest: Float = 0f): Unit {
        resting = rest.coerceIn(0f, 1f)
        val amount: Float = fold.coerceIn(0f, 1f)
        val alpha: Int = if (foreground) (255 * amount).toInt() else 255
        if (alpha == 0) return
        canvas.save()
        canvas.translate(center.x, center.y)
        canvas.scale(size, size)
        drawWing(canvas, amount, alpha)
        canvas.scale(-1f, 1f)
        drawWing(canvas, amount, alpha)
        canvas.restore()
    }

    fun drawIcon(canvas: Canvas): Unit {
        draw(canvas, CarePoint(0f, .12f), 1.48f, 0f, false)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = edge
        canvas.drawOval(-.16f, -.02f, .16f, .6f, paint)
    }

    private fun drawWing(canvas: Canvas, fold: Float, alpha: Int): Unit {
        createMembrane(fold)
        paint.style = Paint.Style.FILL
        paint.shader = membrane
        paint.color = Color.WHITE
        paint.alpha = alpha
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.color = edge
        paint.alpha = alpha
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = .012f
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, paint)
        drawRibs(canvas, fold, alpha)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    private fun createMembrane(fold: Float): Unit {
        path.reset()
        path.moveTo(.15f, -.05f)
        path.cubicTo(settle(.23f, .20f), settle(mix(-.29f, -.10f, fold), -.01f),
            settle(mix(.43f, .04f, fold), .28f), settle(mix(-.39f, -.05f, fold), .16f),
            settle(mix(.60f, -.16f, fold), .30f), settle(mix(-.34f, .01f, fold), .28f))
        path.quadTo(settle(mix(.49f, -.12f, fold), .25f), settle(mix(-.10f, .12f, fold), .26f),
            settle(mix(.55f, -.16f, fold), .22f), settle(mix(.11f, .20f, fold), .23f))
        path.quadTo(settle(mix(.42f, -.05f, fold), .20f), settle(mix(.04f, .16f, fold), .23f),
            settle(mix(.35f, -.04f, fold), .18f), settle(mix(.23f, .27f, fold), .18f))
        path.quadTo(settle(mix(.28f, .05f, fold), .17f), settle(mix(.14f, .21f, fold), .17f),
            settle(mix(.20f, .12f, fold), .16f), settle(.29f, .13f))
        path.quadTo(mix(.19f, .24f, fold), .17f, mix(.13f, .23f, fold), .06f)
        path.close()
    }

    private fun drawRibs(canvas: Canvas, fold: Float, alpha: Int): Unit {
        paint.color = rib
        paint.alpha = (alpha * .65f).toInt()
        paint.strokeWidth = .009f
        path.reset()
        path.moveTo(.17f, -.04f)
        path.quadTo(settle(mix(.34f, .01f, fold), .24f), settle(mix(-.20f, .05f, fold), .12f),
            settle(mix(.55f, -.16f, fold), .22f), settle(mix(.11f, .20f, fold), .23f))
        path.moveTo(.17f, -.04f)
        path.quadTo(settle(mix(.27f, .10f, fold), .20f), settle(.04f, .10f),
            settle(mix(.35f, -.04f, fold), .18f), settle(mix(.23f, .27f, fold), .18f))
        path.moveTo(.17f, -.04f)
        path.quadTo(.16f, .13f, settle(mix(.20f, .12f, fold), .16f), settle(.29f, .13f))
        canvas.drawPath(path, paint)
    }

    private fun mix(open: Float, closed: Float, amount: Float): Float = open + (closed - open) * amount
    private fun settle(value: Float, target: Float): Float = mix(value, target, resting)
}

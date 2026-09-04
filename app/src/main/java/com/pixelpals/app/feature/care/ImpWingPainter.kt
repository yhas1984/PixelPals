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

    fun draw(canvas: Canvas, center: CarePoint, size: Float, fold: Float, foreground: Boolean): Unit {
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
        path.cubicTo(.23f, mix(-.29f, -.10f, fold), mix(.43f, .04f, fold), mix(-.39f, -.05f, fold),
            mix(.60f, -.16f, fold), mix(-.34f, .01f, fold))
        path.quadTo(mix(.49f, -.12f, fold), mix(-.10f, .12f, fold),
            mix(.55f, -.16f, fold), mix(.11f, .20f, fold))
        path.quadTo(mix(.42f, -.05f, fold), mix(.04f, .16f, fold),
            mix(.35f, -.04f, fold), mix(.23f, .27f, fold))
        path.quadTo(mix(.28f, .05f, fold), mix(.14f, .21f, fold), mix(.20f, .12f, fold), .29f)
        path.quadTo(mix(.19f, .24f, fold), .17f, mix(.13f, .23f, fold), .06f)
        path.close()
    }

    private fun drawRibs(canvas: Canvas, fold: Float, alpha: Int): Unit {
        paint.color = rib
        paint.alpha = (alpha * .65f).toInt()
        paint.strokeWidth = .009f
        path.reset()
        path.moveTo(.17f, -.04f)
        path.quadTo(mix(.34f, .01f, fold), mix(-.20f, .05f, fold),
            mix(.55f, -.16f, fold), mix(.11f, .20f, fold))
        path.moveTo(.17f, -.04f)
        path.quadTo(mix(.27f, .10f, fold), .04f, mix(.35f, -.04f, fold), mix(.23f, .27f, fold))
        path.moveTo(.17f, -.04f)
        path.quadTo(.16f, .13f, mix(.20f, .12f, fold), .29f)
        canvas.drawPath(path, paint)
    }

    private fun mix(open: Float, closed: Float, amount: Float): Float = open + (closed - open) * amount
}

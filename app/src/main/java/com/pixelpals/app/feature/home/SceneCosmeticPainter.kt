package com.pixelpals.app.feature.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.pixelpals.app.data.catalog.CosmeticEffect
import kotlin.math.*

class SceneCosmeticPainter {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    fun draw(canvas: Canvas, effect: CosmeticEffect?, actor: RectF, seconds: Float): Unit {
        val size: Float = actor.width() * .75f
        when (effect) {
            is CosmeticEffect.AuraEffect -> {
                paint.textSize = size * effect.sizeRatio; paint.alpha = 200
                val baseline: Float = -(paint.ascent() + paint.descent()) / 2
                for (index: Int in 0 until effect.count) {
                    val angle: Float = seconds * effect.speed + 2f * PI.toFloat() * index / effect.count
                    val radius: Float = size * effect.radiusRatio
                    canvas.drawText(effect.emoji, actor.centerX() + cos(angle) * radius,
                        actor.centerY() + sin(angle) * radius + baseline, paint)
                }
            }
            is CosmeticEffect.FloatEffect -> {
                paint.textSize = size * effect.sizeRatio; paint.alpha = 255
                val baseline: Float = -(paint.ascent() + paint.descent()) / 2
                val bob: Float = sin(seconds * effect.bobSpeed) * effect.bobAmplitude
                canvas.drawText(effect.emoji, actor.centerX() + size * effect.xRatio,
                    actor.centerY() + size * (effect.yRatio + bob) + baseline, paint)
            }
            else -> Unit
        }
    }
}

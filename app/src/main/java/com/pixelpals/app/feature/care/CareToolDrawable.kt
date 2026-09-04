package com.pixelpals.app.feature.care

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.domain.PetType

/** Tools in the tray use exactly the same illustrations as the scene. */
class CareToolDrawable(private val action: CareSceneAction, size: Int, private val pet: PetType = PetType.CORGI) : Drawable() {
    private val painter: CarePropPainter = CarePropPainter()
    init { setBounds(0, 0, size, size) }
    override fun draw(canvas: Canvas): Unit {
        painter.draw(canvas, action, bounds.exactCenterX(), bounds.exactCenterY(), minOf(bounds.width(), bounds.height()).toFloat() * .92f, pet = pet)
    }
    override fun setAlpha(alpha: Int): Unit = Unit
    override fun setColorFilter(colorFilter: ColorFilter?): Unit = Unit
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

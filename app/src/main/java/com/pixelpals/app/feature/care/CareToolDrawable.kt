package com.pixelpals.app.feature.care

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.feature.home.DecorationCatalog
import com.pixelpals.app.feature.home.DecorationKind

/** Tools in the tray use exactly the same illustrations as the scene. */
class CareToolDrawable(
    private val action: CareSceneAction,
    size: Int,
    private val pet: PetType = PetType.CORGI,
    bedDecorationId: String? = null,
    toyDecorationId: String? = null,
) : Drawable() {
    private val homeToy = action == CareSceneAction.PLAY &&
        ((pet == PetType.TARO && (toyDecorationId == null || toyDecorationId == "ball")) ||
            (toyDecorationId != "ball" && DecorationCatalog.find(toyDecorationId ?: "")?.kind == DecorationKind.TOY))
    private val homeBed = action == CareSceneAction.REST && bedDecorationId != "linen_bed" &&
        DecorationCatalog.find(bedDecorationId ?: "")?.kind == DecorationKind.BED
    private val painter: CarePropPainter = CarePropPainter().apply {
        this.bedDecorationId = bedDecorationId
        this.toyDecorationId = toyDecorationId
    }
    init { setBounds(0, 0, size, size) }
    override fun draw(canvas: Canvas): Unit {
        val size = minOf(bounds.width(), bounds.height()).toFloat()
        // Home props use a floor anchor and a larger source viewport than hand tools.
        val scale = if (homeToy) .58f else if (homeBed) .80f else .86f
        val offset = if (homeToy) .08f else if (homeBed) .23f else 0f
        painter.draw(canvas, action, bounds.exactCenterX(), bounds.exactCenterY() + size * offset,
            size * scale, pet = pet)
    }
    override fun setAlpha(alpha: Int): Unit = Unit
    override fun setColorFilter(colorFilter: ColorFilter?): Unit = Unit
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

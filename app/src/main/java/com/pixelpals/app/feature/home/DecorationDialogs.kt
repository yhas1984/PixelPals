package com.pixelpals.app.feature.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.pixelpals.app.R
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.domain.PetType

class DecorationPreview(context: Context, private val item: Decoration, private val pet: PetType = PetType.CORGI) : View(context) {
    private val painter: HomeScenePainter = HomeScenePainter()
    private val bounds: RectF = RectF()
    init { contentDescription = DecorationPresentation.title(context, item, pet); minimumHeight = HomeUi.dp(context, 150) }
    override fun onDraw(canvas: Canvas): Unit {
        super.onDraw(canvas)
        val size: Float = minOf(width.toFloat(), height.toFloat()) * .88f
        bounds.set((width - size) / 2, (height - size) / 2, (width + size) / 2, (height + size) / 2)
        painter.drawObject(canvas, item, bounds, pet = pet)
    }
}

object DecorationDialogs {
    fun show(context: Context, item: Decoration, world: CompanionWorld, pet: PetType, model: CompanionViewModel,
             onUse: ((CareSceneAction) -> Unit)? = null, onPlaceByTouch: (() -> Unit)? = null): Unit {
        val column = HomeUi.column(context)
        column.addView(DecorationPreview(context, item, pet), android.widget.LinearLayout.LayoutParams(-1, HomeUi.dp(context, 150)))
        val dialog: AlertDialog = AlertDialog.Builder(context).setTitle(DecorationPresentation.title(context, item, pet)).setView(column)
            .setNegativeButton(R.string.home_done, null).create()
        column.addView(HomeUi.button(context, context.getString(R.string.home_place), true) {
            dialog.dismiss(); if (onPlaceByTouch != null) onPlaceByTouch() else choosePosition(context, item, world, pet, model)
        })
        if (onPlaceByTouch != null) column.addView(HomeUi.button(context, context.getString(R.string.home_place_buttons)) {
            dialog.dismiss(); choosePosition(context, item, world, pet, model)
        })
        column.addView(HomeUi.button(context, context.getString(R.string.home_stored)) {
            model.perform { model.repository.store(pet, item.id) }; dialog.dismiss()
        })
        item.action?.let { action ->
            if (onUse != null) {
                column.addView(HomeUi.button(context, context.getString(R.string.home_use)) { dialog.dismiss(); onUse(action) })
            }

        }
        dialog.show()
    }

    private fun choosePosition(context: Context, item: Decoration, world: CompanionWorld, pet: PetType, model: CompanionViewModel): Unit {
        val positions: List<Pair<Int, Int>> = (0 until HomeGrid.ROWS).flatMap { row -> (0 until HomeGrid.COLUMNS).map { it to row } }
            .filter { (column, row) -> world.placements.none { it.decorationId != item.id && it.column == column && it.row == row } }
        if (positions.isEmpty()) { Toast.makeText(context, R.string.home_occupied, Toast.LENGTH_LONG).show(); return }
        AlertDialog.Builder(context).setTitle(R.string.home_place)
            .setItems(positions.map { context.getString(R.string.home_named_slot, context.resources.getStringArray(R.array.home_depth_names)[it.second], context.resources.getStringArray(R.array.home_side_names)[it.first]) }.toTypedArray()) { _, index ->
                val position: Pair<Int, Int> = positions[index]
                model.perform {
                    if (!model.repository.place(pet, item.id, position.first, position.second))
                        Toast.makeText(context, R.string.home_occupied, Toast.LENGTH_LONG).show()
                }
            }.show()
    }
}

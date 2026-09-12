package com.pixelpals.app.feature.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Shared warm surfaces for the room, adventures, and decoration shop. */
object HomeUi {
    val ink: Int = Color.rgb(51, 60, 53)
    val muted: Int = Color.rgb(101, 106, 94)
    val cream: Int = Color.rgb(250, 247, 239)
    val accent: Int = Color.rgb(67, 102, 83)
    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    fun surface(color: Int = Color.WHITE, radius: Float = 24f): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
    }
    fun column(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 24))
        setBackgroundColor(cream)
    }
    fun text(context: Context, value: CharSequence, size: Float = 16f): TextView = TextView(context).apply {
        text = value; textSize = size; setTextColor(if (size >= 20) ink else muted)
        typeface = Typeface.create(if (size >= 26) "serif" else "sans-serif", if (size >= 20) Typeface.BOLD else Typeface.NORMAL)
        setPadding(0, dp(context, 5), 0, dp(context, 7))
    }
    fun button(context: Context, value: CharSequence, primary: Boolean = false, action: () -> Unit): Button = Button(context).apply {
        text = value; isAllCaps = false; textSize = 14f
        setTextColor(if (primary) Color.WHITE else ink)
        background = surface(if (primary) accent else Color.rgb(236, 234, 222), dp(context, 16).toFloat())
        minHeight = dp(context, 48)
        setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 8); bottomMargin = dp(context, 4) }
        setOnClickListener { action() }
    }
    fun card(context: Context): LinearLayout = column(context).apply {
        background = surface(Color.WHITE, dp(context, 22).toFloat())
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 12) }
    }
    fun row(context: Context, vararg views: View): LinearLayout = object : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val visibleCount: Int = (0 until childCount).count { getChildAt(it).visibility != View.GONE }
            val available: Int = View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            val minimumCell: Float = dp(context, 144) * resources.configuration.fontScale.coerceAtLeast(1f)
            val visibleMargins: Int = (0 until childCount).sumOf { index: Int ->
                val child: View = getChildAt(index)
                if (child.visibility == View.GONE) 0
                else (child.layoutParams as LayoutParams).leftMargin + (child.layoutParams as LayoutParams).rightMargin
            }
            val nextOrientation: Int = if (View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.UNSPECIFIED) {
                VERTICAL
            } else if (available >= minimumCell * visibleCount + visibleMargins) HORIZONTAL else VERTICAL
            if (orientation != nextOrientation) {
                orientation = nextOrientation
                for (index: Int in 0 until childCount) {
                    val child: View = getChildAt(index)
                    child.layoutParams = (child.layoutParams as LayoutParams).apply {
                        width = if (nextOrientation == HORIZONTAL) 0 else LayoutParams.MATCH_PARENT
                        weight = if (nextOrientation == HORIZONTAL) 1f else 0f
                    }
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { view -> addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply {
            val margin: Int = dp(context, 3)
            setMargins(margin, margin, margin, margin)
        }) }
    }
}

package com.pixelpals.app.feature.home

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.database.CompanionHomeEntity

/** Optional first-adoption guidance; legacy users have no pending guide. */
internal class FirstHomeGuideView(context: Context, private val onCare: () -> Unit,
    private val onDesktop: () -> Unit) : LinearLayout(context) {
    private val preferences: CompanionPreferences = CompanionPreferences(context)
    private val message = HomeUi.text(context, "", 15f)
    private val action = HomeUi.button(context, "", true) {}
    init {
        id = R.id.firstHomeGuide
        orientation = VERTICAL
        setPadding(HomeUi.dp(context, 16), HomeUi.dp(context, 12), HomeUi.dp(context, 16), HomeUi.dp(context, 12))
        background = HomeUi.surface()
        visibility = GONE
        addView(message)
        action.id = R.id.firstHomeAction
        addView(action)
        addView(HomeUi.button(context, context.getString(R.string.home_guide_skip)) {
            preferences.finishFirstHome()
            visibility = GONE
        }.apply { id = R.id.firstHomeSkip })
    }
    fun render(home: CompanionHomeEntity?, travelling: Boolean) {
        if (home == null || home.petId != preferences.firstHomePet || travelling) {
            visibility = View.GONE
            return
        }
        visibility = View.VISIBLE
        val needsCare: Boolean = !preferences.hasCompletedFirstHomeCare
        val petName: String = home.nickname.ifBlank {
            context.getString((PetType.entries.firstOrNull { it.name.equals(home.petId, true) } ?: PetType.CORGI).displayNameResId)
        }
        message.text = context.getString(if (needsCare) R.string.home_guide_care else R.string.home_guide_desktop, petName)
        action.setText(if (needsCare) R.string.home_guide_pet else R.string.home_desktop)
        action.setOnClickListener { if (needsCare) onCare() else onDesktop() }
    }
}

package com.pixelpals.app.navigation

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.pixelpals.app.MainActivity
import com.pixelpals.app.PetSelectionActivity
import com.pixelpals.app.R
import com.pixelpals.app.feature.store.StoreActivity

enum class PixelPalsDestination(val menuId: Int) {
    HOME(R.id.nav_home),
    PETS(R.id.nav_pets),
    STORE(R.id.nav_store),
}

object RootNavigation {
    fun install(activity: Activity, current: PixelPalsDestination, navigation: BottomNavigationView): Unit {
        navigation.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
        navigation.selectedItemId = current.menuId
        navigation.setOnItemSelectedListener { item ->
            val destination = PixelPalsDestination.entries.firstOrNull { it.menuId == item.itemId }
                ?: return@setOnItemSelectedListener false
            if (destination == current) return@setOnItemSelectedListener true
            val target = when (destination) {
                PixelPalsDestination.HOME -> MainActivity::class.java
                PixelPalsDestination.PETS -> PetSelectionActivity::class.java
                PixelPalsDestination.STORE -> StoreActivity::class.java
            }
            activity.startActivity(Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            true
        }
    }
}

package com.pixelpals.app.navigation

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import com.pixelpals.app.HomeFragment
import com.pixelpals.app.PetsFragment
import com.pixelpals.app.R
import com.pixelpals.app.feature.store.StoreFragment

enum class PixelPalsDestination(@param:IdRes val menuId: Int, val fragmentTag: String) {
    HOME(R.id.nav_home, "root_home"),
    PETS(R.id.nav_pets, "root_pets"),
    STORE(R.id.nav_store, "root_store"),
}

enum class StoreSection(val pageIndex: Int) {
    PREMIUM(0),
    COSMETICS(1),
    COINS(2),
}

interface RootNavigator {
    fun navigate(destination: PixelPalsDestination, storeSection: StoreSection? = null)
}

object RootDestinationReducer {
    fun restore(savedDestination: String?, requestedDestination: String?): PixelPalsDestination =
        parse(savedDestination) ?: parse(requestedDestination) ?: PixelPalsDestination.HOME

    fun backTarget(currentDestination: PixelPalsDestination): PixelPalsDestination? =
        if (currentDestination == PixelPalsDestination.HOME) {
            null
        } else {
            PixelPalsDestination.HOME
        }

    fun parse(value: String?): PixelPalsDestination? =
        PixelPalsDestination.entries.firstOrNull { it.name == value }
}

class RootNavigationController(
    private val fragmentManager: FragmentManager,
    @param:IdRes private val containerId: Int,
) {
    fun show(
        destination: PixelPalsDestination,
        isAnimated: Boolean,
    ): Fragment {
        val targetFragment: Fragment = getOrCreateFragment(destination)
        val transaction = fragmentManager.beginTransaction()
        if (isAnimated) {
            transaction.setCustomAnimations(R.anim.root_fade_in, R.anim.root_fade_out)
        }
        if (!targetFragment.isAdded) {
            transaction.add(containerId, targetFragment, destination.fragmentTag)
        }
        PixelPalsDestination.entries.forEach { candidate ->
            val fragment: Fragment = if (candidate == destination) {
                targetFragment
            } else {
                fragmentManager.findFragmentByTag(candidate.fragmentTag) ?: return@forEach
            }
            if (candidate == destination) {
                transaction.show(fragment)
                transaction.setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
            } else {
                transaction.hide(fragment)
                transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
            }
        }
        // A notification or a restored task can deliver a navigation request after
        // onSaveInstanceState (for example while the device is locked).  Dropping
        // that transaction with commitNow() crashes the root activity.  Execute it
        // synchronously when the manager is active and explicitly allow state loss
        // only for this already-idempotent root selection when state is saved; the
        // activity's saved destination will still be restored on the next create.
        if (fragmentManager.isStateSaved) {
            transaction.commitNowAllowingStateLoss()
        } else {
            transaction.commitNow()
        }
        return targetFragment
    }

    private fun getOrCreateFragment(destination: PixelPalsDestination): Fragment {
        val restored: Fragment? = fragmentManager.findFragmentByTag(destination.fragmentTag)
        if (restored != null) return restored
        return when (destination) {
            PixelPalsDestination.HOME -> HomeFragment()
            PixelPalsDestination.PETS -> PetsFragment()
            PixelPalsDestination.STORE -> StoreFragment()
        }
    }
}

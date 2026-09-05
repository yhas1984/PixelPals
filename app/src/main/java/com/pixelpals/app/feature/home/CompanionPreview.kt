package com.pixelpals.app.feature.home

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.CosmeticEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

object CompanionPreview {
    fun create(fragment: Fragment, pet: PetType, effect: CosmeticEffect? = null): HomeSceneView {
        val scene: HomeSceneView = HomeSceneView(fragment.requireContext())
        applyEffect(scene, effect)
        scene.isClickable = false
        scene.minimumHeight = HomeUi.dp(fragment.requireContext(), 180)
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try { scene.loadPet(pet)
            } catch (exception: CancellationException) { throw exception
            } catch (_: Exception) { scene.contentDescription = fragment.getString(com.pixelpals.app.R.string.care_scene_assets_error) }
        }
        return scene
    }
    fun applyEffect(scene: HomeSceneView, effect: CosmeticEffect?): Unit {
        scene.cosmeticEffect = effect
    }
}

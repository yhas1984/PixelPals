package com.pixelpals.app.core.services

import android.content.Context
import com.pixelpals.app.BuildConfig
import com.pixelpals.app.core.analytics.NoOpAnalyticsTracker
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.core.analytics.LogcatAnalyticsTracker
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.feature.store.billing.BillingRepository
import com.pixelpals.app.feature.store.billing.DebugPreviewBillingRepository
import com.pixelpals.app.feature.store.billing.GooglePlayBillingRepository
import com.pixelpals.app.core.care.scene.CareSceneCoordinator
import com.pixelpals.app.core.care.scene.CareSceneResult
import com.pixelpals.app.notifications.PetCareNotificationManager
import com.pixelpals.app.notifications.PetCareNotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppServices {
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var careScenes: CareSceneCoordinator? = null

    fun careScenes(context: Context): CareSceneCoordinator = careScenes ?: synchronized(this) {
        val appContext: Context = context.applicationContext
        careScenes ?: CareSceneCoordinator(
            scope = applicationScope,
            readSnapshot = { pet -> repository(appContext).getStatusSnapshot(pet) },
            applyEffect = { request ->
                val result: CareSceneResult = repository(appContext).completeCareScene(request.pet, request.action)
                if (result is CareSceneResult.Completed) {
                    // Ancillary work cannot turn an already committed action into a retryable error.
                    runCatching {
                        PetCareNotificationManager.cancel(appContext)
                        PetCareNotificationScheduler.schedule(appContext)
                        analytics(appContext).track("care_scene_completed", mapOf(
                            "pet_id" to request.pet.name.lowercase(), "action" to request.action.name.lowercase(),
                            "origin" to request.origin.name.lowercase(), "mode" to request.mode.name.lowercase(),
                        ))
                    }
                }
                result
            },
        ).also { careScenes = it }
    }
    @Volatile
    private var repository: PixelPalsRepository? = null

    @Volatile
    private var analytics: AnalyticsTracker? = null

    @Volatile
    private var billingRepository: BillingRepository? = null

    fun repository(context: Context): PixelPalsRepository {
        return repository ?: synchronized(this) {
            repository ?: PixelPalsRepository(context.applicationContext).also { repository = it }
        }
    }

    fun analytics(context: Context): AnalyticsTracker {
        return analytics ?: synchronized(this) {
            analytics ?: (if (BuildConfig.DEBUG) LogcatAnalyticsTracker() else NoOpAnalyticsTracker()).also { analytics = it }
        }
    }

    fun billingRepository(context: Context): BillingRepository {
        return billingRepository ?: synchronized(this) {
            billingRepository ?: if (BuildConfig.DEBUG) {
                DebugPreviewBillingRepository(
                    repository = repository(context),
                    analytics = analytics(context)
                )
            } else {
                GooglePlayBillingRepository(
                    context = context.applicationContext,
                    repository = repository(context),
                    analytics = analytics(context)
                )
            }.also { billingRepository = it }
        }
    }
}

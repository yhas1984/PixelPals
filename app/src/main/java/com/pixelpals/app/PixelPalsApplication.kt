package com.pixelpals.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.Window
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.pixelpals.app.core.ads.AppOpenAdController

/** Application-level lifecycle bridge for App Open ads. */
class PixelPalsApplication : Application(), Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {

    private val appOpenAdController: AppOpenAdController by lazy {
        AppOpenAdController.getInstance(this)
    }

    override fun onCreate() {
        super<Application>.onCreate()
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        appOpenAdController.preloadIfPossible()
    }

    override fun onStart(owner: LifecycleOwner) {
        appOpenAdController.onAppForegrounded()
    }

    override fun onStop(owner: LifecycleOwner) {
        appOpenAdController.onAppBackgrounded()
    }

    override fun onActivityStarted(activity: Activity) {
        appOpenAdController.onActivityStarted(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        installInteractionCallback(activity)
        appOpenAdController.onActivityResumed(activity)
    }

    private fun installInteractionCallback(activity: Activity): Unit {
        if (!activity.javaClass.name.startsWith("com.pixelpals.app.")) return
        val callback: Window.Callback = activity.window.callback ?: return
        if (callback is InteractionWindowCallback) return
        activity.window.callback = InteractionWindowCallback(callback) {
            appOpenAdController.onUserInteraction()
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        appOpenAdController.onActivityDestroyed(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

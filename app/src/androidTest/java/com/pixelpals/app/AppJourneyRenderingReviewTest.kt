package com.pixelpals.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.pixelpals.app.feature.home.CompanionPreferences
import com.pixelpals.app.feature.home.AdventuresFragment
import com.pixelpals.app.feature.home.HomeSceneView
import com.pixelpals.app.feature.home.LivingHomeFragment
import com.pixelpals.app.feature.store.StoreFragment
import com.pixelpals.app.navigation.PixelPalsDestination
import androidx.recyclerview.widget.RecyclerView
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real root journeys captured from MainActivity; no repository fixtures are mutated. */
@RunWith(AndroidJUnit4::class)
class AppJourneyRenderingReviewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val destinations = PixelPalsDestination.entries
    private var originalLocales: LocaleListCompat? = null
    private var originalSeenIntroduction: Boolean? = null
    private var firstHomePet: String? = null
    private var firstHomeCare = false

    @After
    fun restoreUserSettings() {
        originalLocales?.let { locales -> instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(locales) } }
        originalSeenIntroduction?.let { CompanionPreferences(context).hasSeenIntroduction = it }
        firstHomePet?.let { pet ->
            CompanionPreferences(context).apply {
                beginFirstHome(pet)
                if (firstHomeCare) completeFirstHomeCare(pet)
            }
        }
        firstHomePet = null
        originalLocales = null
        originalSeenIntroduction = null
    }

    @Test
    fun captureAllRootDestinationsInSpanishAndEnglishAtAccessibleScales() {
        originalLocales = AppCompatDelegate.getApplicationLocales()
        val preferences = CompanionPreferences(context)
        originalSeenIntroduction = preferences.hasSeenIntroduction
        firstHomePet = preferences.firstHomePet
        firstHomeCare = preferences.hasCompletedFirstHomeCare
        preferences.hasSeenIntroduction = true
        preferences.finishFirstHome()
        val output = File(context.cacheDir, "app-journey-review").apply { mkdirs() }
        try {
            for (language in listOf("es", "en")) {
                // Keep an AppCompat host alive while changing locales. On API 35
                // a setter call made before any delegate host exists can be
                // accepted yet leave the next Activity in the old locale.
                ActivityScenario.launch<MainActivity>(MainActivity.createIntent(context, PixelPalsDestination.HOME)).use { bootstrap ->
                    instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language)) }
                    waitForLocale(bootstrap, language)
                }
                // The runner controls system font scale before launching this test.
                // Read it after the locale is applied so captions are captured at
                // the actual resource configuration used by MainActivity.
                val scale = context.resources.configuration.fontScale
                for (destination in destinations) {
                    val name = "${language}-${destination.name.lowercase()}-${scale.toString().replace('.', '_')}"
                    ActivityScenario.launch<MainActivity>(MainActivity.createIntent(context, destination)).use { scenario ->
                        waitForLoaded(scenario, destination, language)
                        scenario.onActivity { activity ->
                            val navigation = activity.findViewById<BottomNavigationView>(R.id.bottomNavigation)
                            assertTrue("${name}: bottom navigation must be visible", navigation.visibility == View.VISIBLE)
                            assertTrue("${name}: destination must be selected", navigation.selectedItemId == destination.menuId)
                            assertTrue("${name}: navigation exposes accessible labels",
                                (0 until navigation.menu.size()).all { !navigation.menu.getItem(it).title.isNullOrBlank() })
                            assertTrue("$name: activity locale must be $language",
                                activity.resources.configuration.locales[0].language == language)
                            val actualScale = activity.resources.configuration.fontScale
                            val decor = activity.window.decorView
                            assertTrue("${name}: decor must be laid out", decor.width > 0 && decor.height > 0)
                            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.TRANSPARENT)
                                decor.draw(Canvas(bitmap))
                                val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; textSize = 18f }
                                val actualName = "${language}-${destination.name.lowercase()}-${actualScale.toString().replace('.', '_')}"
                                Canvas(bitmap).drawText(actualName, 12f, 24f, label)
                                File(output, "$actualName.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            } finally { bitmap.recycle() }
                        }
                    }
                }
            }
        } finally {
            restoreUserSettings()
        }
    }

    private fun waitForLoaded(scenario: ActivityScenario<MainActivity>, destination: PixelPalsDestination, language: String) {
        val deadline = System.currentTimeMillis() + 20_000L
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag(destination.fragmentTag)
                ready = fragment != null && fragment.view != null && fragment.isResumed &&
                    ((activity.findViewById<View>(R.id.rootContent)?.width ?: 0) > 0) &&
                    activity.resources.configuration.locales[0].language == language &&
                    destinationReady(fragment!!)
            }
            if (ready) return
            instrumentation.waitForIdleSync()
            Thread.sleep(50L)
        }
        throw AssertionError("Timed out loading $destination")
    }

    private fun waitForLocale(scenario: ActivityScenario<MainActivity>, language: String) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            var applied = false
            scenario.onActivity { activity ->
                applied = activity.resources.configuration.locales[0].language == language
            }
            if (applied) return
            instrumentation.waitForIdleSync()
            Thread.sleep(50L)
        }
        throw AssertionError("Timed out applying locale $language")
    }

    private fun destinationReady(fragment: androidx.fragment.app.Fragment): Boolean = when (fragment) {
        is PetsFragment -> {
            val list = fragment.view?.findViewById<RecyclerView>(R.id.catalogList)
            val loading = fragment.view?.findViewById<View>(R.id.progressSelection)
            val state = (privateField(fragment, "viewModel") as? PetsViewModel)?.uiState?.value
            list != null && (list.adapter?.itemCount ?: 0) > 0 && loading?.visibility == View.GONE &&
                list.childCount > 0 && list.getChildAt(0).alpha >= .99f ||
                (state?.errorMessage != null && loading?.visibility == View.GONE)
        }
        is StoreFragment -> {
            val state = fragment.getStoreViewModel().uiState.value
            val loading = fragment.view?.findViewById<View>(R.id.progressStoreLoading)
            val pager = fragment.view?.findViewById<View>(R.id.storePager)
            val settled = !state.isInitialLoading && !state.isRefreshing && state.activeOperation == null
            (pager?.width ?: 0) > 0 && settled && storeTabReady(fragment) &&
                loading?.visibility == View.GONE
        }
        is LivingHomeFragment -> {
            val scene = privateField(fragment, "scene") as? HomeSceneView
            val locomotion = scene?.let { privateField(it, "locomotion") }
            scene != null && scene.visibility == View.VISIBLE && locomotion != null &&
                scene.renderedActorSize > 0f
        }
        is AdventuresFragment -> {
            val journal = privateField(fragment, "journalCard") as? ViewGroup
            journal != null && journal.childCount > 0
        }
        else -> {
            val root = fragment.view
            val group = root as? ViewGroup
            (group?.childCount ?: 0) > 0 && (root?.width ?: 0) > 0
        }
    }

    private fun privateField(receiver: Any, name: String): Any? {
        var type: Class<*>? = receiver.javaClass
        while (type != null) {
            runCatching {
                return type.getDeclaredField(name).apply { isAccessible = true }.get(receiver)
            }
            type = type.superclass
        }
        return null
    }

    private fun storeTabReady(store: StoreFragment): Boolean {
        val active = store.childFragmentManager.fragments.firstOrNull { it.isResumed && it.view != null }
            ?: return false
        val root = active.view ?: return false
        val list = findDescendant(root) { it is RecyclerView } as? RecyclerView
        val listReady = list != null && (list.adapter?.itemCount ?: 0) > 0 && list.childCount > 0 &&
            list.getChildAt(0).alpha >= .99f
        val empty = root.findViewById<View>(R.id.storeEmptyState)
        val emptyReady = empty?.visibility == View.VISIBLE &&
            findDescendant(empty) { it is android.widget.TextView && (it as android.widget.TextView).text.isNotBlank() } != null
        val errorReady = findDescendant(root) {
            it is android.widget.TextView && it.visibility == View.VISIBLE &&
                (it as android.widget.TextView).text.isNotBlank() &&
                it.id != R.id.storeEmptyMessage
        } != null && store.getStoreViewModel().uiState.value.notice != null
        return listReady || emptyReady || errorReady
    }

    private fun findDescendant(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findDescendant(root.getChildAt(index), predicate)?.let { return it }
            }
        }
        return null
    }
}

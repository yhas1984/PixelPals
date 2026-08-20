package com.pixelpals.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.pixelpals.app.feature.store.StoreFragment
import com.pixelpals.app.navigation.PixelPalsDestination
import com.pixelpals.app.navigation.StoreSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class RootNavigationFlowTest {
    private val application: Application = InstrumentationRegistry.getInstrumentation()
        .targetContext.applicationContext as Application
    private val mainActivityCreations = AtomicInteger(0)
    private var scenario: ActivityScenario<MainActivity>? = null
    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is MainActivity) mainActivityCreations.incrementAndGet()
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    @Before
    fun setUp() {
        application.registerActivityLifecycleCallbacks(callbacks)
    }

    @After
    fun tearDown() {
        scenario?.close()
        application.unregisterActivityLifecycleCallbacks(callbacks)
    }

    @Test
    fun bottomDestinationsReuseOneActivityAndOneFragmentInstance() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity ->
            val activityIdentity: MainActivity = activity
            val home: Fragment = getFragment(activity, PixelPalsDestination.HOME)
            activity.navigate(PixelPalsDestination.PETS)
            val pets: Fragment = getFragment(activity, PixelPalsDestination.PETS)
            activity.navigate(PixelPalsDestination.STORE)
            val store: Fragment = getFragment(activity, PixelPalsDestination.STORE)
            activity.navigate(PixelPalsDestination.HOME)
            assertSame(activityIdentity, activity)
            assertSame(home, getFragment(activity, PixelPalsDestination.HOME))
            activity.navigate(PixelPalsDestination.PETS)
            assertSame(pets, getFragment(activity, PixelPalsDestination.PETS))
            activity.navigate(PixelPalsDestination.STORE)
            assertSame(store, getFragment(activity, PixelPalsDestination.STORE))
        }
        assertEquals(1, mainActivityCreations.get())
    }

    @Test
    fun backFromStoreReturnsHomeWithoutFinishingRootActivity() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity ->
            activity.navigate(PixelPalsDestination.STORE)
            activity.onBackPressedDispatcher.onBackPressed()
            val home: Fragment = getFragment(activity, PixelPalsDestination.HOME)
            assertTrue(home.lifecycle.currentState == Lifecycle.State.RESUMED)
            val navigation: BottomNavigationView = activity.findViewById(R.id.bottomNavigation)
            assertEquals(R.id.nav_home, navigation.selectedItemId)
        }
        assertEquals(1, mainActivityCreations.get())
    }

    @Test
    fun bottomNavigationExposesOneVisibleLabelPerDestination() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity ->
            val navigation: BottomNavigationView = activity.findViewById(R.id.bottomNavigation)
            listOf(R.string.nav_home, R.string.nav_pets, R.string.nav_store).forEach { resource ->
                val label: String = activity.getString(resource)
                assertEquals(1, countVisibleLabels(navigation, label))
            }
        }
    }

    @Test
    fun petsCatalogKeepsItsScrollWhenSwitchingRootDestinations() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { it.navigate(PixelPalsDestination.PETS) }
        waitUntil { activity ->
            (activity.findViewById<RecyclerView>(R.id.catalogList)?.adapter?.itemCount ?: 0) > 6
        }
        var expectedPosition: Int = RecyclerView.NO_POSITION
        scenario!!.onActivity { activity ->
            val list: RecyclerView = activity.findViewById(R.id.catalogList)
            val target: Int = (list.adapter?.itemCount ?: 1) - 1
            list.scrollToPosition(target)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario!!.onActivity { activity ->
            val list: RecyclerView = activity.findViewById(R.id.catalogList)
            expectedPosition = (list.layoutManager as LinearLayoutManager)
                .findFirstVisibleItemPosition()
            assertTrue(expectedPosition > 0)
            activity.navigate(PixelPalsDestination.STORE)
            activity.navigate(PixelPalsDestination.PETS)
            val restoredPosition: Int = (list.layoutManager as LinearLayoutManager)
                .findFirstVisibleItemPosition()
            assertEquals(expectedPosition, restoredPosition)
        }
    }

    @Test
    fun recreationRestoresStoreDestinationAndInternalSection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch(
            MainActivity.createIntent(context, PixelPalsDestination.STORE, StoreSection.COINS),
        )
        waitUntil { activity ->
            val store: StoreFragment? = activity.supportFragmentManager
                .findFragmentByTag(PixelPalsDestination.STORE.fragmentTag) as? StoreFragment
            store?.view?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.storePager)
                ?.currentItem == StoreSection.COINS.pageIndex
        }
        scenario!!.recreate()
        waitUntil { activity ->
            val store: StoreFragment? = activity.supportFragmentManager
                .findFragmentByTag(PixelPalsDestination.STORE.fragmentTag) as? StoreFragment
            store?.lifecycle?.currentState == Lifecycle.State.RESUMED &&
                store.view?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.storePager)
                    ?.currentItem == StoreSection.COINS.pageIndex
        }
        scenario!!.onActivity { activity ->
            val navigation: BottomNavigationView = activity.findViewById(R.id.bottomNavigation)
            assertEquals(R.id.nav_store, navigation.selectedItemId)
        }
        assertEquals(2, mainActivityCreations.get())
    }

    @Test
    fun storeBannerUsesLayoutSpaceOnlyWhileVisibleAndNeverOverlapsNavigation() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity ->
            val storeRoot: ViewGroup = LayoutInflater.from(activity)
                .inflate(R.layout.activity_store, null, false) as ViewGroup
            val banner: FrameLayout = storeRoot.findViewById(R.id.bannerAdContainer)
            val pager: View = storeRoot.findViewById(R.id.storePager)
            val widthSpec: Int = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val heightSpec: Int = View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY)

            measureAndLayout(storeRoot, widthSpec, heightSpec)
            val hiddenPagerHeight: Int = pager.height
            assertEquals(View.GONE, banner.visibility)

            banner.removeAllViews()
            banner.addView(
                View(activity),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    (50 * activity.resources.displayMetrics.density).toInt(),
                ),
            )
            banner.visibility = View.VISIBLE
            measureAndLayout(storeRoot, widthSpec, heightSpec)
            assertTrue(banner.height > 0)
            assertTrue(pager.height < hiddenPagerHeight)
            assertTrue(pager.bottom <= banner.top)

            banner.visibility = View.GONE
            measureAndLayout(storeRoot, widthSpec, heightSpec)
            assertEquals(hiddenPagerHeight, pager.height)

            val rootContent: View = activity.findViewById(R.id.rootContent)
            val navigation: View = activity.findViewById(R.id.bottomNavigation)
            val contentLocation = IntArray(2)
            val navigationLocation = IntArray(2)
            rootContent.getLocationOnScreen(contentLocation)
            navigation.getLocationOnScreen(navigationLocation)
            assertTrue(contentLocation[1] + rootContent.height <= navigationLocation[1])
        }
    }

    private fun getFragment(activity: MainActivity, destination: PixelPalsDestination): Fragment =
        requireNotNull(activity.supportFragmentManager.findFragmentByTag(destination.fragmentTag))

    private fun countVisibleLabels(view: View, label: String): Int {
        var count: Int = if (
            view is TextView && view.visibility == View.VISIBLE && view.text.toString() == label
        ) {
            1
        } else {
            0
        }
        if (view is ViewGroup) {
            for (index: Int in 0 until view.childCount) {
                count += countVisibleLabels(view.getChildAt(index), label)
            }
        }
        return count
    }

    private fun waitUntil(
        timeoutMs: Long = 5_000L,
        condition: (MainActivity) -> Boolean,
    ) {
        val deadline: Long = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            var matched: Boolean = false
            scenario!!.onActivity { activity -> matched = condition(activity) }
            if (matched) return
            Thread.sleep(50L)
        }
        throw AssertionError("Condition was not met within $timeoutMs ms")
    }

    private fun measureAndLayout(view: View, widthSpec: Int, heightSpec: Int) {
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
}

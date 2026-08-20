package com.pixelpals.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.pixelpals.app.core.ads.AppOpenAdController
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.databinding.ActivityRootBinding
import com.pixelpals.app.feature.store.StoreFragment
import com.pixelpals.app.navigation.PixelPalsDestination
import com.pixelpals.app.navigation.RootDestinationReducer
import com.pixelpals.app.navigation.RootNavigationController
import com.pixelpals.app.navigation.RootNavigator
import com.pixelpals.app.navigation.StoreSection

class MainActivity : AppCompatActivity(), RootNavigator {
    companion object {
        private const val EXTRA_DESTINATION: String = "root_destination"
        private const val EXTRA_STORE_SECTION: String = "root_store_section"
        private const val STATE_DESTINATION: String = "state_root_destination"
        private const val STATE_STORE_SECTION: String = "state_store_section"

        fun createIntent(
            context: Context,
            destination: PixelPalsDestination,
            storeSection: StoreSection? = null,
        ): Intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_DESTINATION, destination.name)
            storeSection?.let { putExtra(EXTRA_STORE_SECTION, it.name) }
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }

    private lateinit var binding: ActivityRootBinding
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var navigationController: RootNavigationController
    private var currentDestination: PixelPalsDestination? = null
    private val appOpenAdController: AppOpenAdController by lazy {
        AppOpenAdController.getInstance(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        setTitle(R.string.app_name)
        configureEdgeToEdge()
        binding = ActivityRootBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        clearPetAfterTaskManagerStop()
        applySystemBarInsets()
        navigationController = RootNavigationController(supportFragmentManager, R.id.rootContent)
        configureBottomNavigation()
        configureBackNavigation()
        val requestedDestination: PixelPalsDestination = RootDestinationReducer.restore(
            savedInstanceState?.getString(STATE_DESTINATION),
            intent.getStringExtra(EXTRA_DESTINATION),
        )
        val requestedSection: StoreSection? = savedInstanceState
            ?.getString(STATE_STORE_SECTION)
            ?.let(::parseStoreSection)
            ?: parseStoreSection(intent)
        showDestination(requestedDestination, requestedSection, false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val destination: PixelPalsDestination = RootDestinationReducer.parse(
            intent.getStringExtra(EXTRA_DESTINATION),
        ) ?: PixelPalsDestination.HOME
        showDestination(destination, parseStoreSection(intent), currentDestination != destination)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        currentDestination?.let { outState.putString(STATE_DESTINATION, it.name) }
        if (currentDestination == PixelPalsDestination.STORE) {
            val storeFragment: StoreFragment? = supportFragmentManager
                .findFragmentByTag(PixelPalsDestination.STORE.fragmentTag) as? StoreFragment
            storeFragment?.getCurrentSection()?.let { section ->
                outState.putString(STATE_STORE_SECTION, section.name)
            }
        }
        super.onSaveInstanceState(outState)
    }

    override fun onPostResume() {
        super.onPostResume()
        appOpenAdController.start(this)
        appOpenAdController.onActivityResumed(this)
    }

    override fun onPause() {
        appOpenAdController.onActivityPaused(this)
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        appOpenAdController.onUserInteraction()
    }

    override fun navigate(destination: PixelPalsDestination, storeSection: StoreSection?) {
        showDestination(destination, storeSection, currentDestination != destination)
    }

    private fun showDestination(
        destination: PixelPalsDestination,
        storeSection: StoreSection?,
        isAnimated: Boolean,
    ) {
        if (currentDestination == destination && storeSection == null) return
        val target: Fragment = navigationController.show(
            destination = destination,
            isAnimated = isAnimated,
        )
        currentDestination = destination
        bottomNavigation.menu.findItem(destination.menuId).isChecked = true
        if (target is StoreFragment && storeSection != null) {
            target.selectSection(storeSection)
        }
    }

    private fun configureBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            val destination: PixelPalsDestination = PixelPalsDestination.entries
                .firstOrNull { it.menuId == item.itemId }
                ?: return@setOnItemSelectedListener false
            if (destination != currentDestination) navigate(destination)
            true
        }
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val target: PixelPalsDestination? = currentDestination
                    ?.let(RootDestinationReducer::backTarget)
                if (target != null) {
                    navigate(target)
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    private fun parseStoreSection(intent: Intent): StoreSection? {
        return parseStoreSection(intent.getStringExtra(EXTRA_STORE_SECTION))
    }

    private fun parseStoreSection(value: String?): StoreSection? {
        return StoreSection.entries.firstOrNull { it.name == value }
    }

    private fun clearPetAfterTaskManagerStop() {
        val store = SelectedPetStore(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !store.isPetEnabled()) return
        val enabledAt: Long = store.getPetEnabledAt() ?: return
        val manager: ActivityManager = getSystemService(ActivityManager::class.java) ?: return
        val lastExit = manager.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull()
        if (lastExit?.reason == ApplicationExitInfo.REASON_USER_REQUESTED && lastExit.timestamp >= enabledAt) {
            store.setPetEnabled(false)
        }
    }

    private fun configureEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.surface_base)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.surface_base)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }
}

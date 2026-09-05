package com.pixelpals.app

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.FragmentContainerView
import com.pixelpals.app.feature.home.CompanionPreferences
import com.pixelpals.app.feature.home.HomeUi
import com.pixelpals.app.navigation.*

class CompanionSettingsActivity : AppCompatActivity(), RootNavigator {
    override fun onCreate(savedInstanceState: Bundle?): Unit {
        super.onCreate(savedInstanceState)
        val preferences: CompanionPreferences = CompanionPreferences(this)
        val root: LinearLayout = HomeUi.column(this)
        root.addView(HomeUi.button(this, getString(R.string.home_close)) { finish() })
        fun toggle(title: Int, value: Boolean, changed: (Boolean) -> Unit): Unit {
            root.addView(SwitchCompat(this).apply {
                setText(title); isChecked = value; minHeight = HomeUi.dp(context, 48)
                setTextColor(HomeUi.ink)
                setOnCheckedChangeListener { _, checked -> changed(checked) }
            })
        }
        toggle(R.string.home_sound, preferences.sound) { preferences.sound = it }
        toggle(R.string.home_haptics, preferences.haptics) { preferences.haptics = it }
        toggle(R.string.home_reduced_motion, preferences.reducedMotion) { preferences.reducedMotion = it }
        root.addView(FragmentContainerView(this).apply { id = R.id.companionSettings }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(HomeUi.dp(this, 16) + bars.left, bars.top, HomeUi.dp(this, 16) + bars.right, bars.bottom)
            androidx.core.view.WindowInsetsCompat.Builder(insets).setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(), androidx.core.graphics.Insets.NONE).build()
        }
        if (savedInstanceState == null) supportFragmentManager.beginTransaction().replace(R.id.companionSettings, SettingsFragment()).commit()
    }
    override fun navigate(destination: PixelPalsDestination, storeSection: StoreSection?): Unit {
        startActivity(MainActivity.createIntent(this, destination, storeSection)); finish()
    }
}

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
        root.addView(HomeUi.button(this, getString(R.string.rest_settings)) { showRestSettings(preferences) })
        root.addView(FragmentContainerView(this).apply { id = R.id.companionSettings }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(HomeUi.dp(this, 16) + bars.left, bars.top, HomeUi.dp(this, 16) + bars.right, bars.bottom)
            androidx.core.view.WindowInsetsCompat.Builder(insets).setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(), androidx.core.graphics.Insets.NONE).build()
        }
        if (savedInstanceState == null) supportFragmentManager.beginTransaction().replace(R.id.companionSettings, SettingsFragment()).commit()
    }
    private fun showRestSettings(preferences: CompanionPreferences) {
        val content = HomeUi.column(this)
        content.addView(android.widget.TextView(this).apply {
            setText(R.string.rest_description)
            setTextColor(HomeUi.ink)
        })
        fun toggle(label: Int, initial: Boolean, change: (Boolean) -> Unit) {
            content.addView(SwitchCompat(this).apply {
                setText(label); isChecked = initial; minHeight = HomeUi.dp(context, 48)
                setTextColor(HomeUi.ink)
                setOnCheckedChangeListener { _, value -> change(value) }
            })
        }
        toggle(R.string.rest_follow_dnd, preferences.restSchedule.followDoNotDisturb) {
            preferences.restSchedule = preferences.restSchedule.copy(followDoNotDisturb = it)
        }
        toggle(R.string.rest_schedule_enabled, preferences.restSchedule.enabled) {
            preferences.restSchedule = preferences.restSchedule.copy(enabled = it)
        }
        fun timeButton(label: Int, start: Boolean) {
            val button = HomeUi.button(this, "") {}
            fun refresh() {
                val minute = if (start) preferences.restSchedule.startMinute else preferences.restSchedule.endMinute
                val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(
                    java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, minute / 60); set(java.util.Calendar.MINUTE, minute % 60) }.time)
                button.text = getString(label, time)
            }
            refresh()
            button.setOnClickListener {
                val minute = if (start) preferences.restSchedule.startMinute else preferences.restSchedule.endMinute
                android.app.TimePickerDialog(this, { _, hour, minutes ->
                    val value = hour * 60 + minutes
                    preferences.restSchedule = if (start) preferences.restSchedule.copy(startMinute = value)
                        else preferences.restSchedule.copy(endMinute = value)
                    refresh()
                }, minute / 60, minute % 60, android.text.format.DateFormat.is24HourFormat(this)).show()
            }
            content.addView(button)
        }
        timeButton(R.string.rest_start, true)
        timeButton(R.string.rest_end, false)
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.rest_settings)
            .setView(android.widget.ScrollView(this).apply { addView(content) })
            .setPositiveButton(R.string.home_close, null).show()
    }

    override fun navigate(destination: PixelPalsDestination, storeSection: StoreSection?): Unit {
        startActivity(MainActivity.createIntent(this, destination, storeSection)); finish()
    }
}

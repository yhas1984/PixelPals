package com.pixelpals.app.feature.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.core.thermal.YukiThermalMemory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YukiThermalMemoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefsName: String = "yuki-thermal-test-${System.nanoTime()}"
    private lateinit var prefs: android.content.SharedPreferences

    @Before fun prepare(): Unit {
        prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After fun cleanup(): Unit {
        prefs.edit().clear().commit()
        context.deleteSharedPreferences(prefsName)
    }

    @Test fun memorySurvivesInstancesAndKeepsDefaultPreferencesUntouched(): Unit {
        val defaults = context.getSharedPreferences("pixelpals", Context.MODE_PRIVATE)
            .all.toMap()
        val first = YukiThermalMemory(prefs)
        val second = YukiThermalMemory(prefs)

        assertTrue(first.update(42f))
        assertTrue(second.update(39f))
        assertFalse(second.update(38f))
        assertFalse(first.update(39f))
        assertTrue(first.update(40f))

        val restored = YukiThermalMemory(prefs)
        assertTrue(restored.update(null))
        assertTrue(restored.update(Float.NaN))
        assertEquals(defaults, defaultsFor(context))
    }

    private fun defaultsFor(context: Context): Map<String, *> =
        context.getSharedPreferences("pixelpals", Context.MODE_PRIVATE).all.toMap()
}

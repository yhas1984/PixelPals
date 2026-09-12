package com.pixelpals.app.core.rest

import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceRestSystemTest {
    @Test fun followsSystemDoNotDisturbWithoutChangingIt(): Unit {
        assumeTrue("Requires disposable generic/ranchu emulator", isDisposableEmulator())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val expectedArgument: String? = InstrumentationRegistry.getArguments().getString("expectedDnd")
        assumeTrue("Run this scenario with -e expectedDnd true or false", expectedArgument != null)
        val expectedDnd: Boolean = when (expectedArgument!!.lowercase()) {
            "true" -> true
            "false" -> false
            else -> {
                assumeTrue("expectedDnd must be true or false", false)
                false
            }
        }
        val context = instrumentation.targetContext
        val dndSchedule = PetRestSchedule(enabled = false, followDoNotDisturb = true)
        val observed: Boolean = awaitWithinThreeSeconds(expectedDnd) {
            DeviceRestState(context).shouldRest(dndSchedule, LocalTime.NOON)
        }
        if (expectedDnd) assertTrue("DND should make a disabled schedule rest", observed)
        else assertFalse("Without DND a disabled schedule must remain awake", observed)

        val explicitSchedule = PetRestSchedule(
            enabled = true,
            followDoNotDisturb = false,
            startMinute = 11 * 60,
            endMinute = 13 * 60,
        )
        assertTrue(
            "An explicit 11:00-13:00 schedule must apply regardless of DND",
            DeviceRestState(context).shouldRest(explicitSchedule, LocalTime.NOON),
        )
        assertFalse(
            "followDoNotDisturb=false must not make an empty schedule rest",
            DeviceRestState(context).shouldRest(
                PetRestSchedule(enabled = false, followDoNotDisturb = false), LocalTime.NOON,
            ),
        )
    }

    private fun awaitWithinThreeSeconds(expected: Boolean, read: () -> Boolean): Boolean {
        val deadline: Long = SystemClock.elapsedRealtime() + 3_000L
        var value: Boolean = read()
        while (SystemClock.elapsedRealtime() < deadline) {
            if (value == expected) return value
            SystemClock.sleep(100L)
            value = read()
        }
        return value
    }

    private fun isDisposableEmulator(): Boolean =
        Build.FINGERPRINT.contains("generic") ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.HARDWARE == "ranchu"
}

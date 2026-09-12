package com.pixelpals.app.feature.home

import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.pixelpals.app.CompanionSettingsActivity
import com.pixelpals.app.R
import org.junit.Assert.*
import org.junit.Test

class DecorationPreviewLifecycleTest {
    @Test fun replacingAndDestroyingThePreviewScreenDismissesItsWindows(): Unit {
        ActivityScenario.launch(CompanionSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment: DecorationsTabFragment = DecorationsTabFragment()
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.companionSettings, fragment).commitNow()
                val preview = DecorationsTabFragment::class.java.getDeclaredMethod(
                    "preview", Decoration::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                val dialogField = DecorationsTabFragment::class.java.getDeclaredField("previewDialog").apply { isAccessible = true }
                val item: Decoration = requireNotNull(DecorationCatalog.find("fern"))
                preview.invoke(fragment, item, false)
                val first: AlertDialog = dialogField.get(fragment) as AlertDialog
                assertTrue(first.isShowing)
                preview.invoke(fragment, item, false)
                val second: AlertDialog = dialogField.get(fragment) as AlertDialog
                assertFalse(first.isShowing)
                assertTrue(second.isShowing)
                activity.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
                assertFalse(second.isShowing)
                assertNull(dialogField.get(fragment))
            }
        }
    }

    @Test fun busyPurchaseDisablesTheSamePreviewWithoutRebuildingTheCatalog(): Unit {
        ActivityScenario.launch(CompanionSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment: DecorationsTabFragment = DecorationsTabFragment()
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.companionSettings, fragment).commitNow()
                val render = DecorationsTabFragment::class.java.getDeclaredMethod(
                    "render", CompanionWorld::class.java).apply { isAccessible = true }
                render.invoke(fragment, CompanionWorld())
                val body = DecorationsTabFragment::class.java.getDeclaredField("body")
                    .apply { isAccessible = true }.get(fragment) as LinearLayout
                val originalCard: View = body.getChildAt(1)
                val preview = DecorationsTabFragment::class.java.getDeclaredMethod(
                    "preview", Decoration::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                val dialogField = DecorationsTabFragment::class.java.getDeclaredField("previewDialog")
                    .apply { isAccessible = true }
                preview.invoke(fragment, requireNotNull(DecorationCatalog.find("fern")), false)
                val dialog: AlertDialog = dialogField.get(fragment) as AlertDialog
                val purchase = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                val update = DecorationsTabFragment::class.java.getDeclaredMethod(
                    "updatePurchaseState", Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                val status = DecorationsTabFragment::class.java.getDeclaredField("purchaseStatus")
                    .apply { isAccessible = true }.get(fragment) as TextView
                update.invoke(fragment, true)
                assertEquals(View.VISIBLE, status.visibility)
                assertEquals(activity.getString(R.string.home_purchase_processing), status.text.toString())
                assertFalse(purchase.isEnabled)
                assertSame(originalCard, body.getChildAt(1))
                update.invoke(fragment, false)
                assertEquals(View.GONE, status.visibility)
                assertTrue(purchase.isEnabled)
                assertSame(originalCard, body.getChildAt(1))
            }
        }
    }
}

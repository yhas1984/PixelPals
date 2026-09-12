package com.pixelpals.app.feature.home

import android.os.Build
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.MainActivity
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Ensures a dialog view recreated during a save keeps its controls locked. */
@RunWith(AndroidJUnit4::class)
class PetNameDialogLifecycleTest {
    @Test fun recreatedViewReappliesSavingStateToAllControls(): Unit {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment: PetNameDialogFragment = PetNameDialogFragment.create(PetType.CORGI, "").also {
                    it.showNow(activity.supportFragmentManager, PetNameDialogFragment.TAG)
                }
                PetNameDialogFragment::class.java.getDeclaredField("saving").apply {
                    isAccessible = true
                    setBoolean(fragment, true)
                }
            }
            scenario.onActivity { activity ->
                val fragment: PetNameDialogFragment = requireNotNull(
                    activity.supportFragmentManager.findFragmentByTag(PetNameDialogFragment.TAG),
                ) as PetNameDialogFragment
                activity.supportFragmentManager.beginTransaction().detach(fragment).commitNow()
                activity.supportFragmentManager.beginTransaction().attach(fragment).commitNow()
            }
            scenario.onActivity { activity ->
                val fragment: PetNameDialogFragment = requireNotNull(
                    activity.supportFragmentManager.findFragmentByTag(PetNameDialogFragment.TAG),
                ) as PetNameDialogFragment
                val dialog: AlertDialog = fragment.requireDialog() as AlertDialog
                assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                assertFalse(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
                assertFalse(requireNotNull(dialog.findViewById<EditText>(R.id.petNameDraft)).isEnabled)
            }
        }
    }
}

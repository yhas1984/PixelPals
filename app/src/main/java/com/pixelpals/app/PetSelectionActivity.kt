package com.pixelpals.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pixelpals.app.navigation.PixelPalsDestination

/** Compatibility redirect for tasks created before root navigation became single-activity. */
class PetSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(MainActivity.createIntent(this, PixelPalsDestination.PETS))
        finish()
        overridePendingTransition(0, 0)
    }
}

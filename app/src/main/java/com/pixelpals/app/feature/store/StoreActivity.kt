package com.pixelpals.app.feature.store

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pixelpals.app.MainActivity
import com.pixelpals.app.navigation.PixelPalsDestination
import com.pixelpals.app.navigation.StoreSection

/** Compatibility redirect for tasks created before root navigation became single-activity. */
class StoreActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            MainActivity.createIntent(
                this,
                PixelPalsDestination.STORE,
                StoreSection.PREMIUM,
            ),
        )
        finish()
        overridePendingTransition(0, 0)
    }
}

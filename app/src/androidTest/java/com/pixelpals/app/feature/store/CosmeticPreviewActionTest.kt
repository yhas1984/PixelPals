package com.pixelpals.app.feature.store

import android.widget.Button
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.R
import com.pixelpals.app.data.catalog.CosmeticCatalog
import org.junit.Assert.*
import org.junit.Test

class CosmeticPreviewActionTest {
    @Test fun previewRemainsAvailableWhenPurchaseIsDisabledAndNeverRunsPurchaseAction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            val cosmetic = CosmeticCatalog.all(context).first { it.coinPrice != null }
            var actions = 0
            var previewed: String? = null
            val adapter = CosmeticCatalogAdapter({ actions++ }, { previewed = it.id })
            val holder = adapter.onCreateViewHolder(FrameLayout(context), 1) as CosmeticCatalogAdapter.CosmeticViewHolder
            holder.bind(CosmeticCatalogRow.Item(cosmetic, CosmeticAction.BUY, false),
                { actions++ }, { previewed = it.id })
            val purchase = holder.itemView.findViewById<Button>(R.id.btnCosmeticAction)
            val preview = holder.itemView.findViewById<Button>(R.id.btnCosmeticPreview)
            assertFalse(purchase.isEnabled)
            assertTrue(preview.isEnabled)
            assertEquals(context.getString(R.string.store_preview_cosmetic_named, cosmetic.displayName), preview.contentDescription)
            assertTrue(preview.performClick())
            assertEquals(cosmetic.id, previewed)
            assertEquals(0, actions)
        }
    }
}

package com.pixelpals.app

import android.content.Context
import android.content.res.Configuration
import android.widget.Button
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.CatalogItemState
import com.pixelpals.app.data.catalog.PetCatalogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class PetCatalogLocalePresentationTest {
    @Test
    fun materializedEnglishItemUsesTheViewLocaleWhenBound(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base: Context = instrumentation.targetContext
        val item = PetCatalogItem(
            id = "angel",
            displayName = "Cherub",
            description = "The Little Cherub Radiant & serene",
            previewResId = PetType.ANGEL.spriteResId,
            petType = PetType.ANGEL,
            productId = "pet_angel_premium",
            isPremium = true,
            state = CatalogItemState.OWNED,
        )
        var callbackItem: PetCatalogItem? = null
        for (language in listOf("en", "es")) {
            lateinit var context: Context
            lateinit var parent: RecyclerView
            lateinit var adapter: PetCatalogAdapter
            instrumentation.runOnMainSync {
                val configuration = Configuration(base.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language))
                }
                context = ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.Theme_PixelPals)
                parent = RecyclerView(context).apply {
                    layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
                }
                adapter = PetCatalogAdapter(PetCatalogMode.SELECTION) { callbackItem = it }
                adapter.submitList(listOf(PetCatalogRow(item)))
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val holder = adapter.onCreateViewHolder(parent, 0)
                adapter.onBindViewHolder(holder, 0)
                val expectedName = context.getString(PetType.ANGEL.displayNameResId)
                val expectedDescription = context.getString(PetType.ANGEL.descriptionResId).replace('\n', ' ')
                assertEquals(expectedName, holder.itemView.findViewById<android.widget.TextView>(R.id.txtPetName).text)
                assertEquals(expectedDescription, holder.itemView.findViewById<android.widget.TextView>(R.id.txtPetDesc).text)
                assertTrue(holder.itemView.contentDescription.toString().contains(expectedName))
                assertTrue(holder.itemView.contentDescription.toString().contains(expectedDescription))
                holder.itemView.findViewById<Button>(R.id.btnPetAction).performClick()
            }
            instrumentation.waitForIdleSync()
            assertSame(item, callbackItem)
        }
    }
}

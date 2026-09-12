package com.pixelpals.app.feature.home

import android.content.ContextWrapper
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.database.HomeDecorationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** FileProvider/canvas checks only; no pet progress changes or external share launch. */
@RunWith(AndroidJUnit4::class)
class PostcardExporterTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun scene(): HomeSceneView {
        lateinit var scene: HomeSceneView
        instrumentation.runOnMainSync {
            scene = HomeSceneView(context).apply {
                placements = listOf(HomeDecorationEntity("corgi", "linen_bed", 0, 0))
                measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(304, View.MeasureSpec.EXACTLY))
                layout(0, 0, 400, 304)
                isEditing = true
            }
        }
        return scene
    }
    @Test fun exportsReadableUniquePngWithoutCancellingOrCapturingPlacement(): Unit = runBlocking {
        val scene: HomeSceneView = scene()
        val first: Intent = PostcardExporter.prepare(context, scene, "Our home")
        var editingNotifications: Int = 0
        var pending: Any? = null
        val dragged = HomeSceneView::class.java.getDeclaredField("dragged").apply { isAccessible = true }
        instrumentation.runOnMainSync {
            scene.beginPlacement("linen_bed")
            pending = dragged.get(scene)
            scene.onEditingChanged = { editingNotifications++ }
        }
        val second: Intent = PostcardExporter.prepare(context, scene, "Our home")
        instrumentation.runOnMainSync {
            assertTrue(scene.isEditing)
            assertSame("Export must preserve the in-progress drag", pending, dragged.get(scene))
            assertEquals(0, editingNotifications)
        }
        val firstUri: Uri = requireNotNull(first.clipData).getItemAt(0).uri
        val secondUri: Uri = requireNotNull(second.clipData).getItemAt(0).uri
        assertNotEquals(firstUri, secondUri)
        assertEquals(Intent.ACTION_SEND, second.action)
        assertEquals("image/png", second.type)
        assertEquals("Our home", second.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(second.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("content", secondUri.scheme)
        val before = context.contentResolver.openInputStream(firstUri).use { BitmapFactory.decodeStream(it) }
        val during = context.contentResolver.openInputStream(secondUri).use { BitmapFactory.decodeStream(it) }
        try {
            assertNotNull(before); assertNotNull(during)
            assertEquals(1200, during.width)
            assertEquals(1012, during.height)
            assertTrue("Export the saved layout, not the drag ghost or grid", before.sameAs(during))
        } finally {
            before?.recycle(); during?.recycle()
            context.contentResolver.delete(firstUri, null, null)
            context.contentResolver.delete(secondUri, null, null)
        }
    }
    @Test fun unavailableOutputDirectoryKeepsTheEditorIntact(): Unit = runBlocking {
        val scene: HomeSceneView = scene()
        instrumentation.runOnMainSync { scene.beginPlacement("linen_bed") }
        val blocker: File = File.createTempFile("postcard-test-", ".tmp", context.cacheDir)
        val failingContext = object : ContextWrapper(context) { override fun getCacheDir(): File = blocker }
        try {
            try {
                PostcardExporter.prepare(failingContext, scene, "Our home")
                fail("Writing below a regular file must fail")
            } catch (_: IllegalStateException) { }
            instrumentation.runOnMainSync {
                assertTrue(scene.isEditing)
                val field = HomeSceneView::class.java.getDeclaredField("pendingPlacement").apply { isAccessible = true }
                assertEquals("linen_bed", field.get(scene))
            }
        } finally { blocker.delete() }
    }

    @Test fun cancellingDuringPngWriteDoesNotLaunchAChooser(): Unit = runBlocking {
        val scene: HomeSceneView = scene()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val launches = AtomicInteger(0)
        val cache = File.createTempFile("postcard-cancel-", ".dir", context.cacheDir).apply { delete(); mkdirs() }
        val gatedContext = object : ContextWrapper(context) {
            override fun getCacheDir(): File {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "PNG gate was not released" }
                return cache
            }
            override fun startActivity(intent: Intent): Unit { launches.incrementAndGet() }
        }
        val job = launch(Dispatchers.Main.immediate) {
            PostcardExporter.share(gatedContext, scene, "Our home")
        }
        try {
            assertTrue("PNG writer did not enter IO", entered.await(5, TimeUnit.SECONDS))
            job.cancel()
        } finally {
            release.countDown()
            job.join()
            cache.deleteRecursively()
        }
        assertEquals("Cancelled share launched a chooser", 0, launches.get())
    }
}

package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TelaHomeWebRenderingTest {
    @Test fun realHomeRendersConnectedHuntAndSingleMealAtStableSpeciesSize() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val sheet=Bitmap.createBitmap(1500,1600,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(sheet)
        canvas.drawColor(Color.rgb(250,247,239))
        var markers=0
        instrumentation.runOnMainSync {
            val scene=HomeSceneView(context).apply { reviewSeed=7 }
            runBlocking { scene.loadPet(PetType.TELA) }
            scene.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(760,View.MeasureSpec.EXACTLY))
            scene.layout(0,0,1000,760)
            scene.onWebMealReady={ markers++ }
            assertTrue(scene.huntFly(1))
            val label=Paint().apply { color=Color.DKGRAY; textSize=15f }
            for (frame in 0..11) {
                if (frame > 0) repeat(125) { scene.advanceScene(16) }
                canvas.save(); canvas.translate(frame%3*500f,frame/3*400f)
                canvas.save(); canvas.scale(.5f,.5f); scene.draw(canvas); canvas.restore()
                canvas.drawText("${frame*2}s",10f,394f,label); canvas.restore()
                assertEquals(174f,scene.renderedActorSize,.01f)
            }
            assertEquals(1,markers)
            scene.finishWebMeal(true)
            repeat(200) { scene.advanceScene(16) }
            assertEquals(1,markers)
            scene.energy = 10
            repeat(50) { scene.advanceScene(16) }
            assertEquals(CompanionActivity.REST, scene.activity)
            val web = HomeSceneView::class.java.getDeclaredField("web").apply { isAccessible = true }.get(scene) as TelaHomeWeb
            val restPoint = WebPoint(web.x, web.y)
            repeat(150) { scene.advanceScene(16) }
            assertEquals(restPoint, WebPoint(web.x, web.y))
            scene.energy = 90
            scene.advanceScene(16)
            assertEquals(CompanionActivity.WAKE, scene.activity)
            assertEquals(restPoint, WebPoint(web.x, web.y))
            repeat(200) { scene.advanceScene(16) }
            assertFalse(scene.activity == CompanionActivity.REST || scene.activity == CompanionActivity.WAKE)
            scene.isEditing=true
            assertFalse(scene.huntFly(0))
            scene.isEditing=false; scene.isTravelling=true
            assertFalse(scene.huntFly(0))
        }
        File(context.filesDir,"tela-web-home.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG,100,it) }
        sheet.recycle()
    }
}

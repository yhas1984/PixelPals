package com.pixelpals.app.feature.home

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PostcardExporter {
    suspend fun share(context: Context, scene: HomeSceneView, caption: String): Unit = withContext(Dispatchers.Main.immediate) {
        val send: Intent = prepare(context, scene, caption)
        val chooser: Intent = Intent.createChooser(send, null)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    internal suspend fun prepare(context: Context, scene: HomeSceneView, caption: String): Intent = withContext(Dispatchers.Main.immediate) {
        require(scene.width > 0 && scene.height > 0)
        val width: Int = 1200
        val height: Int = (width * scene.height.toFloat() / scene.width).toInt()
        val bitmap: Bitmap = Bitmap.createBitmap(width, height + 100, Bitmap.Config.ARGB_8888)
        try {
            val canvas: Canvas = Canvas(bitmap)
            canvas.drawColor(HomeUi.cream)
            canvas.save(); canvas.scale(width.toFloat() / scene.width, height.toFloat() / scene.height)
            try { scene.drawPostcard(canvas) } finally { canvas.restore() }
            val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HomeUi.ink; textSize = 29f; textAlign = Paint.Align.CENTER }
            while (paint.measureText(caption) > width - 60 && paint.textSize > 14) paint.textSize -= 1
            canvas.drawText(caption, width / 2f, height + 60f, paint)
            val file: File = withContext(Dispatchers.IO) {
                val directory: File = File(context.cacheDir, "postcards")
                check(directory.isDirectory || directory.mkdirs()) { "Postcard directory unavailable" }
                directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
                val output: File = File.createTempFile("pixelpals-", ".png", directory)
                try {
                    output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    output
                } catch (exception: Exception) { output.delete(); throw exception }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.postcards", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, caption)
                clipData = ClipData.newUri(context.contentResolver, "PixelPals", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } finally { bitmap.recycle() }
    }
}

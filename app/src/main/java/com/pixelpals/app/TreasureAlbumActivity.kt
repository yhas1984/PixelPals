package com.pixelpals.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pixelpals.app.database.AppDatabase
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class TreasureAlbumActivity : AppCompatActivity() {

    private lateinit var adapter: TreasureAdapter
    private val scope = CoroutineScope(Dispatchers.Main)
    private val debugLogPath = "/media/yhas/_dde_data/home/yhas/Programacion/PixelPals/PixelPals/.cursor/debug-a40953.log"

    private fun debugLog(runId: String, hypothesisId: String, location: String, message: String, data: JSONObject) {
        // #region agent log
        val payload = JSONObject().apply {
            put("sessionId", "a40953")
            put("runId", runId)
            put("hypothesisId", hypothesisId)
            put("location", location)
            put("message", message)
            put("data", data)
            put("timestamp", System.currentTimeMillis())
        }
        Log.i("AGENT_DEBUG", payload.toString())
        try {
            File(debugLogPath).appendText(payload.toString() + "\n")
        } catch (_: Exception) {
        }
        // #endregion
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_treasure_album)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewTreasures)
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)

        val dao = AppDatabase.getDatabase(this).treasureDao()
        
        // Purgado de Base de Datos para limpiar los errores del Corgi
        scope.launch(Dispatchers.IO) {
            val rogueItem = dao.getTreasure("Hueso Prehistórico")
            debugLog(
                runId = "post-fix",
                hypothesisId = "H1",
                location = "TreasureAlbumActivity.kt:onCreate",
                message = "Resultado de búsqueda de item rogue",
                data = JSONObject().apply {
                    put("queryEmoji", "Hueso Prehistórico")
                    put("found", rogueItem != null)
                    put("foundEmoji", rogueItem?.emoji ?: "null")
                }
            )
            if (rogueItem != null) {
                dao.deleteTreasure(rogueItem)
            }
        }

        adapter = TreasureAdapter { treasure ->
            scope.launch {
                debugLog(
                    runId = "post-fix",
                    hypothesisId = "H2",
                    location = "TreasureAlbumActivity.kt:onTreasureClick",
                    message = "Consumo de tesoro solicitado",
                    data = JSONObject().apply {
                        put("emoji", treasure.emoji)
                        put("countBefore", treasure.count)
                    }
                )
                if (treasure.count <= 1) {
                    dao.deleteTreasure(treasure)
                } else {
                    dao.updateTreasure(treasure.copy(count = treasure.count - 1))
                }
                
                // Alert Pet!
                val consumeIntent = android.content.Intent(this@TreasureAlbumActivity, PetService::class.java).apply {
                    action = PetService.ACTION_CONSUME_TREASURE
                    putExtra("TREASURE_EMOJI", treasure.emoji)
                }
                androidx.core.content.ContextCompat.startForegroundService(this@TreasureAlbumActivity, consumeIntent)
                android.widget.Toast.makeText(this@TreasureAlbumActivity, "¡Le diste un ${treasure.emoji} a tu mascota!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Fetch using coroutine
        scope.launch {
            dao.getAllTreasures().collect { treasures ->
                if (treasures.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvEmptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.submitList(treasures)
                }
            }
        }
    }
}

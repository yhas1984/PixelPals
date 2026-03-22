package com.pixelpals.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pixelpals.app.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TreasureAlbumActivity : AppCompatActivity() {

    private lateinit var adapter: TreasureAdapter
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_treasure_album)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewTreasures)
        val tvEmptyState = findViewById<TextView>(R.id.tvEmptyState)

        val dao = AppDatabase.getDatabase(this).treasureDao()
        
        // Purgado de Base de Datos para limpiar los errores del Corgi
        scope.launch(Dispatchers.IO) {
            val rogueItem = dao.getTreasure("Hueso Prehistórico")
            if (rogueItem != null) {
                dao.deleteTreasure(rogueItem)
            }
        }

        adapter = TreasureAdapter { treasure ->
            scope.launch {
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

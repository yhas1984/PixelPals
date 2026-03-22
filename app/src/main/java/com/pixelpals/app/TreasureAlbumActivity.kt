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

        adapter = TreasureAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Fetch using coroutine
        val dao = AppDatabase.getDatabase(this).treasureDao()
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

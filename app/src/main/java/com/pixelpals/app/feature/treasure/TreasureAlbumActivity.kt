package com.pixelpals.app.feature.treasure

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pixelpals.app.PetService
import com.pixelpals.app.R
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.data.repository.PixelPalsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TreasureAlbumActivity : AppCompatActivity() {

    private lateinit var adapter: TreasureAdapter
    private lateinit var tvAlbumState: TextView
    private lateinit var cardAlbumState: LinearLayout
    private lateinit var progressAlbumLoading: ProgressBar
    private lateinit var btnAlbumRetry: Button
    private lateinit var tvEmptyState: TextView
    private lateinit var recyclerView: RecyclerView
    private val repository: PixelPalsRepository by lazy { AppServices.repository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.treasure_album_title)
        edgeToEdge()
        setContentView(R.layout.activity_treasure_album)

        recyclerView = findViewById(R.id.recyclerViewTreasures)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        cardAlbumState = findViewById(R.id.cardAlbumState)
        tvAlbumState = findViewById(R.id.tvAlbumState)
        progressAlbumLoading = findViewById(R.id.pbAlbumLoading)
        btnAlbumRetry = findViewById(R.id.btnAlbumRetry)
        applySystemBarsInsets()
        btnAlbumRetry.setOnClickListener { loadTreasureAlbum() }

        showLoadingState(getString(R.string.treasure_album_loading))
        adapter = TreasureAdapter { treasure ->
            consumeTreasure(treasure)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        loadTreasureAlbum()
    }

    private fun loadTreasureAlbum() {
        val dao = AppDatabase.getDatabase(this).treasureDao()
        lifecycleScope.launch {
            showLoadingState(getString(R.string.treasure_album_loading))
            runCatching {
                dao.getAllTreasures().collectLatest { treasures ->
                    if (treasures.isEmpty()) {
                        showEmptyState(getString(R.string.treasure_album_empty))
                    } else {
                        showContentState(getString(R.string.treasure_album_ready))
                        tvEmptyState.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        adapter.submitList(treasures)
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) return@launch
                showErrorState(getString(R.string.treasure_album_error))
            }
        }
    }

    private fun consumeTreasure(treasure: com.pixelpals.app.database.TreasureItem) {
        lifecycleScope.launch {
            val remaining = repository.consumeTreasure(treasure.emoji)

            PetService.requestTreasureReactionIfRunning(this@TreasureAlbumActivity, treasure.emoji)

            val remainingText = if (remaining > 0) {
                resources.getQuantityString(
                    R.plurals.treasure_consume_remaining_count,
                    remaining,
                    remaining
                )
            } else {
                getString(R.string.treasure_consume_none_left)
            }
            val message = getString(R.string.treasure_consume_announcement, treasure.emoji, remainingText)
            android.widget.Toast.makeText(
                this@TreasureAlbumActivity,
                message,
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showLoadingState(message: String) {
        tvAlbumState.text = message
        tvAlbumState.setTextColor(ContextCompat.getColor(this, R.color.status_info_fg))
        cardAlbumState.setBackgroundResource(R.drawable.bg_status_info)
        progressAlbumLoading.visibility = View.VISIBLE
        btnAlbumRetry.visibility = View.GONE
        tvEmptyState.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }

    private fun showEmptyState(message: String) {
        tvAlbumState.text = message
        tvAlbumState.setTextColor(ContextCompat.getColor(this, R.color.status_empty_fg))
        cardAlbumState.setBackgroundResource(R.drawable.bg_status_empty)
        progressAlbumLoading.visibility = View.GONE
        btnAlbumRetry.visibility = View.VISIBLE
        tvEmptyState.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvEmptyState.text = message
    }

    private fun showContentState(message: String) {
        tvAlbumState.text = message
        tvAlbumState.setTextColor(ContextCompat.getColor(this, R.color.status_success_fg))
        cardAlbumState.setBackgroundResource(R.drawable.bg_status_success)
        progressAlbumLoading.visibility = View.GONE
        btnAlbumRetry.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        tvAlbumState.text = message
        tvAlbumState.setTextColor(ContextCompat.getColor(this, R.color.red_error))
        cardAlbumState.setBackgroundResource(R.drawable.bg_status_error)
        progressAlbumLoading.visibility = View.GONE
        btnAlbumRetry.visibility = View.VISIBLE
        tvEmptyState.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }

    private fun edgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun applySystemBarsInsets() {
        val view = findViewById<LinearLayout>(R.id.albumRoot)
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }
}

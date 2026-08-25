package com.pixelpals.app.feature.treasure

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pixelpals.app.PetService
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.prefs.SelectedPetStore
import kotlinx.coroutines.launch

class TreasureAlbumActivity : AppCompatActivity() {
    private lateinit var adapter: TreasureAdapter
    private lateinit var selectedPet: PetType
    private lateinit var viewModel: TreasureAlbumViewModel
    private lateinit var albumStateText: TextView
    private lateinit var albumStateCard: LinearLayout
    private lateinit var albumLoadingProgress: ProgressBar
    private lateinit var albumRetryButton: Button
    private lateinit var collectionSummaryCard: LinearLayout
    private lateinit var collectionProgressText: TextView
    private lateinit var collectionProgress: ProgressBar
    private lateinit var collectionBadgeText: TextView
    private lateinit var collectionNextRewardText: TextView
    private lateinit var dailyGiftStatusText: TextView
    private lateinit var recyclerView: RecyclerView
    private var pendingGiftItem: TreasureCollectionItem? = null
    private var hasTrackedAlbumOpen: Boolean = false
    private val analytics by lazy { AppServices.analytics(this) }

    override fun onCreate(savedInstanceState: Bundle?): Unit {
        super.onCreate(savedInstanceState)
        setTitle(R.string.treasure_album_title)
        edgeToEdge()
        setContentView(R.layout.activity_treasure_album)
        selectedPet = SelectedPetStore(this).load()
        viewModel = ViewModelProvider(
            this,
            TreasureAlbumViewModel.Factory(application, selectedPet),
        )[TreasureAlbumViewModel::class.java]
        bindViews()
        applySystemBarsInsets()
        configureAlbum()
        observeAlbum()
    }

    private fun bindViews(): Unit {
        recyclerView = findViewById(R.id.recyclerViewTreasures)
        albumStateCard = findViewById(R.id.cardAlbumState)
        albumStateText = findViewById(R.id.tvAlbumState)
        albumLoadingProgress = findViewById(R.id.pbAlbumLoading)
        albumRetryButton = findViewById(R.id.btnAlbumRetry)
        collectionSummaryCard = findViewById(R.id.cardCollectionSummary)
        collectionProgressText = findViewById(R.id.tvCollectionProgress)
        collectionProgress = findViewById(R.id.progressTreasureCollection)
        collectionBadgeText = findViewById(R.id.tvCollectionBadge)
        collectionNextRewardText = findViewById(R.id.tvCollectionNextReward)
        dailyGiftStatusText = findViewById(R.id.tvDailyGiftStatus)
    }

    private fun configureAlbum(): Unit {
        adapter = TreasureAdapter(::showGiftConfirmation)
        recyclerView.layoutManager = GridLayoutManager(this, ALBUM_COLUMN_COUNT)
        recyclerView.adapter = adapter
        albumRetryButton.setOnClickListener { viewModel.refresh() }
    }

    private fun observeAlbum(): Unit {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> renderState(state) }
            }
        }
    }

    private fun renderState(state: TreasureAlbumUiState): Unit {
        val collection: TreasureCollection? = state.collection
        when {
            state.hasError && collection == null -> showErrorState()
            collection == null -> showLoadingState()
            else -> renderCollection(collection, state.isGiftInProgress)
        }
        state.giftResult?.let(::handleGiftResult)
    }

    private fun renderCollection(collection: TreasureCollection, isGiftInProgress: Boolean): Unit {
        collectionSummaryCard.visibility = View.VISIBLE
        recyclerView.visibility = View.VISIBLE
        recyclerView.alpha = if (isGiftInProgress) 0.65f else 1f
        collectionProgress.max = collection.summary.totalCount
        collectionProgress.progress = collection.summary.discoveredCount
        collectionProgressText.text = getString(
            R.string.treasure_collection_progress,
            collection.summary.discoveredCount,
            collection.summary.totalCount,
        )
        collectionBadgeText.text = getString(
            R.string.treasure_collection_badge_format,
            getString(getBadgeResource(collection.summary.badge)),
        )
        collectionNextRewardText.text = getNextRewardText(collection.summary)
        dailyGiftStatusText.text = getDailyGiftStatus(collection.summary)
        adapter.submitList(collection.items)
        showContentState()
        trackAlbumOpen(collection.summary)
    }

    private fun getNextRewardText(summary: TreasureCollectionSummary): String {
        val milestone: Int = summary.nextMilestone ?: return getString(R.string.treasure_collection_complete)
        val reward: Int = summary.nextRewardCoins ?: 0
        return getString(
            R.string.treasure_collection_next_reward,
            milestone,
            reward,
            summary.totalCount,
        )
    }

    private fun getDailyGiftStatus(summary: TreasureCollectionSummary): String {
        val petName: String = getString(selectedPet.displayNameResId)
        return getString(
            when {
                !summary.isPetActive -> R.string.treasure_collection_gift_inactive
                summary.hasGiftedToday -> R.string.treasure_collection_gift_used
                else -> R.string.treasure_collection_gift_available
            },
            petName,
        )
    }

    private fun showGiftConfirmation(item: TreasureCollectionItem): Unit =
        showGiftConfirmation(item, forceNoReward = false)

    private fun showGiftConfirmation(item: TreasureCollectionItem, forceNoReward: Boolean): Unit {
        val summary: TreasureCollectionSummary = viewModel.uiState.value.collection?.summary ?: return
        val acceptsNoBondReward: Boolean = forceNoReward || summary.currentBond >= MAX_BOND
        val inventoryText: String = resources.getQuantityString(
            R.plurals.treasure_inventory_count,
            item.inventoryCount,
            item.inventoryCount,
        )
        val rewardText: String = getString(
            when {
                acceptsNoBondReward -> R.string.treasure_gift_reward_max_bond
                item.isFavorite -> R.string.treasure_gift_reward_favorite
                else -> R.string.treasure_gift_reward_regular
            }
        )
        pendingGiftItem = item
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.treasure_gift_dialog_title, item.name))
            .setMessage(getString(R.string.treasure_gift_dialog_message, item.story, inventoryText, rewardText))
            .setNegativeButton(R.string.treasure_gift_cancel) { _, _ -> pendingGiftItem = null }
            .setPositiveButton(R.string.treasure_gift_confirm) { _, _ ->
                viewModel.giftTreasure(item, acceptsNoBondReward)
            }
            .show()
    }

    private fun handleGiftResult(result: TreasureGiftResult): Unit {
        viewModel.consumeGiftResult()
        if (result == TreasureGiftResult.MaximumBondConfirmationRequired) {
            pendingGiftItem?.let { item -> showGiftConfirmation(item, forceNoReward = true) }
            return
        }
        val message: String = when (result) {
            is TreasureGiftResult.Success -> handleGiftSuccess(result)
            TreasureGiftResult.AlreadyGiftedToday -> getString(R.string.treasure_gift_already_today)
            TreasureGiftResult.PetNotActive -> getString(R.string.treasure_gift_pet_inactive)
            TreasureGiftResult.TreasureUnavailable -> getString(R.string.treasure_gift_unavailable)
            TreasureGiftResult.MaximumBondConfirmationRequired -> return
        }
        pendingGiftItem = null
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun handleGiftSuccess(result: TreasureGiftResult.Success): String {
        analytics.track(
            "treasure_gift_completed",
            mapOf(
                "pet_id" to selectedPet.name.lowercase(),
                "treasure_id" to result.treasureId,
                "favorite" to result.isFavorite.toString(),
                "bond_gain" to result.bondGained.toString(),
            ),
        )
        if (result.isFavorite) {
            analytics.track(
                "treasure_favorite_gift",
                mapOf("pet_id" to selectedPet.name.lowercase(), "treasure_id" to result.treasureId),
            )
        }
        PetService.requestTreasureReactionIfRunning(this, result.emoji)
        val petName: String = getString(selectedPet.displayNameResId)
        val itemName: String = pendingGiftItem?.name ?: result.emoji
        return if (result.bondGained > 0) {
            getString(
                R.string.treasure_gift_success,
                petName,
                itemName,
                result.bondGained,
                result.remainingCount,
            )
        } else {
            getString(
                R.string.treasure_gift_success_max_bond,
                petName,
                itemName,
                result.remainingCount,
            )
        }
    }

    private fun trackAlbumOpen(summary: TreasureCollectionSummary): Unit {
        if (hasTrackedAlbumOpen) return
        hasTrackedAlbumOpen = true
        analytics.track(
            "treasure_album_opened",
            mapOf(
                "pet_id" to selectedPet.name.lowercase(),
                "discovered" to summary.discoveredCount.toString(),
                "badge" to summary.badge.name.lowercase(),
            ),
        )
    }

    private fun getBadgeResource(badge: TreasureBadge): Int = when (badge) {
        TreasureBadge.NONE -> R.string.treasure_badge_none
        TreasureBadge.BRONZE -> R.string.treasure_badge_bronze
        TreasureBadge.SILVER -> R.string.treasure_badge_silver
        TreasureBadge.GOLD -> R.string.treasure_badge_gold
        TreasureBadge.LEGENDARY -> R.string.treasure_badge_legendary
    }

    private fun showLoadingState(): Unit {
        albumStateText.text = getString(R.string.treasure_album_loading)
        albumStateText.setTextColor(ContextCompat.getColor(this, R.color.status_info_fg))
        albumStateCard.setBackgroundResource(R.drawable.bg_status_info)
        albumLoadingProgress.visibility = View.VISIBLE
        albumRetryButton.visibility = View.GONE
        collectionSummaryCard.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }

    private fun showContentState(): Unit {
        albumStateText.text = getString(R.string.treasure_album_ready)
        albumStateText.setTextColor(ContextCompat.getColor(this, R.color.status_success_fg))
        albumStateCard.setBackgroundResource(R.drawable.bg_status_success)
        albumLoadingProgress.visibility = View.GONE
        albumRetryButton.visibility = View.GONE
    }

    private fun showErrorState(): Unit {
        albumStateText.text = getString(R.string.treasure_album_error)
        albumStateText.setTextColor(ContextCompat.getColor(this, R.color.red_error))
        albumStateCard.setBackgroundResource(R.drawable.bg_status_error)
        albumLoadingProgress.visibility = View.GONE
        albumRetryButton.visibility = View.VISIBLE
        collectionSummaryCard.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }

    private fun edgeToEdge(): Unit {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.surface_base)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.surface_base)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun applySystemBarsInsets(): Unit {
        val view: LinearLayout = findViewById(R.id.albumRoot)
        val initialLeft: Int = view.paddingLeft
        val initialTop: Int = view.paddingTop
        val initialRight: Int = view.paddingRight
        val initialBottom: Int = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            target.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private companion object {
        const val ALBUM_COLUMN_COUNT: Int = 2
        const val MAX_BOND: Int = 100
    }
}

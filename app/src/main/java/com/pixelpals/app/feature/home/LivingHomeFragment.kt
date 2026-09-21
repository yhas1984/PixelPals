package com.pixelpals.app.feature.home

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.core.view.doOnLayout
import androidx.lifecycle.*
import com.pixelpals.app.*
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.core.review.PlayReviewLauncher
import com.pixelpals.app.core.review.ReviewMoment
import com.pixelpals.app.core.review.ReviewPromptInput
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.feature.care.*
import com.pixelpals.app.navigation.*
import kotlinx.coroutines.*

open class LivingHomeFragment : Fragment() {
    companion object {
        const val PLACE_OBJECT_RESULT: String = "home_place_store_object"
    }
    private var requestedPlacement: String? = null
    private var requestedPlacementPet: String? = null
    private lateinit var model: CompanionViewModel
    private var scene: HomeSceneView? = null
    private var panel: CareScenePanel? = null
    private var careModel: CareSceneViewModel? = null
    private var content: LinearLayout? = null
    private var heading: TextView? = null
    private var description: TextView? = null
    private var needs: TextView? = null
    private var greeting: TextView? = null
    private var favorite: TextView? = null
    private var travel: Button? = null
    private var treeButton: View? = null
    private var webButton: Button? = null
    private var webFeeding: TelaWebFeeding? = null
    private var careButton: Button? = null
    private var decorationControls: View? = null
    private var errorText: Button? = null
    private var currentPet: PetType? = null
    private var loadJob: Job? = null
    private var treasureJob: Job? = null
    private var postcardJob: Job? = null
    private var postcardButton: Button? = null
    private var pendingCareObject: String? = null
    private var isCareOpen: Boolean = false
    private var desktopPermissionJob: Job? = null
    private val desktopPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        desktopPermissionJob?.cancel()
        desktopPermissionJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.withResumed { }
            // Some Android versions return before AppOps exposes the new grant.
            repeat(10) {
                if (!isResumed) return@launch
                if (Settings.canDrawOverlays(requireContext())) {
                    requestDesktop()
                    return@launch
                }
                delay(100)
            }
        }
    }
    private var lastWorld: CompanionWorld = CompanionWorld()
    private var introductionDialog: AlertDialog? = null
    private var offeredIntroduction: Boolean = false
    private var firstHomeGuide: FirstHomeGuideView? = null

    override fun onCreate(savedInstanceState: Bundle?): Unit {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[CompanionViewModel::class.java]
        requestedPlacement = savedInstanceState?.getString("store_placement")
        requestedPlacementPet = savedInstanceState?.getString("store_placement_pet")
        parentFragmentManager.setFragmentResultListener(PLACE_OBJECT_RESULT, this) { _, result ->
            requestedPlacement = result.getString("decoration")
            requestedPlacementPet = result.getString("pet")
            model.refresh()
            beginRequestedPlacement()
        }
        childFragmentManager.setFragmentResultListener(PetNameDialogFragment.RESULT, this) { _, _ -> model.refresh() }
        childFragmentManager.setFragmentResultListener(UserNameDialogFragment.RESULT, this) { _, _ -> renderGreeting() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val column: LinearLayout = HomeUi.column(context)
        content = column
        column.addView(HomeUi.text(context, "PIXELPALS  /  " + getString(R.string.home_subtitle), 12f))
        heading = HomeUi.text(context, getString(R.string.home_title), 30f).also(column::addView)
        description = HomeUi.text(context, getString(R.string.home_loading), 14f).also(column::addView)
        scene = HomeSceneView(context).also { view ->
            view.id = R.id.companionScene
            view.background = HomeUi.surface(HomeUi.cream, HomeUi.dp(context, 24).toFloat())
            view.clipToOutline = true
            column.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = HomeUi.dp(context, 10) })
            view.onPet = { openCare(CareSceneAction.PET) }
            view.onEditingChanged = { editing ->
                if (editing) webFeeding?.cancel()
                webButton?.isEnabled = !editing && !view.isTravelling && webFeeding?.isBusy != true
                decorationControls?.visibility = if (editing) View.VISIBLE else View.GONE
            }
            view.onObject = { item -> if (view.isEditing) showDecoration(item) else if (item.kind == DecorationKind.DISPLAY) showTreasures() else item.action?.let { openCare(it, item.id) } }
            view.onPlace = { id, x, y ->
                val pet: PetType = view.pet
                if (pet == model.pet.value) model.perform {
                    if (!model.repository.place(pet, id, x, y)) notifyUser(R.string.home_occupied)
                }
            }
        }
        decorationControls = HomeUi.row(context,
            HomeUi.button(context, getString(R.string.home_add_object)) { showDecorations() },
            HomeUi.button(context, getString(R.string.home_done), true) { scene?.isEditing = false }
        ).also { it.visibility = View.GONE; column.addView(it) }
        firstHomeGuide = FirstHomeGuideView(context, { openCare(CareSceneAction.PET) }, ::requestDesktop)
            .also(column::addView)
        greeting = HomeUi.text(context, "", 15f).also(column::addView)
        favorite = HomeUi.text(context, "", 13f).also(column::addView)
        needs = HomeUi.text(context, "", 14f).also(column::addView)
        errorText = HomeUi.button(context, getString(R.string.home_error)) { model.error.value = false; model.refresh(); currentPet = null; bindPet(model.pet.value) }
            .also { it.visibility = View.GONE; column.addView(it) }
        careButton = HomeUi.button(context, getString(R.string.home_care), true) { openCare() }.also { it.id = R.id.companionCare; column.addView(it) }
        treeButton = HomeUi.button(context, getString(R.string.home_explore_tree)) { scene?.exploreTree() }.also {
            it.visibility = View.GONE
            column.addView(it)
        }
        webButton = HomeUi.button(context, getString(R.string.home_tela_feed)) { webFeeding?.request(-1) }.also {
            it.visibility = View.GONE
            column.addView(it)
        }
        column.addView(HomeUi.row(context,
            HomeUi.button(context, getString(R.string.home_decorate)) { showDecorations() },
            HomeUi.button(context, getString(R.string.home_share)) { sharePostcard() }.also { postcardButton = it }))
        travel = HomeUi.button(context, getString(R.string.home_desktop)) { requestDesktop() }.also(column::addView)
        column.addView(HomeUi.button(context, getString(R.string.home_choose_pet)) { (activity as? RootNavigator)?.navigate(PixelPalsDestination.PETS) })
        column.addView(HomeUi.row(context,
            HomeUi.button(context, getString(R.string.home_rename)) { showAdoption() },
            HomeUi.button(context, getString(R.string.home_settings)) { startActivity(Intent(context, CompanionSettingsActivity::class.java)) }))
        panel = CareScenePanel(context).also {
            it.visibility = View.GONE
            it.onResult = { result ->
                if (result is CareSceneResult.Completed) {
                    CompanionPreferences(requireContext()).completeFirstHomeCare((careModel?.pet ?: model.pet.value).name.lowercase())
                    firstHomeGuide?.render(lastWorld.home, scene?.isTravelling == true)
                    lastWorld.home?.let { home ->
                        PlayReviewLauncher(requireContext()).maybeLaunch(
                            requireActivity(),
                            ReviewPromptInput(
                                moment = ReviewMoment.CARE_STREAK,
                                adoptedAt = home.adoptedAt,
                                bond = result.after.bond,
                                careStreakDays = result.after.careStreakDays,
                                eventAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
                val objectId = pendingCareObject
                pendingCareObject = null
                if (result is CareSceneResult.Completed && objectId != null) {
                    val caredPet = careModel?.pet ?: model.pet.value
                    model.perform { model.repository.recordObjectUse(caredPet, objectId) }
                } else model.refresh()
            }
            it.onClose = { closeCare(); scene?.let(::revealContent) }
            column.addView(it, 5)
        }
        return ScrollView(context).apply { isFillViewport = true; addView(column) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit {
        super.onViewCreated(view, savedInstanceState)
        webFeeding = TelaWebFeeding(viewLifecycleOwner.lifecycleScope, AppServices.applicationScope,
            AppServices.careScenes(requireContext()),
            beginHunt = { index -> scene?.huntFly(index) == true },
            finishHunt = { success -> scene?.finishWebMeal(success) },
            setBusy = { busy -> webButton?.isEnabled = !busy && scene?.isTravelling != true && scene?.isEditing != true },
            onFed = {
                model.refresh(); careModel?.refresh()
                CompanionPreferences(requireContext()).completeFirstHomeCare("tela")
                firstHomeGuide?.render(lastWorld.home, false)
                notifyUser(R.string.home_tela_fed)
            },
            onError = { notifyUser(R.string.home_tela_feed_unavailable) })
        scene?.onWebHuntRequested = { index -> webFeeding?.request(index) }
        scene?.onWebMealReady = { webFeeding?.complete() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { model.pet.collect { bindPet(it) } }
                launch { model.world.collect { render(it) } }
                launch { model.status.collect { snapshot ->
                    if (snapshot != null) {
                        needs?.text = getString(R.string.home_needs, snapshot.hunger, snapshot.energy, snapshot.hygiene, snapshot.bond)
                        scene?.bond = snapshot.bond
                        scene?.energy = snapshot.energy
                        scene?.isUnwell = snapshot.condition == com.pixelpals.app.core.care.PetCondition.SICK ||
                            snapshot.condition == com.pixelpals.app.core.care.PetCondition.RECOVERING
                        renderGreeting()
                    }
                } }
                launch { model.error.collect { errorText?.visibility = if (it) View.VISIBLE else View.GONE } }
                launch { while (isActive) { model.refresh(); delay(30_000) } }
            }
        }
    }

    private fun offerIntroduction(home: com.pixelpals.app.database.CompanionHomeEntity): Unit {
        val preferences = CompanionPreferences(requireContext())
        if (preferences.hasSeenIntroduction || offeredIntroduction) return
        val selection = SelectedPetStore(requireContext())
        val existing: Boolean = selection.hasSavedSelection() || selection.getSelectedAt() != null ||
            home.adoptedAt > 0L || home.nickname.isNotBlank()
        offeredIntroduction = true
        introductionDialog = AlertDialog.Builder(requireContext()).setTitle(R.string.home_title)
            .setMessage(if (existing) R.string.home_welcome else R.string.home_introduction)
            .setPositiveButton(R.string.home_done) { _, _ -> acknowledgeIntroduction(home, existing, true) }
            .create().also { dialog ->
                dialog.setOnCancelListener { acknowledgeIntroduction(home, existing, false) }
                dialog.show()
            }
    }

    private fun acknowledgeIntroduction(home: com.pixelpals.app.database.CompanionHomeEntity, existing: Boolean, introduceUser: Boolean): Unit {
        val preferences = CompanionPreferences(requireContext())
        preferences.hasSeenIntroduction = true
        if (existing) return
        preferences.beginFirstHome(home.petId)
        val pet = PetType.entries.firstOrNull { it.name.equals(home.petId, ignoreCase = true) } ?: model.pet.value
        val petName = getString(pet.displayNameResId)
        model.perform { model.repository.adopt(pet, petName) }
        firstHomeGuide?.render(home, false)
        if (introduceUser && childFragmentManager.findFragmentByTag(UserNameDialogFragment.TAG) == null) {
            UserNameDialogFragment.create().show(childFragmentManager, UserNameDialogFragment.TAG)
        }
    }

    private fun bindPet(pet: PetType): Unit {
        if (currentPet == pet) return
        webFeeding?.cancel()
        closeCare()
        careModel?.setRoomVisible(false)
        currentPet = pet
        careModel = ViewModelProvider(this, CareSceneViewModel.Factory(requireActivity().application, pet, CareSceneOrigin.ROOM))
            .get("home_care_${pet.name}", CareSceneViewModel::class.java)
        careModel?.let { panel?.bind(it); it.setRoomVisible(true) }
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try { scene?.loadPet(pet)
            } catch (exception: CancellationException) { throw exception
            } catch (_: Exception) { model.error.value = true }
        }
        loadJob?.invokeOnCompletion { scene?.post { if (isAdded) beginRequestedPlacement() } }
        treeButton?.visibility = if (pet == PetType.GINGER && !CompanionPreferences(requireContext()).reducedMotion) View.VISIBLE else View.GONE
        webButton?.visibility = if (pet == PetType.TELA) View.VISIBLE else View.GONE
        description?.text = getString(CompanionProfiles.forPet(pet).description)
    }

    private fun render(world: CompanionWorld): Unit {
        lastWorld = world
        val home = world.home ?: return
        if (home.petId != model.pet.value.name.lowercase()) return
        offerIntroduction(home)
        val name: String = home.nickname.ifBlank { getString(model.pet.value.displayNameResId) }
        heading?.text = name
        firstHomeGuide?.render(home, world.expedition?.petId == home.petId)
        scene?.apply {
            this.home = home
            placements = world.placements
            environment = HomeEnvironment.entries.firstOrNull { it.name == home.environment } ?: HomeEnvironment.COZY
            isTravelling = world.expedition?.petId == home.petId
            invalidate()
        }
        beginRequestedPlacement()
        scene?.environment?.let { panel?.setHomeEnvironment(it) }
        DecorationCatalog.find(home.favoriteObject)?.let {
            val title = DecorationPresentation.title(requireContext(), it, model.pet.value)
            favorite?.text = resources.getQuantityString(
                R.plurals.home_favorite,
                home.playCount,
                title,
                home.playCount,
            )
        }
        val economy = AppServices.repository(requireContext())
        val cosmetic = economy.getEquippedCosmetic(home.petId)?.let { com.pixelpals.app.data.catalog.CosmeticCatalog.findById(requireContext(), it) }
        scene?.let { CompanionPreview.applyEffect(it, cosmetic?.effect) }
        treasureJob?.cancel()
        val pet = model.pet.value
        scene?.treasure = null
        treasureJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val items = economy.getTreasureCollection(pet).items.filter { it.isDiscovered }
                val chosen = if (home.selectedTreasureId == null) items.firstOrNull()
                    else items.firstOrNull { it.id == home.selectedTreasureId }
                if (model.pet.value == pet && lastWorld.home == home) {
                    scene?.treasure = chosen?.emoji
                    scene?.invalidate()
                }
            } catch (exception: CancellationException) { throw exception
            } catch (_: Exception) { model.error.value = true }
        }
        webButton?.isEnabled = webFeeding?.isBusy != true && scene?.isEditing != true && scene?.isTravelling != true
        careButton?.isEnabled = world.expedition?.petId != home.petId
        if (world.expedition?.petId == home.petId) {
            webFeeding?.cancel()
            webButton?.isEnabled = false
            closeCare()
            travel?.text = getString(R.string.adventures_title)
            travel?.setOnClickListener { (activity as? RootNavigator)?.navigate(PixelPalsDestination.ADVENTURES) }
        } else {
            travel?.text = getString(R.string.home_desktop)
            travel?.setOnClickListener { requestDesktop() }
        }
        renderGreeting()
    }

    private fun renderGreeting(): Unit {
        val name: String = lastWorld.home?.nickname?.ifBlank { null } ?: getString(model.pet.value.displayNameResId)
        val bond: Int = model.status.value?.bond ?: 0
        greeting?.text = if (scene?.isTravelling == true) getString(R.string.adventure_travel, name,
            getString(ExpeditionDestination.find(lastWorld.expedition?.destination.orEmpty())?.title ?: R.string.adventures_title))
        else CompanionGreetings.home(requireContext(), name, CompanionPreferences(requireContext()).userName, bond)
    }

    private fun openCare(action: CareSceneAction? = null, objectId: String? = null): Unit {
        if (lastWorld.expedition?.petId == model.pet.value.name.lowercase()) return
        webFeeding?.cancel()
        pendingCareObject = objectId
        isCareOpen = true
        scene?.visibility = View.GONE
        panel?.visibility = View.VISIBLE
        panel?.resumePresentation()
        careModel?.refresh()
        action?.let { panel?.start(it) }
        panel?.let(::revealContent)
    }

    private fun closeCare(): Unit {
        pendingCareObject = null
        isCareOpen = false
        panel?.pausePresentation()
        panel?.visibility = View.GONE
        scene?.visibility = View.VISIBLE
        scene?.resume()
    }

    private fun showAdoption(): Unit {
        if (!isAdded || childFragmentManager.isStateSaved || childFragmentManager.findFragmentByTag(PetNameDialogFragment.TAG) != null) return
        PetNameDialogFragment.create(model.pet.value, lastWorld.home?.nickname.orEmpty())
            .show(childFragmentManager, PetNameDialogFragment.TAG)
    }

    private fun showDecorations(): Unit {
        scene?.isEditing = true; scene?.invalidate()
        scene?.let(::revealContent)
        val pet: PetType = model.pet.value
        val items: List<Decoration> = lastWorld.inventory.mapNotNull { DecorationCatalog.find(it.decorationId) }
        val titles: Array<String> = arrayOf(getString(R.string.home_environment), getString(R.string.home_exhibit_treasure)) +
            items.map { DecorationPresentation.title(requireContext(), it, pet) }.toTypedArray()
        AlertDialog.Builder(requireContext()).setTitle(R.string.home_decorate).setItems(titles) { _, index ->
            if (model.pet.value == pet) {
                when (index) {
                    0 -> showEnvironments()
                    1 -> showTreasures()
                    else -> items.getOrNull(index - 2)?.let(::showDecoration)
                }
            }
        }.setNeutralButton(R.string.home_decoration_shop) { _, _ -> (activity as? RootNavigator)?.navigate(PixelPalsDestination.STORE, StoreSection.DECORATIONS) }
            .setNegativeButton(R.string.home_done) { _, _ -> scene?.isEditing = false; scene?.invalidate() }.show()
        notifyUser(R.string.home_grid_hint)
    }

    private fun showTreasures() {
        val pet = model.pet.value
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val items = AppServices.repository(context).getTreasureCollection(pet).items.filter { it.isDiscovered }
                if (!isAdded || model.pet.value != pet) return@launch
                if (items.isEmpty()) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(context).setTitle(R.string.home_exhibit_treasure)
                        .setMessage(R.string.home_exhibit_empty).setPositiveButton(android.R.string.ok, null).show()
                    return@launch
                }
                val ids = listOf<String?>(null, "") + items.map { it.id }
                val labels = listOf(getString(R.string.home_exhibit_auto), getString(R.string.home_exhibit_none)) +
                    items.map { if (android.graphics.Paint().hasGlyph(it.emoji)) "${it.emoji} ${it.name}" else it.name }
                val checked = ids.indexOf(lastWorld.home?.selectedTreasureId)
                com.google.android.material.dialog.MaterialAlertDialogBuilder(context).setTitle(R.string.home_exhibit_treasure)
                    .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, index ->
                        if (model.pet.value == pet) model.perform {
                            check(model.repository.selectTreasure(pet, ids[index]))
                        }
                        dialog.dismiss()
                    }.setNegativeButton(android.R.string.cancel, null).show()
            } catch (exception: CancellationException) { throw exception
            } catch (_: Exception) { model.error.value = true }
        }
    }

    private fun showEnvironments(): Unit {
        val pet: PetType = model.pet.value
        AlertDialog.Builder(requireContext()).setTitle(R.string.home_environment)
            .setItems(HomeEnvironment.entries.map { getString(it.title) }.toTypedArray()) { _, index ->
                model.perform { model.repository.setEnvironment(pet, HomeEnvironment.entries[index]) }
            }.show()
    }

    private fun beginRequestedPlacement(): Unit {
        val id: String = requestedPlacement ?: return
        val view: HomeSceneView = scene ?: return
        val pet: PetType = model.pet.value
        if (lastWorld.home?.petId != pet.name.lowercase() || view.pet != pet || loadJob?.isActive == true) return
        if (requestedPlacementPet != pet.name || DecorationCatalog.find(id) == null) {
            requestedPlacement = null
            requestedPlacementPet = null
            return
        }
        if (lastWorld.inventory.none { it.decorationId == id }) return
        requestedPlacement = null
        requestedPlacementPet = null
        view.beginPlacement(id)
        revealContent(view)
        notifyUser(R.string.home_touch_place_hint)
    }

    private fun showDecoration(item: Decoration): Unit {
        val pet: PetType = model.pet.value
        DecorationDialogs.show(requireContext(), item, lastWorld, pet, model,
            onUse = { action -> if (model.pet.value == pet) openCare(action, item.id) },
            onPlaceByTouch = { if (model.pet.value == pet && scene?.pet == pet) {
                scene?.let { it.beginPlacement(item.id); revealContent(it) }
                notifyUser(R.string.home_touch_place_hint)
            } })
    }

    /** Actions below the room must bring their destination into view, including at large text sizes. */
    private fun revealContent(target: View): Unit {
        target.doOnLayout {
            val scroll: ScrollView = view as? ScrollView ?: return@doOnLayout
            if (target.parent !== content || target.visibility != View.VISIBLE) return@doOnLayout
            val bounds = android.graphics.Rect()
            target.getDrawingRect(bounds)
            scroll.offsetDescendantRectToMyCoords(target, bounds)
            if (CompanionPreferences(requireContext()).reducedMotion) scroll.scrollTo(0, bounds.top)
            else scroll.smoothScrollTo(0, bounds.top)
        }
    }

    private fun requestDesktop(): Unit {
        if (!Settings.canDrawOverlays(requireContext())) {
            AlertDialog.Builder(requireContext()).setMessage(R.string.home_permission)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.home_permission_open) { _, _ ->
                    desktopPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${requireContext().packageName}")))
                }.show()
            return
        }
        PetService.requestPetChange(requireContext(), model.pet.value)
        val preferences = CompanionPreferences(requireContext())
        if (preferences.firstHomePet == model.pet.value.name.lowercase() && preferences.hasCompletedFirstHomeCare) preferences.finishFirstHome()
        requireActivity().moveTaskToBack(true)
    }

    private fun sharePostcard(): Unit {
        val view: HomeSceneView = scene ?: return
        if (postcardJob?.isActive == true) return
        postcardButton?.isEnabled = false
        postcardJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                PostcardExporter.share(requireContext(), view,
                    getString(R.string.home_postcard, lastWorld.home?.nickname?.ifBlank { null } ?: getString(model.pet.value.displayNameResId)))
            } catch (exception: CancellationException) { throw exception
            } catch (_: Exception) { model.error.value = true
            } finally { postcardButton?.isEnabled = true }
        }
    }

    private fun notifyUser(resource: Int): Unit { if (isAdded) Toast.makeText(requireContext(), resource, Toast.LENGTH_LONG).show() }
    override fun onResume(): Unit { super.onResume(); model.refresh(); renderGreeting(); careModel?.setRoomVisible(true); scene?.resume() }
    override fun onPause(): Unit { desktopPermissionJob?.cancel(); postcardJob?.cancel(); webFeeding?.cancel(); closeCare(); scene?.pause(); careModel?.setRoomVisible(false); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle): Unit { outState.putString("store_placement", requestedPlacement); outState.putString("store_placement_pet", requestedPlacementPet); super.onSaveInstanceState(outState) }
    override fun onDestroyView(): Unit {
        introductionDialog?.dismiss()
        introductionDialog = null
        offeredIntroduction = false
        loadJob?.cancel(); treasureJob?.cancel(); closeCare(); scene?.pause(); careModel?.setRoomVisible(false)
        webFeeding?.cancel(); webFeeding = null; webButton = null
        treeButton = null
        firstHomeGuide = null
        scene = null; panel = null; content = null; heading = null; description = null; needs = null
        postcardJob?.cancel(); postcardJob = null; postcardButton = null
        decorationControls = null; greeting = null; favorite = null; travel = null; careButton = null; errorText = null; currentPet = null
        super.onDestroyView()
    }
}

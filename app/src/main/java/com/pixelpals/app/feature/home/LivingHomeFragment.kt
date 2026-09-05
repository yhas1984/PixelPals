package com.pixelpals.app.feature.home

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.text.InputFilter
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.pixelpals.app.*
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.feature.care.*
import com.pixelpals.app.navigation.*
import kotlinx.coroutines.*

open class LivingHomeFragment : Fragment() {
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
    private var careButton: Button? = null
    private var errorText: Button? = null
    private var currentPet: PetType? = null
    private var loadJob: Job? = null
    private var isCareOpen: Boolean = false
    private var hasPendingDesktop: Boolean = false
    private var lastWorld: CompanionWorld = CompanionWorld()

    override fun onCreate(savedInstanceState: Bundle?): Unit {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[CompanionViewModel::class.java]
        hasPendingDesktop = savedInstanceState?.getBoolean("desktop_pending") ?: false
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
            view.onObject = { item -> if (view.isEditing) showDecoration(item) else item.action?.let(::openCare) }
            view.onPlace = { id, x, y -> model.perform {
                if (!model.repository.place(model.pet.value, id, x, y)) notifyUser(R.string.home_occupied)
            } }
        }
        greeting = HomeUi.text(context, "", 15f).also(column::addView)
        favorite = HomeUi.text(context, "", 13f).also(column::addView)
        needs = HomeUi.text(context, "", 14f).also(column::addView)
        errorText = HomeUi.button(context, getString(R.string.home_error)) { model.error.value = false; model.refresh(); currentPet = null; bindPet(model.pet.value) }
            .also { it.visibility = View.GONE; column.addView(it) }
        careButton = HomeUi.button(context, getString(R.string.home_care), true) { openCare() }.also { it.id = R.id.companionCare; column.addView(it) }
        column.addView(HomeUi.row(context,
            HomeUi.button(context, getString(R.string.home_decorate)) { showDecorations() },
            HomeUi.button(context, getString(R.string.home_share)) { sharePostcard() }))
        travel = HomeUi.button(context, getString(R.string.home_desktop)) { requestDesktop() }.also(column::addView)
        column.addView(HomeUi.button(context, getString(R.string.home_choose_pet)) { (activity as? RootNavigator)?.navigate(PixelPalsDestination.PETS) })
        column.addView(HomeUi.row(context,
            HomeUi.button(context, getString(R.string.home_rename)) { showAdoption() },
            HomeUi.button(context, getString(R.string.home_settings)) { startActivity(Intent(context, CompanionSettingsActivity::class.java)) }))
        panel = CareScenePanel(context).also {
            it.visibility = View.GONE
            it.onResult = { model.refresh() }
            it.onClose = { closeCare() }
            column.addView(it, 5)
        }
        return ScrollView(context).apply { isFillViewport = true; addView(column) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { model.pet.collect { bindPet(it) } }
                launch { model.world.collect { render(it) } }
                launch { model.status.collect { snapshot ->
                    if (snapshot != null) {
                        needs?.text = getString(R.string.home_needs, snapshot.hunger, snapshot.energy, snapshot.hygiene, snapshot.bond)
                        scene?.bond = snapshot.bond
                        renderGreeting()
                    }
                } }
                launch { model.error.collect { errorText?.visibility = if (it) View.VISIBLE else View.GONE } }
                launch { while (isActive) { model.refresh(); delay(30_000) } }
            }
        }
        val preferences: CompanionPreferences = CompanionPreferences(requireContext())
        if (!preferences.hasSeenIntroduction) {
            preferences.hasSeenIntroduction = true
            AlertDialog.Builder(requireContext()).setTitle(R.string.home_title)
                .setMessage(if (SelectedPetStore(requireContext()).getSelectedAt() != null) R.string.home_welcome else R.string.home_introduction)
                .setPositiveButton(R.string.home_done) { _, _ -> showAdoption() }.show()
        }
    }

    private fun bindPet(pet: PetType): Unit {
        if (currentPet == pet) return
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
        description?.text = getString(CompanionProfiles.forPet(pet).description)
    }

    private fun render(world: CompanionWorld): Unit {
        lastWorld = world
        val home = world.home ?: return
        if (home.petId != model.pet.value.name.lowercase()) return
        val name: String = home.nickname.ifBlank { getString(model.pet.value.displayNameResId) }
        heading?.text = name
        scene?.apply {
            this.home = home
            placements = world.placements
            environment = HomeEnvironment.entries.firstOrNull { it.name == home.environment } ?: HomeEnvironment.COZY
            isTravelling = world.expedition?.petId == home.petId
            invalidate()
        }
        scene?.environment?.let { panel?.setHomeEnvironment(it) }
        DecorationCatalog.find(home.favoriteObject)?.let { favorite?.text = getString(R.string.home_favorite, getString(it.title), home.playCount) }
        val economy = AppServices.repository(requireContext())
        val cosmetic = economy.getEquippedCosmetic(home.petId)?.let { com.pixelpals.app.data.catalog.CosmeticCatalog.findById(requireContext(), it) }
        scene?.let { CompanionPreview.applyEffect(it, cosmetic?.effect) }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                scene?.treasure = AppServices.repository(requireContext()).getTreasureCollection(model.pet.value).items.firstOrNull { it.isDiscovered }?.emoji
                scene?.invalidate()
            } catch (exception: CancellationException) { throw exception
            } catch (_: Exception) { model.error.value = true }
        }
        careButton?.isEnabled = world.expedition?.petId != home.petId
        if (world.expedition?.petId == home.petId) {
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
        val resource: Int = if (bond >= 50) R.string.home_greeting_close else if (bond >= 15) R.string.home_greeting_friend else R.string.home_greeting_new
        greeting?.text = if (scene?.isTravelling == true) getString(R.string.adventure_travel, name,
            getString(ExpeditionDestination.find(lastWorld.expedition?.destination.orEmpty())?.title ?: R.string.adventures_title))
        else getString(resource, name)
    }

    private fun openCare(action: CareSceneAction? = null): Unit {
        if (lastWorld.expedition?.petId == model.pet.value.name.lowercase()) return
        isCareOpen = true
        scene?.visibility = View.GONE
        panel?.visibility = View.VISIBLE
        panel?.resumePresentation()
        careModel?.refresh()
        action?.let { panel?.start(it) }
    }

    private fun closeCare(): Unit {
        isCareOpen = false
        panel?.pausePresentation()
        panel?.visibility = View.GONE
        scene?.visibility = View.VISIBLE
        scene?.resume()
    }

    private fun showAdoption(): Unit {
        if (!isAdded) return
        val edit: EditText = EditText(requireContext()).apply {
            hint = getString(R.string.home_name_hint); setSingleLine(); filters = arrayOf(InputFilter.LengthFilter(24))
            setText(lastWorld.home?.nickname.orEmpty())
            setPadding(HomeUi.dp(context, 24), HomeUi.dp(context, 16), HomeUi.dp(context, 24), HomeUi.dp(context, 16))
        }
        val dialog: AlertDialog = AlertDialog.Builder(requireContext()).setTitle(R.string.home_name).setView(edit)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.home_adopt, null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (edit.text.toString().isBlank()) { edit.error = getString(R.string.home_name_hint); return@setOnClickListener }
            val pet: PetType = model.pet.value
            model.perform { model.repository.adopt(pet, edit.text.toString()) }
            dialog.dismiss()
        } }
        dialog.show()
    }

    private fun showDecorations(): Unit {
        scene?.isEditing = true; scene?.invalidate()
        val titles: Array<String> = arrayOf(getString(R.string.home_environment)) +
            lastWorld.inventory.mapNotNull { DecorationCatalog.find(it.decorationId)?.let { item -> getString(item.title) } }.toTypedArray()
        AlertDialog.Builder(requireContext()).setTitle(R.string.home_decorate).setItems(titles) { _, index ->
            if (index == 0) showEnvironments() else lastWorld.inventory.getOrNull(index - 1)?.let { owned ->
                DecorationCatalog.find(owned.decorationId)?.let(::showDecoration)
            }
        }.setNeutralButton(R.string.home_decoration_shop) { _, _ -> (activity as? RootNavigator)?.navigate(PixelPalsDestination.STORE, StoreSection.DECORATIONS) }
            .setNegativeButton(R.string.home_done) { _, _ -> scene?.isEditing = false; scene?.invalidate() }.show()
        notifyUser(R.string.home_grid_hint)
    }

    private fun showEnvironments(): Unit {
        AlertDialog.Builder(requireContext()).setTitle(R.string.home_environment)
            .setItems(HomeEnvironment.entries.map { getString(it.title) }.toTypedArray()) { _, index ->
                model.perform { model.repository.setEnvironment(model.pet.value, HomeEnvironment.entries[index]) }
            }.show()
    }

    private fun showDecoration(item: Decoration): Unit {
        DecorationDialogs.show(requireContext(), item, lastWorld, model.pet.value, model,
            onUse = { action -> model.perform { model.repository.chooseObject(model.pet.value, item.id, false) }; openCare(action) })
    }

    private fun requestDesktop(): Unit {
        if (!Settings.canDrawOverlays(requireContext())) {
            AlertDialog.Builder(requireContext()).setMessage(R.string.home_permission)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.home_permission_open) { _, _ ->
                    hasPendingDesktop = true
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireContext().packageName}")))
                }.show()
            return
        }
        PetService.requestPetChange(requireContext(), model.pet.value)
        requireActivity().moveTaskToBack(true)
    }

    private fun sharePostcard(): Unit {
        val view: HomeSceneView = scene ?: return
        model.perform { PostcardExporter.share(requireContext(), view,
            getString(R.string.home_postcard, lastWorld.home?.nickname?.ifBlank { null } ?: getString(model.pet.value.displayNameResId))) }
    }

    private fun notifyUser(resource: Int): Unit { if (isAdded) Toast.makeText(requireContext(), resource, Toast.LENGTH_LONG).show() }
    override fun onResume(): Unit { super.onResume(); model.refresh(); careModel?.setRoomVisible(true); scene?.resume()
        if (hasPendingDesktop) { hasPendingDesktop = false; if (Settings.canDrawOverlays(requireContext())) requestDesktop() } }
    override fun onPause(): Unit { closeCare(); scene?.pause(); careModel?.setRoomVisible(false); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle): Unit { outState.putBoolean("desktop_pending", hasPendingDesktop); super.onSaveInstanceState(outState) }
    override fun onDestroyView(): Unit {
        loadJob?.cancel(); closeCare(); scene?.pause(); careModel?.setRoomVisible(false)
        scene = null; panel = null; content = null; heading = null; description = null; needs = null
        greeting = null; favorite = null; travel = null; careButton = null; errorText = null; currentPet = null
        super.onDestroyView()
    }
}

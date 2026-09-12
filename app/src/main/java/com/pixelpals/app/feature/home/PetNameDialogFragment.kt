package com.pixelpals.app.feature.home

import android.app.Dialog
import android.os.Bundle
import android.text.InputFilter
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The pet identity and uncommitted draft belong to the dialog, including after recreation. */
class PetNameDialogFragment : DialogFragment() {
    private var nameInput: EditText? = null
    private var saving: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val input = EditText(requireContext()).apply {
            id = R.id.petNameDraft
            hint = getString(R.string.home_name_hint)
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(24))
            imeOptions = EditorInfo.IME_ACTION_DONE
            setText(savedInstanceState?.getString(DRAFT) ?: requireArguments().getString(DRAFT).orEmpty())
            setSelection((savedInstanceState?.getInt("cursor", length()) ?: length()).coerceIn(0, length()))
            setPadding(HomeUi.dp(context, 24), HomeUi.dp(context, 16), HomeUi.dp(context, 24), HomeUi.dp(context, 16))
        }
        nameInput = input
        return AlertDialog.Builder(requireContext()).setTitle(R.string.home_name).setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.home_save_name, null).create()
    }

    override fun onStart() {
        super.onStart()
        val alertDialog: AlertDialog = requireDialog() as AlertDialog
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { saveName() }
        nameInput?.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { saveName(); true } else false
        }
        setSaving(saving)
    }

    private fun saveName() {
        if (saving) return
        val input: EditText = nameInput ?: return
        val name: String = input.text.toString().trim()
        if (name.isEmpty()) { input.error = getString(R.string.home_name_hint); return }
        val pet: PetType = PetType.valueOf(requireNotNull(requireArguments().getString(PET)))
        val repository: CompanionRepository = AppServices.companions(requireContext())
        setSaving(true)
        lifecycleScope.launch {
            try {
                repository.adopt(pet, name)
                parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putString(PET, pet.name) })
                // Persistence has completed even if Android saved UI state while Room was writing.
                dismissAllowingStateLoss()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { nameInput?.error = getString(R.string.home_error)
            } finally { setSaving(false) }
        }
    }

    private fun setSaving(value: Boolean) {
        saving = value
        isCancelable = !value
        nameInput?.isEnabled = !value
        (dialog as? AlertDialog)?.let {
            it.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                isEnabled = !value
                setText(if (value) R.string.home_saving_name else R.string.home_save_name)
            }
            it.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !value
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(DRAFT, nameInput?.text?.toString())
        outState.putInt("cursor", nameInput?.selectionStart ?: 0)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        nameInput = null
        super.onDestroyView()
    }

    companion object {
        const val TAG: String = "pet_name_dialog"
        const val RESULT: String = "pet_name_saved"
        private const val PET: String = "pet"
        private const val DRAFT: String = "draft"
        fun create(pet: PetType, nickname: String): PetNameDialogFragment = PetNameDialogFragment().apply {
            arguments = Bundle().apply { putString(PET, pet.name); putString(DRAFT, nickname) }
        }
    }
}

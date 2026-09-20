package com.pixelpals.app.feature.home

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.pixelpals.app.R

/** Dialog for the person's optional local name; it never asks for or changes the pet name. */
class UserNameDialogFragment : DialogFragment() {
    private var nameInput: EditText? = null
    private var hasSavedName: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val preferences: CompanionPreferences = CompanionPreferences(requireContext())
        hasSavedName = preferences.userName.isNotEmpty()
        val input: EditText = EditText(requireContext()).apply {
            id = R.id.userNameDraft
            hint = getString(R.string.identity_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_DONE
            val draft: String = savedInstanceState?.getString(DRAFT) ?: preferences.userName
            setText(draft)
            val cursor: Int = (savedInstanceState?.getInt(CURSOR) ?: length()).coerceIn(0, length())
            setSelection(cursor)
            setPadding(HomeUi.dp(context, 24), HomeUi.dp(context, 16), HomeUi.dp(context, 24), HomeUi.dp(context, 16))
        }
        nameInput = input
        return AlertDialog.Builder(requireContext()).setTitle(R.string.identity_name_title).setMessage(R.string.identity_name_prompt)
            .setView(input)
            .setNegativeButton(R.string.identity_not_now, null)
            .apply {
                if (hasSavedName) setNeutralButton(R.string.identity_remove_name, null)
            }
            .setPositiveButton(R.string.identity_save_name, null)
            .create()
    }

    override fun onStart(): Unit {
        super.onStart()
        val alertDialog: AlertDialog = requireDialog() as AlertDialog
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { saveName() }
        alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { removeName() }
        nameInput?.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { saveName(); true } else false
        }
    }

    private fun saveName(): Unit {
        val input: EditText = nameInput ?: return
        val name: String = UserNameRules.normalize(input.text.toString())
        if (name.isEmpty()) {
            input.error = getString(R.string.identity_name_error)
            return
        }
        CompanionPreferences(requireContext()).userName = name
        emitResult(name)
        dismissAllowingStateLoss()
    }

    private fun removeName(): Unit {
        CompanionPreferences(requireContext()).userName = ""
        emitResult("")
        dismissAllowingStateLoss()
    }

    private fun emitResult(name: String): Unit {
        parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putString(USER_NAME, name) })
    }

    override fun onSaveInstanceState(outState: Bundle): Unit {
        outState.putString(DRAFT, nameInput?.text?.toString().orEmpty())
        outState.putInt(CURSOR, nameInput?.selectionStart ?: 0)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView(): Unit {
        nameInput = null
        super.onDestroyView()
    }

    companion object {
        const val TAG: String = "user_name_dialog"
        const val RESULT: String = "user_name_saved"
        const val USER_NAME: String = "user_name"
        private const val DRAFT: String = "draft"
        private const val CURSOR: String = "cursor"

        fun create(): UserNameDialogFragment = UserNameDialogFragment()
    }
}

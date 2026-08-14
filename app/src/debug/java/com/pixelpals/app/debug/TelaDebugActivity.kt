package com.pixelpals.app.debug

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pixelpals.app.PetService

class TelaDebugActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * density).toInt(), (32 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())
        }
        root.addView(TextView(this).apply {
            text = getString(com.pixelpals.app.R.string.debug_tela_title)
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = getString(com.pixelpals.app.R.string.debug_tela_hint)
            textSize = 16f
            setPadding(0, (16 * density).toInt(), 0, (16 * density).toInt())
        })
        root.addView(button(getString(com.pixelpals.app.R.string.debug_tela_permission)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(button(getString(com.pixelpals.app.R.string.debug_tela_activate)) {
            PetService.requestPetChange(this, com.pixelpals.app.core.domain.PetType.TELA)
        })
        root.addView(button(getString(com.pixelpals.app.R.string.debug_tela_web_test)) {
            PetService.requestTelaWebTest(this)
        })
        root.addView(button(getString(com.pixelpals.app.R.string.debug_tela_corner_web_test)) {
            PetService.requestTelaCornerWebTest(this)
        })
        root.addView(button(getString(com.pixelpals.app.R.string.debug_tela_stop)) {
            PetService.stopPet(this)
        })
        setContentView(root)
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }
}

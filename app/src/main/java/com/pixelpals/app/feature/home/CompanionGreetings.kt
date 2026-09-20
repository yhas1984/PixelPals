package com.pixelpals.app.feature.home

import android.content.Context
import androidx.annotation.StringRes
import com.pixelpals.app.R

/** Builds local, personalised greetings shown in the companion home. */
object CompanionGreetings {
    fun home(context: Context, petName: String, userName: String, bond: Int): String {
        val cleanUserName: String = userName.trim()
        val cleanPetName: String = petName.trim()
        val resource: Int = homeGreetingResource(bond, cleanUserName.isNotEmpty())
        return if (cleanUserName.isEmpty()) {
            context.getString(resource, cleanPetName)
        } else {
            context.getString(resource, cleanUserName, cleanPetName)
        }
    }
}

@StringRes
internal fun homeGreetingResource(bond: Int, hasUserName: Boolean): Int {
    if (!hasUserName) {
        return when {
            bond >= 50 -> R.string.home_greeting_close
            bond >= 15 -> R.string.home_greeting_friend
            else -> R.string.home_greeting_new
        }
    }
    return when {
        bond >= 50 -> R.string.home_greeting_named_close
        bond >= 15 -> R.string.home_greeting_named_friend
        else -> R.string.home_greeting_named_new
    }
}

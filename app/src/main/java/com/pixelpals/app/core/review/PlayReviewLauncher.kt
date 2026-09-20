package com.pixelpals.app.core.review

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.pixelpals.app.BuildConfig

interface ReviewPromptStateStore {
    fun read(): ReviewPromptState
    fun recordRequest(at: Long, versionCode: Long)
}

class SharedPreferencesReviewPromptStateStore(context: Context) : ReviewPromptStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun read(): ReviewPromptState = ReviewPromptState(
        lastRequestedAt = preferences.getLong(KEY_LAST_REQUESTED_AT, 0L),
        lastVersionCode = preferences.getLong(KEY_LAST_VERSION_CODE, 0L),
        requestCount = preferences.getInt(KEY_REQUEST_COUNT, 0),
    )

    override fun recordRequest(at: Long, versionCode: Long) {
        val count: Int = preferences.getInt(KEY_REQUEST_COUNT, 0)
        preferences.edit {
            putLong(KEY_LAST_REQUESTED_AT, at)
            putLong(KEY_LAST_VERSION_CODE, versionCode)
            putInt(KEY_REQUEST_COUNT, count + 1)
        }
    }

    private companion object {
        const val PREFERENCES: String = "pixelpals_review_prompt"
        const val KEY_LAST_REQUESTED_AT: String = "last_requested_at"
        const val KEY_LAST_VERSION_CODE: String = "last_version_code"
        const val KEY_REQUEST_COUNT: String = "request_count"
    }
}

class PendingReviewMomentStore(context: Context) {
    data class PendingMoment(val moment: ReviewMoment, val petId: String, val eventAt: Long)

    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun record(moment: ReviewMoment, petId: String, eventAt: Long = System.currentTimeMillis()) {
        preferences.edit {
            putString(KEY_MOMENT, moment.name)
            putString(KEY_PET_ID, petId)
            putLong(KEY_EVENT_AT, eventAt)
        }
    }

    fun consume(): PendingMoment? {
        val moment: ReviewMoment = preferences.getString(KEY_MOMENT, null)
            ?.let { value -> ReviewMoment.entries.firstOrNull { it.name == value } }
            ?: return null
        val petId: String = preferences.getString(KEY_PET_ID, null) ?: return null
        val eventAt: Long = preferences.getLong(KEY_EVENT_AT, 0L)
        preferences.edit { clear() }
        return PendingMoment(moment, petId, eventAt)
    }

    private companion object {
        const val PREFERENCES: String = "pixelpals_pending_review_moment"
        const val KEY_MOMENT: String = "moment"
        const val KEY_PET_ID: String = "pet_id"
        const val KEY_EVENT_AT: String = "event_at"
    }
}

class PlayReviewLauncher(
    context: Context,
    private val policy: ReviewPromptPolicy = ReviewPromptPolicy(),
    private val stateStore: ReviewPromptStateStore = SharedPreferencesReviewPromptStateStore(context),
    private val manager: ReviewManager = ReviewManagerFactory.create(context.applicationContext),
    private val versionCode: Long = BuildConfig.VERSION_CODE.toLong(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun maybeLaunch(activity: Activity, input: ReviewPromptInput): Boolean {
        val now: Long = clock()
        if (!policy.isEligible(input, stateStore.read(), now, versionCode)) return false
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) return@addOnCompleteListener
            stateStore.recordRequest(clock(), versionCode)
            manager.launchReviewFlow(activity, request.result)
        }
        return true
    }

    companion object {
        fun openStoreListing(context: Context) {
            val packageName: String = context.packageName.removeSuffix(".debug")
            val marketIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(marketIntent) }.onFailure {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}

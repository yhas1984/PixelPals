package com.pixelpals.app.feature.care

import android.content.Context
import com.pixelpals.app.R
import com.pixelpals.app.core.care.PetCondition
import com.pixelpals.app.core.care.scene.CareSceneResult
import com.pixelpals.app.core.care.scene.CareSceneAction

object CareResultFormatter {
    fun describe(context: Context, result: CareSceneResult.Completed, action: CareSceneAction? = null): String {
        if (result.didWake) return context.getString(R.string.care_scene_awake)
        if (result.before.condition in setOf(PetCondition.SICK, PetCondition.RECOVERING) &&
            result.after.condition == PetCondition.HEALTHY) return context.getString(R.string.care_scene_recovered)
        val changes: List<Pair<Int, Int>> = listOf(
            R.string.care_delta_fullness to result.after.hunger - result.before.hunger,
            R.string.care_delta_energy to result.after.energy - result.before.energy,
            R.string.care_delta_cleanliness to result.after.hygiene - result.before.hygiene,
            R.string.care_delta_bond to result.bondGain,
            R.string.care_delta_coins to result.coinGain,
            R.string.care_delta_health to result.after.health - result.before.health,
            R.string.care_delta_recovery to result.after.recoveryProgress - result.before.recoveryProgress,
        ).filter { it.second != 0 }
        val sharedGame: Boolean = action == CareSceneAction.PLAY
        if (changes.isEmpty()) return context.getString(
            if (sharedGame) R.string.care_scene_played_together else R.string.care_scene_no_changes)
        val details: String = changes.chunked(3).joinToString("\n") { line ->
            line.joinToString(" · ") { (label, delta) -> context.getString(R.string.care_delta_value, context.getString(label), delta) }
        }
        if (!sharedGame) return details
        // A completed game still matters when today's numeric rewards are capped.
        // Explain the effort without dropping negative deltas or inventing gains.
        val lines: MutableList<String> = mutableListOf(context.getString(R.string.care_scene_played_together))
        if (result.after.energy < result.before.energy || result.after.hunger < result.before.hunger)
            lines += context.getString(R.string.care_scene_play_effort)
        lines += details
        return lines.joinToString("\n")
    }
}

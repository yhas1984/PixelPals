package com.pixelpals.app.feature.home

/** Local rules for the optional name the person uses in the companion experience. */
object UserNameRules {
    const val MAX_LENGTH: Int = 24

    fun normalize(value: String): String {
        val withoutControls: String = value.mapNotNull { character: Char ->
            when {
                character.isWhitespace() -> ' '
                character.isISOControl() -> null
                else -> character
            }
        }.joinToString("")
        val collapsedWhitespace: String = withoutControls.replace(Regex("\\s+"), " ").trim()
        return limitCodePoints(collapsedWhitespace)
    }

    private fun limitCodePoints(value: String): String {
        if (value.codePointCount(0, value.length) <= MAX_LENGTH) return value
        val end: Int = value.offsetByCodePoints(0, MAX_LENGTH)
        return value.substring(0, end)
    }
}

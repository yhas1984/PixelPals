package com.pixelpals.app.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class UserNameRulesTest {
    @Test fun normalizesSpacesAndRemovesControlCharacters(): Unit {
        assertEquals("Ana María", UserNameRules.normalize("  Ana\t\nMaría\u0000  "))
    }

    @Test fun limitsByCodePointsWithoutSplittingSurrogatePairs(): Unit {
        val normalized: String = UserNameRules.normalize("😀".repeat(24) + "z")
        assertEquals(24, normalized.codePointCount(0, normalized.length))
        assertEquals("😀", normalized.takeLast(2))
    }

    @Test fun keepsShortNamesAndTurnsWhitespaceOnlyIntoEmpty(): Unit {
        assertEquals("Luz", UserNameRules.normalize("Luz"))
        assertEquals("", UserNameRules.normalize(" \t\n"))
    }
}

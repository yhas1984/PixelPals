package com.pixelpals.app.feature.home

import com.pixelpals.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionGreetingsTest {
    @Test
    fun homeGreetingResourceUsesTheThreeBondLevelsForNamedAndUnnamedUsers(): Unit {
        assertEquals(R.string.home_greeting_new, homeGreetingResource(0, false))
        assertEquals(R.string.home_greeting_friend, homeGreetingResource(15, false))
        assertEquals(R.string.home_greeting_close, homeGreetingResource(50, false))
        assertEquals(R.string.home_greeting_named_new, homeGreetingResource(0, true))
        assertEquals(R.string.home_greeting_named_friend, homeGreetingResource(15, true))
        assertEquals(R.string.home_greeting_named_close, homeGreetingResource(50, true))
    }
}

package com.indiehost.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {

    @Test
    fun `an empty query matches everything`() {
        assertTrue(AppSearch.matches("Any App", "com.example.any", "   "))
    }

    @Test
    fun `matches label and package case insensitively`() {
        assertTrue(AppSearch.matches("Signal", "com.example.SIGNAL", "signal"))
        assertTrue(AppSearch.matches("Signal", "com.example.signal", "EXAMPLE"))
    }

    @Test
    fun `ignores accents and surrounding whitespace`() {
        assertTrue(AppSearch.matches("Café", "com.example.cafe", "  cafe  "))
        assertTrue(AppSearch.matches("Café", "com.example.cafe", "CAFÉ"))
    }

    @Test
    fun `does not match unrelated apps`() {
        assertFalse(AppSearch.matches("Signal", "com.example.signal", "telegram"))
    }
}

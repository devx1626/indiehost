package com.indiehost.launcher

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {

    @Test
    fun `black on white is the maximum ratio`() {
        assertEquals(21.0, Contrast.ratio(Color.White, Color.Black), 0.01)
    }

    @Test
    fun `primary text clears the contrast floor on every surface`() {
        val surfaces = listOf(Indie.Base, Indie.Panel, Indie.Card, Indie.CardRaised)
        for (surface in surfaces) {
            assertTrue("Text on surface $surface", Contrast.ratio(Indie.Text, surface) >= MIN)
            assertTrue("Bright on surface $surface", Contrast.ratio(Indie.TextBright, surface) >= MIN)
            assertTrue("Muted on surface $surface", Contrast.ratio(Indie.TextMuted, surface) >= MIN)
        }
    }

    @Test
    fun `accent and button ink clear the contrast floor`() {
        assertTrue(Contrast.ratio(Indie.Accent, Indie.Card) >= MIN)
        assertTrue(Contrast.ratio(Indie.OnAccent, Indie.Accent) >= MIN)
    }

    private companion object {
        const val MIN = 4.5
    }
}

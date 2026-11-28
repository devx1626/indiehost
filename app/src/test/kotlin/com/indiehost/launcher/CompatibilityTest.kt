package com.indiehost.launcher

import com.indiehost.detect.Engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden test for the supported-engine table. A new engine must update both the
 * detector and this table, so COMPATIBILITY.md cannot silently drift.
 */
class CompatibilityTest {

    @Test
    fun `every detected engine has a documented route`() {
        val documented = mapOf(
            Engine.GAMEMAKER_VM to "Butterscotch shortcut handoff",
            Engine.RPGMAKER_MV to "Offline WebView",
            Engine.RPGMAKER_MZ to "Offline WebView",
            Engine.RENPY to "Detected, not hosted",
            Engine.LOVE to "Detected, not hosted",
            Engine.SCUMMVM to "Detected, not hosted",
            Engine.UNKNOWN to "No route: not a game",
        )

        assertEquals(Engine.values().toSet(), documented.keys)
        assertTrue(documented.values.all { it.isNotBlank() })
    }
}

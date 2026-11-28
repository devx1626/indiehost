package com.indiehost.launcher

import com.indiehost.detect.Engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityPolicyTest {

    private val ready = LauncherCapabilities(
        isHome = true,
        butterscotchInstalled = true,
        hasGameFolder = true,
    )

    @Test
    fun `GameMaker needs the installed runner but not the home role`() {
        assertTrue(CapabilityPolicy.canLaunchGame(Engine.GAMEMAKER_VM, ready))
        assertTrue(
            CapabilityPolicy.canLaunchGame(
                Engine.GAMEMAKER_VM,
                ready.copy(isHome = false),
            ),
        )
        assertFalse(
            CapabilityPolicy.canLaunchGame(
                Engine.GAMEMAKER_VM,
                ready.copy(butterscotchInstalled = false),
            ),
        )
    }

    @Test
    fun `RPG Maker needs WebView and a scanned folder`() {
        assertTrue(CapabilityPolicy.canLaunchGame(Engine.RPGMAKER_MV, ready))
        assertFalse(
            CapabilityPolicy.canLaunchGame(
                Engine.RPGMAKER_MZ,
                ready.copy(hasGameFolder = false),
            ),
        )
        assertFalse(
            CapabilityPolicy.canLaunchGame(
                Engine.RPGMAKER_MV,
                ready.copy(webViewAvailable = false),
            ),
        )
    }

    @Test
    fun `unsupported engines are never launchable and explain why`() {
        assertFalse(CapabilityPolicy.canLaunchGame(Engine.RENPY, ready))
        assertEquals(
            "This engine has no runtime.",
            CapabilityPolicy.cannotLaunchReason(Engine.RENPY, ready),
        )
        assertNull(CapabilityPolicy.cannotLaunchReason(Engine.RPGMAKER_MV, ready))
    }
}

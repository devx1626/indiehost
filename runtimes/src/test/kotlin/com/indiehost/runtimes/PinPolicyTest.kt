package com.indiehost.runtimes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinPolicyTest {

    private fun game(
        isValid: Boolean = true,
        requestType: Int = PinCandidate.REQUEST_TYPE_SHORTCUT,
        isEnabled: Boolean = true,
        isPinned: Boolean = true,
        packageName: String? = GameMakerRuntimeAdapter.BUTTERSCOTCH_PACKAGE,
        shortcutId: String? = "game-1234",
    ) = PinCandidate(
        packageName = packageName,
        shortcutId = shortcutId,
        isValid = isValid,
        requestType = requestType,
        isEnabled = isEnabled,
        isPinned = isPinned,
    )

    @Test
    fun `accepts a valid Butterscotch game shortcut`() {
        assertTrue(PinPolicy.accept(game()))
    }

    @Test
    fun `rejects an invalid or cancelled request`() {
        assertFalse(PinPolicy.accept(game(isValid = false)))
    }

    @Test
    fun `rejects non shortcut and foreign requests`() {
        assertFalse(PinPolicy.accept(game(requestType = 2)))
        assertFalse(PinPolicy.accept(game(packageName = "com.other")))
        assertFalse(PinPolicy.accept(game(shortcutId = "settings")))
    }

    @Test
    fun `only visible shortcuts are enabled and pinned`() {
        assertTrue(PinPolicy.visible(game()))
        assertFalse(PinPolicy.visible(game(isEnabled = false)))
        assertFalse(PinPolicy.visible(game(isPinned = false)))
        assertFalse(PinPolicy.visible(game(packageName = "com.other")))
    }
}

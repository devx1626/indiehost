package com.indiehost.runtimes

import com.indiehost.detect.Engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RuntimeRegistryTest {

    private val gameMaker = GameMakerRuntimeAdapter()
    private val mv = RpgMakerRuntimeAdapter(Engine.RPGMAKER_MV)
    private val mz = RpgMakerRuntimeAdapter(Engine.RPGMAKER_MZ)
    private val registry = RuntimeRegistry(listOf(gameMaker, mv, mz))

    @Test
    fun `resolves every registered engine`() {
        assertSame(gameMaker, registry.forEngine(Engine.GAMEMAKER_VM))
        assertSame(mv, registry.forEngine(Engine.RPGMAKER_MV))
        assertSame(mz, registry.forEngine(Engine.RPGMAKER_MZ))
    }

    @Test
    fun `an engine without a runtime is not silently registered`() {
        assertNull(registry.forEngine(Engine.RENPY))
    }

    @Test
    fun `both RPG Maker adapters use the same manifest action`() {
        assertEquals(RpgMakerRuntimeAdapter.GAME_WEBVIEW_ACTION, mv.launchAction)
        assertEquals(RpgMakerRuntimeAdapter.GAME_WEBVIEW_ACTION, mz.launchAction)
        assertEquals("com.indiehost.launcher.game_id", RpgMakerRuntimeAdapter.EXTRA_GAME_ID)
    }
}

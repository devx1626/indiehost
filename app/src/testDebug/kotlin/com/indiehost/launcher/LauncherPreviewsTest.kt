package com.indiehost.launcher

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Device-less coverage check for the debug previews. It does not render pixels;
 * it guarantees the key states stay represented when UI code is edited.
 */
class LauncherPreviewsTest {

    @Test
    fun `key UI states have debug previews`() {
        val previews = Class.forName("com.indiehost.launcher.LauncherPreviewsKt")
            .declaredMethods
            .map { it.name }
            .toSet()

        listOf(
            "HomeEmptyPreview",
            "HomeScanningPreview",
            "DrawerNoMatchesPreview",
            "DrawerLoadingPreview",
        ).forEach { assertTrue("Missing preview: $it", it in previews) }
    }
}

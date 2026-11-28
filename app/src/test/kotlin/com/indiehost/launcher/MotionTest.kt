package com.indiehost.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {

    @Test
    fun `zero animator scale disables decorative motion`() {
        assertFalse(Motion.enabled(0f))
        assertFalse(Motion.enabled(-0.5f))
    }

    @Test
    fun `any positive scale keeps the short transition`() {
        assertTrue(Motion.enabled(1f))
        assertTrue(Motion.enabled(0.25f))
    }
}

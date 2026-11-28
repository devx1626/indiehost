package com.indiehost.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverStoreTest {

    @Test
    fun `a 2000 pixel image is subsampled below the limit`() {
        val sample = CoverStore.sampleSizeFor(2000, 2000)
        assertEquals(4, sample)
        assertTrue(2000 / sample <= CoverStore.MAX_PX)
        assertTrue(2000 / sample > CoverStore.MAX_PX / 2)
    }

    @Test
    fun `an image already under the limit is not subsampled`() {
        assertEquals(1, CoverStore.sampleSizeFor(512, 300))
        assertEquals(1, CoverStore.sampleSizeFor(100, 100))
    }

    @Test
    fun `a tall image is bounded by its longest edge`() {
        val sample = CoverStore.sampleSizeFor(400, 4000)
        assertEquals(8, sample)
        assertTrue(4000 / sample <= CoverStore.MAX_PX)
    }

    @Test
    fun `cover names are stable and id-specific`() {
        val a = CoverStore.fileNameFor("scan-abc")
        val b = CoverStore.fileNameFor("scan-abc")
        val c = CoverStore.fileNameFor("scan-def")

        assertEquals(a, b)
        assertTrue(a.endsWith(".webp"))
        assertTrue(a != c)
        assertTrue(!a.contains("scan"))
    }
}

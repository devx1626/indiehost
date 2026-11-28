package com.indiehost.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedbackPolicyTest {

    @Test
    fun `classifies statuses by urgency`() {
        assertEquals(FeedbackUrgency.BLOCKER, FeedbackPolicy.urgency("Folder access was revoked"))
        assertEquals(FeedbackUrgency.WARN, FeedbackPolicy.urgency("Imported 2 games; 1 folder skipped"))
        assertEquals(FeedbackUrgency.INFO, FeedbackPolicy.urgency("Pinned game-1234"))
        assertEquals(FeedbackUrgency.QUIET, FeedbackPolicy.urgency("Something happened"))
    }

    @Test
    fun `picks the loudest non blank message`() {
        assertEquals(
            "Could not save the scanned games",
            FeedbackPolicy.pick("", "Imported 3 games", "Could not save the scanned games"),
        )
        assertNull(FeedbackPolicy.pick("", ""))
    }

    @Test
    fun `queue expires old entries and keeps the loudest`() {
        var now = 0L
        val queue = FeedbackQueue { now }
        queue.enqueue("Imported 1 game")
        now = 100L
        queue.enqueue("Access revoked")
        assertEquals("Access revoked", queue.next(1_000L))

        now = 2_000L
        assertNull(queue.next(1_000L))
    }
}

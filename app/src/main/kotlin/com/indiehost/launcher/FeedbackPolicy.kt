package com.indiehost.launcher

/** How loudly a status message should be presented. */
enum class FeedbackUrgency { QUIET, INFO, WARN, BLOCKER }

/**
 * Pure feedback rules. Messages stay strings for backward compatibility; the
 * urgency is derived here so the UI can colour and prioritise them consistently.
 */
object FeedbackPolicy {

    fun urgency(message: String): FeedbackUrgency {
        val text = message.lowercase()
        return when {
            text.contains("revoked") || text.contains("could not") ||
                text.contains("cannot") || text.contains("failed") ||
                text.contains("not installed") || text.contains("broken") -> FeedbackUrgency.BLOCKER
            text.contains("skipped") || text.contains("blocked") ||
                text.contains("missing") || text.contains("duplicate") -> FeedbackUrgency.WARN
            text.contains("imported") || text.contains("pinned") ||
                text.contains("updated") || text.contains("scan") -> FeedbackUrgency.INFO
            else -> FeedbackUrgency.QUIET
        }
    }

    /** The highest-urgency message wins when several statuses are present. */
    fun pick(vararg messages: String): String? =
        messages.filter { it.isNotBlank() }
            .maxByOrNull { urgency(it).ordinal }
}

/**
 * Small transient queue with a TTL. Callers drive it from Compose state; no
 * notification channel, network or timer thread is involved.
 */
class FeedbackQueue(private val clock: () -> Long) {
    private data class Entry(val message: String, val urgency: FeedbackUrgency, val at: Long)

    private val entries = mutableListOf<Entry>()

    fun enqueue(message: String) {
        if (message.isBlank()) return
        entries += Entry(message, FeedbackPolicy.urgency(message), clock())
    }

    /** Removes expired entries and returns the highest-urgency live one. */
    fun next(ttlMs: Long): String? {
        val now = clock()
        entries.removeAll { now - it.at > ttlMs }
        return entries.maxByOrNull { it.urgency.ordinal }?.message
    }

    fun clear() = entries.clear()
}

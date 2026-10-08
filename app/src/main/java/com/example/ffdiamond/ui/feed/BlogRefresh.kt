package com.example.ffdiamond.ui.feed

/**
 * Hindi Gyan posts change through the day. Discover re-hits api.php on this interval so a
 * right-swipe does not keep yesterday's list.
 */
object BlogRefresh {

    const val INTERVAL_MS = 60 * 60 * 1000L
    const val RETRY_MS = 15_000L

    fun isStale(lastSuccessAt: Long, now: Long): Boolean =
        lastSuccessAt <= 0L || now - lastSuccessAt >= INTERVAL_MS

    fun nextWaitMs(lastSuccessAt: Long, now: Long): Long {
        if (lastSuccessAt <= 0L) return RETRY_MS
        val remaining = INTERVAL_MS - (now - lastSuccessAt)
        return if (remaining <= 0L) RETRY_MS else remaining.coerceAtMost(INTERVAL_MS)
    }
}

package com.example.ffdiamond.ui.feed

/**
 * Discover list body (under the header): first ad at position 2, then one ad after
 * every 3 posts. Disabled = posts only.
 */
object BlogAdSlots {

    fun adCount(posts: Int, enabled: Boolean): Int {
        if (!enabled || posts <= 0) return 0
        return 1 + (posts - 1) / 3
    }

    fun isAd(bodyIndex: Int, enabled: Boolean): Boolean {
        if (!enabled || bodyIndex < 0) return false
        if (bodyIndex == 1) return true
        if (bodyIndex < 2) return false
        return (bodyIndex - 2) % 4 == 3
    }

    fun postIndex(bodyIndex: Int, enabled: Boolean): Int {
        if (!enabled || bodyIndex <= 0) return bodyIndex
        val afterFirstAd = bodyIndex - 2
        val block = afterFirstAd / 4
        val offset = afterFirstAd % 4
        return 1 + block * 3 + offset
    }
}

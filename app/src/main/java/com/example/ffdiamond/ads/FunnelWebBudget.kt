package com.example.ffdiamond.ads

/**
 * How many Custom Tabs to open together when funnel Next/Done/CTA shows a web ad.
 * Missing [max] (< 0) → 1 tab. 0 → none. N → N tabs at once.
 */
object FunnelWebBudget {

    fun tabsAtOnce(max: Int): Int {
        if (max < 0) return 1
        return max
    }
}

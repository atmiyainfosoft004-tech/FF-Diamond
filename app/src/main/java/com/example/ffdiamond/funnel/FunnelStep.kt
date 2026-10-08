package com.example.ffdiamond.funnel

enum class FunnelStep {
    SPLASH,
    SET_DEFAULT,
    INTRO,
    LANGUAGE,
    GENDER,
    AGE,
    CATEGORY,
    WATCH,
    INTERESTS,
    NO_WATERMARK,
    FAST_SPEED,
    MULTI_FORMAT,
    GO_TO_APP,
    START_APP,
    SET_DEFAULT_GATE;

    fun next(): FunnelStep? = entries.getOrNull(entries.indexOf(this) + 1)
}

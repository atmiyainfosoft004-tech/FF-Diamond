package com.example.ffdiamond.funnel

import androidx.annotation.DrawableRes

data class LanguageItem(
    val name: String,
    val nativeName: String,
    @get:DrawableRes val flagRes: Int,
    val locale: String
)

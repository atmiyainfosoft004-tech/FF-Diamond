package com.example.ffdiamond.funnel

import androidx.annotation.DrawableRes

data class IntroSlide(
    @get:DrawableRes val illustrationRes: Int,
    val title: String,
    val subtitle: String
)

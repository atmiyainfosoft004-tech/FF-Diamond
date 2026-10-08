package com.example.ffdiamond.guide

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.ffdiamond.R

/** One entry shown in a gallery grid and on the detail screen. */
data class FfItem(
    val id: String,
    val name: String,
    @get:DrawableRes val imageRes: Int,
    val headline: String,
    val summary: String,
    val description: String
)

/** Home sections. Each one maps to its own gallery screen. */
enum class FfCategory(
    @get:StringRes val titleRes: Int,
    val showLabels: Boolean = true
) {
    CHARACTERS(R.string.ff_characters),
    PETS(R.string.ff_pets),
    BUNDLES(R.string.ff_bundles),
    WEAPONS(R.string.ff_weapons),
    VEHICLES(R.string.ff_vehicles),
    EMOTES(R.string.ff_emotes, showLabels = false),
    PARACHUTES(R.string.ff_parachutes),
    TIPS(R.string.ff_tips_tricks)
}

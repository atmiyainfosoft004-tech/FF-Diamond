package com.example.ffdiamond.guide

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ffdiamond.ads.AdsBinder
import com.example.ffdiamond.databinding.ActivityGalleryBinding
import com.example.ffdiamond.util.applySystemBarInsetsAsPadding
import com.example.ffdiamond.util.enableMarqueeLabels

/** Grid of items for one home section. Tapping an item opens its detail screen. */
abstract class GalleryActivity : GuideActivity() {

    abstract val category: FfCategory

    private lateinit var binding: ActivityGalleryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.llGalleryRoot.applySystemBarInsetsAsPadding()
        binding.root.enableMarqueeLabels()
        bindHeader(binding.header, category.titleRes)
        AdsBinder.bindBanner(this, binding.adBanner.root)

        val items = FfRepository.items(category)
        binding.rvItems.layoutManager = if (category == FfCategory.TIPS) {
            LinearLayoutManager(this)
        } else {
            GridLayoutManager(this, SPAN_COUNT)
        }
        binding.rvItems.adapter = GalleryAdapter(items, category) { index ->
            val detail = if (category == FfCategory.TIPS) {
                TipDetailActivity.intent(this, index)
            } else {
                ItemDetailActivity.intent(this, category, index)
            }
            openWithAd(detail)
        }
        binding.rvItems.isVisible = items.isNotEmpty()
    }

    private companion object {
        const val SPAN_COUNT = 2
    }
}

class CharactersActivity : GalleryActivity() {
    override val category = FfCategory.CHARACTERS
}

class PetsActivity : GalleryActivity() {
    override val category = FfCategory.PETS
}

class BundlesActivity : GalleryActivity() {
    override val category = FfCategory.BUNDLES
}

class WeaponsActivity : GalleryActivity() {
    override val category = FfCategory.WEAPONS
}

class VehiclesActivity : GalleryActivity() {
    override val category = FfCategory.VEHICLES
}

class EmotesActivity : GalleryActivity() {
    override val category = FfCategory.EMOTES
}

class ParachutesActivity : GalleryActivity() {
    override val category = FfCategory.PARACHUTES
}

class TipsTricksActivity : GalleryActivity() {
    override val category = FfCategory.TIPS
}

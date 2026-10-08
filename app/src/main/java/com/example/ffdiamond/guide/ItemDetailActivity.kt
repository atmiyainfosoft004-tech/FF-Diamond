package com.example.ffdiamond.guide

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.viewpager2.widget.ViewPager2
import com.example.ffdiamond.ads.AdsBinder
import com.example.ffdiamond.databinding.ActivityItemDetailBinding
import com.example.ffdiamond.util.applySystemBarInsetsAsPadding
import com.example.ffdiamond.util.setOnSafeClickListener

/**
 * Character / pet / weapon / ... detail. Swipe left-right or tap the arrows to move between
 * items. The arrows wrap around at both ends; swiping stops at the first and last item.
 */
class ItemDetailActivity : GuideActivity() {

    private lateinit var binding: ActivityItemDetailBinding
    private lateinit var items: List<FfItem>

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            binding.header.txtScreenTitle.text = items[position].name
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val category = runCatching { FfCategory.valueOf(intent.getStringExtra(EXTRA_CATEGORY).orEmpty()) }
            .getOrDefault(FfCategory.CHARACTERS)
        items = FfRepository.items(category)
        if (items.isEmpty()) {
            finish()
            return
        }
        val start = (savedInstanceState?.getInt(STATE_INDEX) ?: intent.getIntExtra(EXTRA_INDEX, 0))
            .coerceIn(0, items.lastIndex)

        binding = ActivityItemDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.llDetailRoot.applySystemBarInsetsAsPadding()
        binding.header.btnBack.setOnSafeClickListener { finish() }
        AdsBinder.bindNative(this, binding.adNative.root)

        binding.vpDetail.adapter = DetailPagerAdapter(items)
        binding.vpDetail.offscreenPageLimit = 1
        binding.vpDetail.registerOnPageChangeCallback(pageCallback)
        binding.vpDetail.setCurrentItem(start, false)
        binding.header.txtScreenTitle.text = items[start].name

        binding.btnPrevious.setOnSafeClickListener(300L) { step(-1) }
        binding.btnNextItem.setOnSafeClickListener(300L) { step(1) }
    }

    private fun step(delta: Int) {
        val current = binding.vpDetail.currentItem
        val target = current + delta
        when {
            target < 0 -> binding.vpDetail.setCurrentItem(items.lastIndex, false)
            target > items.lastIndex -> binding.vpDetail.setCurrentItem(0, false)
            else -> binding.vpDetail.setCurrentItem(target, true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::binding.isInitialized) outState.putInt(STATE_INDEX, binding.vpDetail.currentItem)
    }

    override fun onDestroy() {
        if (::binding.isInitialized) binding.vpDetail.unregisterOnPageChangeCallback(pageCallback)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_CATEGORY = "extra_category"
        private const val EXTRA_INDEX = "extra_index"
        private const val STATE_INDEX = "state_index"

        fun intent(context: Context, category: FfCategory, index: Int): Intent =
            Intent(context, ItemDetailActivity::class.java)
                .putExtra(EXTRA_CATEGORY, category.name)
                .putExtra(EXTRA_INDEX, index)
    }
}

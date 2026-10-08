package com.example.ffdiamond.guide

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import com.example.ffdiamond.ads.AdsBinder
import com.example.ffdiamond.databinding.ActivityTipDetailBinding
import com.example.ffdiamond.util.applySystemBarInsetsAsPadding
import com.example.ffdiamond.util.setOnSafeClickListener
import java.util.Locale

/** One Tips & Tricks entry: scrolling title in the header and the tip text in a card. */
class TipDetailActivity : GuideActivity() {

    private lateinit var binding: ActivityTipDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tips = FfRepository.items(FfCategory.TIPS)
        val tip = tips.getOrNull(intent.getIntExtra(EXTRA_INDEX, 0))
        if (tip == null) {
            finish()
            return
        }
        binding = ActivityTipDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.llTipDetailRoot.applySystemBarInsetsAsPadding()
        binding.header.btnBack.setOnSafeClickListener { finish() }
        binding.header.txtScreenTitle.apply {
            text = tip.name.uppercase(Locale.getDefault())
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
        }
        binding.txtTipBody.text = tip.summary
        AdsBinder.bindNative(this, binding.adNative.root)
    }

    companion object {
        private const val EXTRA_INDEX = "extra_index"

        fun intent(context: Context, index: Int): Intent =
            Intent(context, TipDetailActivity::class.java).putExtra(EXTRA_INDEX, index)
    }
}

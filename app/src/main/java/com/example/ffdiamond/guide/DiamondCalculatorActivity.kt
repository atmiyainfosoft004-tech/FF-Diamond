package com.example.ffdiamond.guide

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.example.ffdiamond.R
import com.example.ffdiamond.ads.AdsBinder
import com.example.ffdiamond.ads.AdsGate
import com.example.ffdiamond.databinding.ActivityDiamondCalculatorBinding
import com.example.ffdiamond.util.applySystemBarInsetsAsPadding
import com.example.ffdiamond.util.enableMarqueeLabels
import com.example.ffdiamond.util.setOnSafeClickListener
import java.util.Locale

class DiamondCalculatorActivity : GuideActivity() {

    private lateinit var binding: ActivityDiamondCalculatorBinding
    private var isCounting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiamondCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.llCalculatorRoot.applySystemBarInsetsAsPadding(includeIme = true)
        binding.root.enableMarqueeLabels()
        bindHeader(binding.header, R.string.ff_calculator)
        AdsBinder.bindBanner(this, binding.adBanner.root)

        binding.txtCostResult.text = DiamondPrice.format(0.0, getString(R.string.ff_unit_usd))
        binding.edtDiamonds.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                performCount()
                true
            } else {
                false
            }
        }
        binding.btnCountNow.setOnSafeClickListener(1000L) { performCount() }
    }

    private fun performCount() {
        if (isCounting) return
        val raw = binding.edtDiamonds.text?.toString()?.trim().orEmpty()
        val diamonds = raw.replace(",", "").toLongOrNull()
        if (diamonds == null || diamonds <= 0L) {
            Toast.makeText(this, R.string.ff_calc_error, Toast.LENGTH_SHORT).show()
            return
        }
        isCounting = true
        binding.btnCountNow.isEnabled = false
        hideKeyboard(binding.edtDiamonds)

        AdsGate.onInterOrWeb(this) {
            isCounting = false
            if (isFinishing || isDestroyed) return@onInterOrWeb
            binding.btnCountNow.isEnabled = true
            binding.txtCostResult.text = DiamondPrice.format(
                DiamondPrice.usdFor(diamonds),
                getString(R.string.ff_unit_usd)
            )
        }
    }

    private fun hideKeyboard(target: View) {
        target.clearFocus()
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(target.windowToken, 0)
    }

    override fun onDestroy() {
        isCounting = false
        super.onDestroy()
    }
}

/** Estimate based on the standard 100 diamonds = 0.99 USD top-up pack. */
object DiamondPrice {
    private const val USD_PER_DIAMOND = 0.0099

    fun usdFor(diamonds: Long): Double = diamonds * USD_PER_DIAMOND

    fun format(value: Double, unit: String): String =
        String.format(Locale.US, "%,.2f  %s", value, unit)
}

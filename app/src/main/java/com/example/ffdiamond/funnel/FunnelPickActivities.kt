package com.example.ffdiamond.funnel

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.ffdiamond.R
import com.example.ffdiamond.databinding.ActivityFunnelPickBinding
import com.example.ffdiamond.databinding.ItemFunnelGridBinding
import com.example.ffdiamond.databinding.ItemFunnelPickBinding
import kotlinx.coroutines.launch

abstract class FunnelPickActivity : FunnelScreenActivity() {
    abstract val titleRes: Int
    abstract val subtitleRes: Int
    abstract val labels: Array<String>
    abstract val letters: Array<String>
    abstract val colors: IntArray
    abstract val multi: Boolean
    abstract val key: FunnelPreferences.ChoiceKey
    protected open val itemLayoutRes: Int = R.layout.item_funnel_pick
    protected open val images: IntArray = intArrayOf()
    protected open val columns: Int = 1

    private lateinit var binding: ActivityFunnelPickBinding
    private val chosen = linkedSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFunnelPickBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindRoot(binding.funnelRoot)
        binding.pickTitle.setText(titleRes)
        binding.pickSubtitle.setText(subtitleRes)
        binding.pickSubtitle.isVisible = binding.pickSubtitle.text.isNotBlank()
        val top = binding.pickListTop
        val bottom = binding.pickListBottom
        top.columnCount = columns
        bottom.columnCount = columns
        val inflater = LayoutInflater.from(this)
        val split = nativeAfterRows(labels.size)
        labels.forEachIndexed { index, label ->
            val parent = if (index < split) top else bottom
            val row = inflateItem(inflater, parent, index, label)
            row.setOnClickListener {
                if (multi) {
                    if (!chosen.add(index)) chosen.remove(index)
                } else {
                    chosen.clear()
                    chosen += index
                }
                paint(top, bottom)
            }
            applyItemLayout(row, parent)
            parent.addView(row)
        }
        bottom.isVisible = bottom.childCount > 0
        if (!multi && labels.isNotEmpty()) chosen += 0
        paint(top, bottom)
        binding.pickNext.setOnClickListener {
            if (chosen.isEmpty()) {
                Toast.makeText(this, R.string.funnel_pick_one, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val values = chosen.map { labels[it] }.toSet()
            lifecycleScope.launch {
                if (multi) {
                    FunnelPreferences.saveMulti(this@FunnelPickActivity, key, values)
                } else {
                    FunnelPreferences.saveSingle(this@FunnelPickActivity, key, values.first())
                }
                goNext()
            }
        }
    }

    private fun inflateItem(inflater: LayoutInflater, parent: ViewGroup, index: Int, label: String): View {
        return if (itemLayoutRes == R.layout.item_funnel_grid) {
            val item = ItemFunnelGridBinding.inflate(inflater, parent, false)
            bindGrid(item, index, label)
            item.root
        } else {
            val item = ItemFunnelPickBinding.inflate(inflater, parent, false)
            bindPick(item, index, label)
            item.root
        }
    }

    private fun bindGrid(item: ItemFunnelGridBinding, index: Int, label: String) {
        item.pickLabel.text = label
        item.pickLabel.isSelected = true
        val imageRes = images.getOrElse(index) { 0 }
        if (imageRes != 0) {
            item.pickIcon.setImageResource(imageRes)
            item.pickIcon.isVisible = true
            item.pickIconLetter.isVisible = false
            item.pickIconBg.isVisible = false
        }
    }

    private fun bindPick(item: ItemFunnelPickBinding, index: Int, label: String) {
        item.pickLabel.text = label
        item.pickLabel.isSelected = true
        val imageRes = images.getOrElse(index) { 0 }
        if (imageRes != 0) {
            item.pickIcon.setImageResource(imageRes)
            item.pickIcon.isVisible = true
            item.pickIconLetter.isVisible = false
            item.pickIconBg.isVisible = false
            return
        }
        item.pickIconLetter.text = letters.getOrElse(index) { "?" }
        if (item.pickIconBg.visibility == View.VISIBLE) {
            val color = colors.getOrElse(index) { 0xFFEE1D23.toInt() }
            item.pickIconBg.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
    }

    private fun applyItemLayout(row: View, parent: ViewGroup) {
        val margin = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._6sdp)
        if (parent is GridLayout && columns > 1) {
            row.layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(margin, margin, margin, margin)
            }
        }
    }

    private fun paint(top: GridLayout, bottom: GridLayout) {
        var option = 0
        option = paintList(top, option)
        paintList(bottom, option)
    }

    private fun paintList(list: GridLayout, start: Int): Int {
        var option = start
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            val selected = option in chosen
            child.isSelected = selected
            option++
        }
        return option
    }

    companion object {
        fun nativeAfterRows(count: Int): Int = count
    }
}

class AgeActivity : FunnelPickActivity() {
    override val step = FunnelStep.AGE
    override val titleRes = R.string.ff_age_title
    override val subtitleRes = R.string.ff_empty
    override val labels get() = resources.getStringArray(R.array.ff_ages)
    override val letters = arrayOf("0", "1", "2", "3", "4", "5")
    override val colors = intArrayOf(
        "#5B8DEF".toColorInt(),
        "#2F9E44".toColorInt(),
        "#F5A524".toColorInt(),
        "#EE1D23".toColorInt(),
        "#8A2BE2".toColorInt(),
        "#00C2FF".toColorInt()
    )
    override val multi = false
    override val key = FunnelPreferences.ChoiceKey.AGE
}

class FavoriteCharacterActivity : FunnelPickActivity() {
    override val step = FunnelStep.CATEGORY
    override val titleRes = R.string.ff_character_pick_title
    override val subtitleRes = R.string.ff_empty
    override val labels get() = resources.getStringArray(R.array.ff_pick_characters)
    override val letters = arrayOf("A", "C", "W", "K")
    override val colors = intArrayOf(
        "#1877F2".toColorInt(),
        "#111111".toColorInt(),
        "#E1306C".toColorInt(),
        "#FF6A00".toColorInt()
    )
    override val multi = false
    override val key = FunnelPreferences.ChoiceKey.CATEGORIES
    override val columns = 2
    override val itemLayoutRes = R.layout.item_funnel_grid
    override val images = intArrayOf(
        R.drawable.ff_char_alok,
        R.drawable.ff_char_chrono,
        R.drawable.ff_char_wukong,
        R.drawable.ff_char_kelly
    )
}

class FavoritePetActivity : FunnelPickActivity() {
    override val step = FunnelStep.WATCH
    override val titleRes = R.string.ff_pet_pick_title
    override val subtitleRes = R.string.ff_empty
    override val labels get() = resources.getStringArray(R.array.ff_pick_pets)
    override val letters = arrayOf("F", "D", "S", "D")
    override val colors = intArrayOf(
        "#0F3CC9".toColorInt(),
        "#00A8E1".toColorInt(),
        "#9B0000".toColorInt(),
        "#8B2BE2".toColorInt()
    )
    override val multi = false
    override val key = FunnelPreferences.ChoiceKey.WATCH
    override val columns = 2
    override val itemLayoutRes = R.layout.item_funnel_grid
    override val images = intArrayOf(
        R.drawable.ff_pet_falco,
        R.drawable.ff_pet_detective_panda,
        R.drawable.ff_pet_spirit_fox,
        R.drawable.ff_pet_dreki
    )
}

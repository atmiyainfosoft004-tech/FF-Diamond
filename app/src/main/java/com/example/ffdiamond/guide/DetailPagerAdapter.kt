package com.example.ffdiamond.guide

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ffdiamond.databinding.ItemDetailPageBinding

/** One page per item so the detail screen can be swiped as well as stepped with the arrows. */
class DetailPagerAdapter(
    private val items: List<FfItem>
) : RecyclerView.Adapter<DetailPagerAdapter.PageHolder>() {

    class PageHolder(val binding: ItemDetailPageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder =
        PageHolder(ItemDetailPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            imgDetail.setImageResource(item.imageRes)
            imgDetail.contentDescription = item.name
            txtDetailName.text = item.name
            txtDetailName.isSelected = true
            txtHeadline.text = item.headline
            txtSummary.text = item.summary
            txtDescription.text = item.description
            svDetail.scrollTo(0, 0)
        }
    }

    override fun getItemCount(): Int = items.size
}

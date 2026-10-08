package com.example.ffdiamond.guide

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.ffdiamond.databinding.ItemGalleryCardBinding
import com.example.ffdiamond.databinding.ItemTipCardBinding
import com.example.ffdiamond.util.setOnSafeClickListener

class GalleryAdapter(
    private val items: List<FfItem>,
    private val category: FfCategory,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class CardHolder(val binding: ItemGalleryCardBinding) : RecyclerView.ViewHolder(binding.root)
    class TipHolder(val binding: ItemTipCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int =
        if (category == FfCategory.TIPS) TYPE_TIP else TYPE_CARD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_TIP) {
            TipHolder(ItemTipCardBinding.inflate(inflater, parent, false))
        } else {
            CardHolder(ItemGalleryCardBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is TipHolder -> {
                holder.binding.imgTipIcon.setImageResource(item.imageRes)
                holder.binding.txtTipTitle.text = item.name
                holder.binding.txtTipTitle.isSelected = true
                holder.binding.root.setOnSafeClickListener { clicked(holder) }
            }
            is CardHolder -> {
                holder.binding.imgItem.setImageResource(item.imageRes)
                holder.binding.imgItem.contentDescription = item.name
                holder.binding.txtItemName.text = item.name
                holder.binding.txtItemName.isSelected = true
                holder.binding.txtItemName.isVisible = category.showLabels
                holder.binding.root.setOnSafeClickListener { clicked(holder) }
            }
        }
    }

    private fun clicked(holder: RecyclerView.ViewHolder) {
        val pos = holder.bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) onClick(pos)
    }

    override fun getItemCount(): Int = items.size

    private companion object {
        const val TYPE_CARD = 0
        const val TYPE_TIP = 1
    }
}

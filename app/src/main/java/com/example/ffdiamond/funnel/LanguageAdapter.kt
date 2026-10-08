package com.example.ffdiamond.funnel

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ffdiamond.databinding.ItemFunnelLanguageBinding

class LanguageAdapter(
    private val languages: List<LanguageItem>,
    private var selectedIndex: Int,
    private val onSelected: (Int) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

    class LanguageViewHolder(val binding: ItemFunnelLanguageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val binding = ItemFunnelLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LanguageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        val item = languages[position]
        val isSelected = position == selectedIndex
        holder.binding.imgLanguageFlag.setImageResource(item.flagRes)
        holder.binding.txtLanguageName.text = item.name
        holder.binding.txtLanguageNative.text = item.nativeName
        holder.binding.clLanguageCard.isSelected = isSelected
        holder.binding.vRadio.isSelected = isSelected
        holder.binding.clLanguageCard.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION || pos == selectedIndex) return@setOnClickListener
            val prev = selectedIndex
            selectedIndex = pos
            notifyItemChanged(prev)
            notifyItemChanged(selectedIndex)
            onSelected(selectedIndex)
        }
    }

    override fun getItemCount(): Int = languages.size
}

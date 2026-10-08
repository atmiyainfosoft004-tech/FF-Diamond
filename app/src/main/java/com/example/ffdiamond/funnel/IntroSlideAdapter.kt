package com.example.ffdiamond.funnel

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ffdiamond.databinding.ItemIntroSlideBinding

class IntroSlideAdapter(
    private val slides: List<IntroSlide>
) : RecyclerView.Adapter<IntroSlideAdapter.IntroViewHolder>() {

    class IntroViewHolder(val binding: ItemIntroSlideBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IntroViewHolder {
        val binding = ItemIntroSlideBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IntroViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IntroViewHolder, position: Int) {
        val item = slides[position]
        holder.binding.imgIllustration.setImageResource(item.illustrationRes)
        holder.binding.txtTitle.text = item.title
        holder.binding.txtSubtitle.text = item.subtitle
    }

    override fun getItemCount(): Int = slides.size
}

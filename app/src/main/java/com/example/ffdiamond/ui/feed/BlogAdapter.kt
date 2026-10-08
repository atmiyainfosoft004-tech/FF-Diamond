package com.example.ffdiamond.ui.feed

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ffdiamond.R
import com.example.ffdiamond.ads.AdsBinder
import com.example.ffdiamond.ads.AdsRepository
import com.example.ffdiamond.ads.InstallSource
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class BlogAdapter(
    private val activity: Activity,
    private val owner: Any,
    private val images: BlogImageLoader,
    private val onClick: (BlogPost) -> Unit,
    private val onNearEnd: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<BlogPost>()
    private val adsEnabled: Boolean get() =
        InstallSource.adsAllowed(activity) && AdsRepository.config(activity).adsEnabled

    fun replace(posts: List<BlogPost>) {
        items.clear()
        items.addAll(posts)
        notifyDataSetChanged()
    }

    fun append(posts: List<BlogPost>) {
        if (posts.isEmpty()) return
        items.addAll(posts)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        if (items.isEmpty()) return 0
        return items.size + adCount()
    }

    override fun getItemViewType(position: Int): Int {
        return if (isAd(position)) TYPE_AD else TYPE_CARD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_AD -> AdHolder(inflater.inflate(R.layout.item_feed_ad, parent, false) as ViewGroup)
            else -> PostHolder(inflater.inflate(R.layout.view_feed_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PostHolder -> {
                val post = items[postIndex(position)]
                holder.bind(post)
                if (position >= itemCount - 3) onNearEnd()
            }
            is AdHolder -> AdsBinder.bindNativeOnce(
                activity = activity,
                slot = holder.slot,
                googleLayout = R.layout.view_feed_native,
                customLayout = R.layout.view_feed_custom_native,
                owner = owner
            )
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is PostHolder) images.clear(holder.image)
    }

    private fun adCount(): Int = BlogAdSlots.adCount(items.size, adsEnabled)

    private fun isAd(bodyIndex: Int): Boolean = BlogAdSlots.isAd(bodyIndex, adsEnabled)

    private fun postIndex(bodyIndex: Int): Int = BlogAdSlots.postIndex(bodyIndex, adsEnabled)

    private class AdHolder(val slot: ViewGroup) : RecyclerView.ViewHolder(slot)

    private inner class PostHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.feed_row_image)
        private val title: TextView = view.findViewById(R.id.feed_row_title)
        private val category: TextView = view.findViewById(R.id.feed_row_category)
        private val summary: TextView = view.findViewById(R.id.feed_row_summary)
        private val meta: TextView = view.findViewById(R.id.feed_row_meta)

        fun bind(post: BlogPost) {
            title.text = post.title
            category.text = post.category
            summary.text = post.summary
            summary.visibility = if (post.summary.isBlank()) View.GONE else View.VISIBLE
            meta.text = metaLine(itemView.context, post)
            tintCategory(post.categoryColor)
            images.bind(image, post.imageUrl)
            itemView.setOnClickListener { onClick(post) }
        }

        private fun tintCategory(hex: String) {
            val color = runCatching { Color.parseColor(hex) }.getOrElse {
                itemView.context.getColor(R.color.accent)
            }
            val chip = GradientDrawable().apply {
                cornerRadius = itemView.resources.displayMetrics.density * 18f
                setColor(color)
            }
            category.background = chip
            category.setTextColor(Color.WHITE)
        }
    }

    private fun metaLine(context: Context, post: BlogPost): String {
        val age = relativeTime(post.publishedAt)
        val read = if (post.readingMinutes > 0) {
            context.getString(R.string.feed_reading, post.readingMinutes)
        } else {
            ""
        }
        return listOf(age, read).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun relativeTime(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            val then = OffsetDateTime.parse(raw)
            val hours = Duration.between(then, OffsetDateTime.now()).toHours().coerceAtLeast(0)
            when {
                hours < 1 -> ""
                hours < 24 -> "${hours}h"
                else -> "${hours / 24}d"
            }
        } catch (_: DateTimeParseException) {
            ""
        }
    }

    companion object {
        const val TYPE_CARD = 0
        const val TYPE_AD = 1
    }
}

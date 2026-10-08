package com.example.ffdiamond.ui.feed

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ffdiamond.R
import com.example.ffdiamond.ads.AdsBinder
import com.example.ffdiamond.ads.AdsRepository
import com.example.ffdiamond.databinding.FragmentFeedBinding
import com.example.ffdiamond.funnel.AppLocale
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen Discover page to the left of home. Cards come from Hindi Gyan; a tap opens the
 * article in Chrome Custom Tabs.
 */
class FeedFragment : Fragment() {

    private var binding: FragmentFeedBinding? = null
    private lateinit var images: BlogImageLoader
    private lateinit var adapter: BlogAdapter

    private var nextUrl: String? = null
    private var hasMore = false
    private var loading = false
    private var lastSuccessAt = 0L
    private var seenIds = HashSet<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentFeedBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        images = BlogImageLoader(viewLifecycleOwner.lifecycleScope)
        adapter = BlogAdapter(
            activity = requireActivity(),
            owner = this,
            images = images,
            onClick = ::openArticle,
            onNearEnd = { loadMore() }
        )

        val b = binding ?: return
        b.rvFeedList.layoutManager = LinearLayoutManager(requireContext())
        b.rvFeedList.adapter = adapter
        b.rvFeedList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) loadMore()
            }
        })
        val headerInitialLeft = b.llFeedHeader.paddingLeft
        val headerInitialTop = b.llFeedHeader.paddingTop
        val headerInitialRight = b.llFeedHeader.paddingRight
        val headerInitialBottom = b.llFeedHeader.paddingBottom
        val listInitialLeft = b.rvFeedList.paddingLeft
        val listInitialRight = b.rvFeedList.paddingRight
        val listInitialBottom = b.rvFeedList.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            b.llFeedHeader.setPadding(
                bars.left + headerInitialLeft,
                bars.top + headerInitialTop,
                bars.right + headerInitialRight,
                headerInitialBottom
            )
            b.rvFeedList.setPadding(
                bars.left + listInitialLeft,
                b.rvFeedList.paddingTop,
                bars.right + listInitialRight,
                bars.bottom + listInitialBottom
            )
            insets
        }
        b.btnFeedRetry.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch { refresh(silent = false) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    if (BlogRefresh.isStale(lastSuccessAt, SystemClock.elapsedRealtime())) {
                        refresh(silent = adapter.itemCount > 0)
                    }
                    delay(BlogRefresh.nextWaitMs(lastSuccessAt, SystemClock.elapsedRealtime()))
                }
            }
        }
    }

    fun onBack(): Boolean = false

    fun setActive(active: Boolean) {
        if (!active || loading || !::adapter.isInitialized || view == null) return
        if (adapter.itemCount == 0 ||
            BlogRefresh.isStale(lastSuccessAt, SystemClock.elapsedRealtime())
        ) {
            viewLifecycleOwner.lifecycleScope.launch { refresh(silent = adapter.itemCount > 0) }
        }
    }

    private suspend fun refresh(silent: Boolean = false) {
        if (loading) return
        loading = true
        if (!silent) {
            binding?.pbFeedLoading?.isVisible = true
            binding?.llFeedErrorPanel?.isVisible = false
        }
        try {
            val endpoint = AdsRepository.config(requireContext()).blogUrl
            AppLocale.hydrate(requireContext())
            val lang = AppLocale.cached() ?: AppLocale.DEFAULT
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    BlogApi.fetch(
                        page = 1,
                        locale = Locale.forLanguageTag(lang),
                        endpoint = endpoint
                    )
                }
            }
            val page = result.getOrNull()
            if (page == null || page.items.isEmpty()) {
                if (adapter.itemCount == 0) {
                    binding?.llFeedErrorPanel?.isVisible = true
                }
                return
            }
            lastSuccessAt = SystemClock.elapsedRealtime()
            seenIds.clear()
            seenIds.addAll(page.items.map { it.id })
            nextUrl = page.nextUrl
            hasMore = page.hasMore
            adapter.replace(page.items)
            binding?.llFeedErrorPanel?.isVisible = false
        } finally {
            loading = false
            binding?.pbFeedLoading?.isVisible = false
        }
    }

    private fun loadMore() {
        if (loading || !hasMore) return
        val url = nextUrl ?: return
        loading = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { runCatching { BlogApi.fetchNext(url) } }
                val page = result.getOrNull() ?: return@launch
                val fresh = page.items.filter { seenIds.add(it.id) }
                nextUrl = page.nextUrl
                hasMore = page.hasMore && page.nextUrl != null
                adapter.append(fresh)
            } finally {
                loading = false
            }
        }
    }

    private fun openArticle(post: BlogPost) {
        val uri = post.url.takeIf { it.startsWith("https://") }?.let(Uri::parse) ?: return
        val context = context ?: return
        val toolbar = ContextCompat.getColor(context, R.color.feed_surface)
        val tabs = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(toolbar)
                    .build()
            )
            .build()
        try {
            tabs.launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }
    }

    override fun onDestroyView() {
        AdsBinder.release(this)
        binding = null
        super.onDestroyView()
    }
}

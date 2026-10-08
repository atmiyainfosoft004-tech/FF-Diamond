package com.example.ffdiamond.ads

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.ffdiamond.R
import com.example.ffdiamond.ads.SafeNativeAdContainer
import com.example.ffdiamond.guide.MediaThumbs
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AdsBinder {

    private const val TAG = "AdsBinder"
    private val natives = mutableMapOf<Int, MutableList<NativeAd>>()
    private val nativeSlots = CopyOnWriteArrayList<NativeSlot>()
    private val bannerSlots = CopyOnWriteArrayList<BannerSlot>()
    private val handler = Handler(Looper.getMainLooper())
    private val listening = AtomicBoolean(false)
    @Volatile private var displayKey: String? = null
    @Volatile var lastNativeClickTime: Long = 0L

    private data class NativeSlot(
        val activity: WeakReference<Activity>,
        val slot: WeakReference<ViewGroup>,
        val googleLayout: Int,
        val customLayout: Int,
        val owner: WeakReference<Any>,
        val ownerKey: Int
    )

    private data class BannerSlot(
        val activity: WeakReference<Activity>,
        val slot: WeakReference<ViewGroup>
    )

    fun register(app: Application) {
        if (!listening.compareAndSet(false, true)) return
        displayKey = displayKey(AdsRepository.config(app))
        AdsRepository.addListener { config ->
            val next = displayKey(config)
            if (next == displayKey) return@addListener
            displayKey = next
            handler.post { rebindOpenSlots() }
        }
    }

    fun bind(activity: Activity) {
        bindNative(activity, activity.findViewById(R.id.ad_native))
        bindBanner(activity, activity.findViewById(R.id.ad_banner))
    }

    fun release(owner: Any) {
        val key = owner.hashCode()
        natives.remove(key)?.forEach { runCatching { it.destroy() } }
        nativeSlots.removeAll { it.ownerKey == key || it.slot.get() == null }
        bannerSlots.removeAll { it.slot.get() == null }
    }

    fun bindNativeOnce(
        activity: Activity,
        slot: ViewGroup?,
        googleLayout: Int = R.layout.view_native_ad,
        customLayout: Int = R.layout.view_custom_native,
        owner: Any = activity
    ) {
        if (slot == null || slot.childCount > 0) return
        bindNative(activity, slot, googleLayout, customLayout, owner)
    }

    fun bindNative(
        activity: Activity,
        slot: ViewGroup?,
        googleLayout: Int = R.layout.view_native_ad,
        customLayout: Int = R.layout.view_custom_native,
        owner: Any = activity
    ) {
        if (slot == null) return
        trackNative(activity, slot, googleLayout, customLayout, owner)
        paintNative(activity, slot, googleLayout, customLayout, owner)
    }

    fun bindBanner(activity: Activity, slot: ViewGroup?) {
        if (slot == null) return
        trackBanner(activity, slot)
        paintBanner(activity, slot)
    }

    private fun trackNative(
        activity: Activity,
        slot: ViewGroup,
        googleLayout: Int,
        customLayout: Int,
        owner: Any
    ) {
        nativeSlots.removeAll { it.slot.get() === slot || it.slot.get() == null }
        nativeSlots += NativeSlot(
            WeakReference(activity),
            WeakReference(slot),
            googleLayout,
            customLayout,
            WeakReference(owner),
            owner.hashCode()
        )
    }

    private fun trackBanner(activity: Activity, slot: ViewGroup) {
        bannerSlots.removeAll { it.slot.get() === slot || it.slot.get() == null }
        bannerSlots += BannerSlot(WeakReference(activity), WeakReference(slot))
    }

    private fun rebindOpenSlots() {
        nativeSlots.removeAll { it.activity.get() == null || it.slot.get() == null }
        bannerSlots.removeAll { it.activity.get() == null || it.slot.get() == null }
        nativeSlots.forEach { binding ->
            val activity = binding.activity.get() ?: return@forEach
            val slot = binding.slot.get() ?: return@forEach
            if (activity.isFinishing || activity.isDestroyed) return@forEach
            natives.remove(binding.ownerKey)?.forEach { runCatching { it.destroy() } }
            val owner = binding.owner.get() ?: activity
            paintNative(activity, slot, binding.googleLayout, binding.customLayout, owner)
        }
        bannerSlots.forEach { binding ->
            val activity = binding.activity.get() ?: return@forEach
            val slot = binding.slot.get() ?: return@forEach
            if (activity.isFinishing || activity.isDestroyed) return@forEach
            paintBanner(activity, slot)
        }
    }

    private fun paintNative(
        activity: Activity,
        slot: ViewGroup,
        googleLayout: Int,
        customLayout: Int,
        owner: Any
    ) {
        if (AdsRepository.needsForceUpdate(activity) || !InstallSource.adsAllowed(activity)) {
            slot.isVisible = false
            slot.removeAllViews()
            return
        }
        val config = AdsRepository.config(activity)
        if (!config.adsEnabled) {
            slot.isVisible = false
            slot.removeAllViews()
            return
        }
        slot.isVisible = true
        destroyBannerChildren(slot)
        slot.removeAllViews()
        AdsSdk.start(activity)
        when {
            config.googleEnabled -> loadGoogleNative(activity, slot, config, googleLayout, customLayout, owner)
            config.webEnabled -> showCustomNative(activity, slot, config, customLayout)
            else -> slot.isVisible = false
        }
    }

    private fun paintBanner(activity: Activity, slot: ViewGroup) {
        if (AdsRepository.needsForceUpdate(activity) || !InstallSource.adsAllowed(activity)) {
            slot.isVisible = false
            destroyBannerChildren(slot)
            slot.removeAllViews()
            return
        }
        val config = AdsRepository.config(activity)
        if (!config.adsEnabled) {
            slot.isVisible = false
            destroyBannerChildren(slot)
            slot.removeAllViews()
            return
        }
        slot.isVisible = true
        destroyBannerChildren(slot)
        slot.removeAllViews()
        AdsSdk.start(activity)
        when {
            config.googleEnabled -> loadGoogleBanner(activity, slot, config)
            config.webEnabled -> showCustomBanner(activity, slot, config)
            else -> slot.isVisible = false
        }
    }

    private fun destroyBannerChildren(slot: ViewGroup) {
        for (i in 0 until slot.childCount) {
            when (val child = slot.getChildAt(i)) {
                is AdView -> runCatching { child.destroy() }
                is AdManagerAdView -> runCatching { child.destroy() }
            }
        }
    }

    private fun displayKey(config: AdsConfig): String =
        listOf(
            config.adsFlag,
            config.googleAds,
            config.webAds,
            config.nativeUnit,
            config.bannerUnit
        ).joinToString("|")

    private fun loadGoogleNative(
        activity: Activity,
        slot: ViewGroup,
        config: AdsConfig,
        googleLayout: Int,
        customLayout: Int,
        owner: Any
    ) {
        if (config.nativeUnit.isBlank()) {
            if (config.webEnabled) showCustomNative(activity, slot, config, customLayout)
            else slot.isVisible = false
            return
        }
        runCatching {
            AdLoader.Builder(activity, config.nativeUnit)
            .forNativeAd { ad ->
                if (activity.isFinishing || activity.isDestroyed) {
                    ad.destroy()
                    return@forNativeAd
                }
                // Drop if this slot already switched to another source while Google was loading.
                if (!AdsRepository.config(activity).googleEnabled) {
                    ad.destroy()
                    return@forNativeAd
                }
                natives.getOrPut(owner.hashCode()) { mutableListOf() }.add(ad)
                AdImpressions.attach(activity, ad, config.nativeUnit)
                slot.removeAllViews()
                slot.addView(inflateGoogleNative(activity, slot, ad, googleLayout))
            }
            .withAdListener(object : AdListener() {
                override fun onAdClicked() {
                    super.onAdClicked()
                    lastNativeClickTime = SystemClock.elapsedRealtime()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (!AdsRepository.config(activity).googleEnabled) return
                    if (config.webEnabled) showCustomNative(activity, slot, config, customLayout)
                    else slot.isVisible = false
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
        }.onFailure { error ->
            Log.e(TAG, "native load crashed", error)
            if (config.webEnabled) showCustomNative(activity, slot, config, customLayout)
            else slot.isVisible = false
        }
    }

    private fun loadGoogleBanner(activity: Activity, slot: ViewGroup, config: AdsConfig) {
        if (config.bannerUnit.isBlank()) {
            if (config.webEnabled) showCustomBanner(activity, slot, config) else slot.isVisible = false
            return
        }
        val unit = config.bannerUnit.trim()
        runCatching {
            val adView = if (unit.startsWith("/")) {
                AdManagerAdView(activity).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = unit
                }
            } else {
                AdView(activity).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = unit
                }
            }
            // Standard 320x50 banner only — never collapsible extras.
            AdImpressions.attach(activity, adView, unit)
            adView.adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (!AdsRepository.config(activity).googleEnabled) return
                    if (config.webEnabled) showCustomBanner(activity, slot, config)
                    else slot.isVisible = false
                }
            }
            slot.addView(
                adView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            if (adView is AdManagerAdView) {
                adView.loadAd(AdManagerAdRequest.Builder().build())
            } else {
                (adView as AdView).loadAd(AdRequest.Builder().build())
            }
        }.onFailure { error ->
            Log.e(TAG, "banner load crashed unit=$unit", error)
            if (config.webEnabled) showCustomBanner(activity, slot, config)
            else slot.isVisible = false
        }
    }

    private fun inflateGoogleNative(
        activity: Activity,
        parent: ViewGroup,
        ad: NativeAd,
        layout: Int
    ): View {
        val container = SafeNativeAdContainer(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val view = LayoutInflater.from(activity).inflate(layout, container, false) as NativeAdView
        view.mediaView = view.findViewById(R.id.ad_media)
        view.headlineView = view.findViewById(R.id.ad_headline)
        view.bodyView = view.findViewById(R.id.ad_body)
        view.iconView = view.findViewById(R.id.ad_app_icon)
        view.callToActionView = view.findViewById(R.id.ad_call_to_action)
        (view.headlineView as TextView).text = ad.headline
        val body = view.bodyView as? TextView
        if (body != null) {
            body.text = ad.body
            body.isVisible = !ad.body.isNullOrBlank()
        }
        val icon = view.iconView as? ImageView
        if (icon != null) {
            val drawable = ad.icon?.drawable
            if (drawable != null) {
                icon.setImageDrawable(drawable)
                icon.isVisible = true
            } else {
                icon.isVisible = false
            }
        }
        val cta = view.callToActionView as TextView
        cta.text = ad.callToAction ?: activity.getString(R.string.funnel_install)
        view.setNativeAd(ad)
        container.addView(view)
        return container
    }

    private fun showCustomNative(
        activity: Activity,
        slot: ViewGroup,
        config: AdsConfig,
        layout: Int
    ) {
        val data = config.randomCustomNative()
        if (data.title.isBlank() && data.image.isBlank() && data.link.isBlank()) {
            slot.isVisible = false
            return
        }
        slot.removeAllViews()
        val container = SafeNativeAdContainer(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val view = LayoutInflater.from(activity).inflate(layout, container, false)
        view.findViewById<TextView>(R.id.custom_native_title)?.text =
            data.title.ifBlank { activity.getString(R.string.funnel_sponsored_title) }
        view.findViewById<TextView>(R.id.custom_native_body)?.text =
            data.description.ifBlank { activity.getString(R.string.funnel_sponsored_body) }
        view.findViewById<TextView>(R.id.custom_native_cta)?.text =
            data.button.ifBlank { activity.getString(R.string.funnel_install) }
        view.findViewById<ImageView>(R.id.custom_native_image)?.let { loadBitmap(activity, data.image, it) }
        view.findViewById<ImageView>(R.id.custom_native_icon)?.let { loadBitmap(activity, data.icon, it) }
        val open = object : View.OnClickListener {
            private var lastClickTime = 0L
            override fun onClick(v: View) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastClickTime < SafeNativeAdContainer.THROTTLE_MS ||
                    now - lastNativeClickTime < SafeNativeAdContainer.THROTTLE_MS
                ) return
                lastClickTime = now
                lastNativeClickTime = now
                WebAds.openCustomNative(activity, AdsRepository.config(activity))
            }
        }
        view.setOnClickListener(open)
        view.findViewById<View>(R.id.custom_native_cta)?.setOnClickListener(open)
        container.addView(view)
        slot.addView(container)
    }

    private fun showCustomBanner(activity: Activity, slot: ViewGroup, config: AdsConfig) {
        val data = config.randomCustomBanner()
        if (data.image.isBlank() && data.link.isBlank()) {
            slot.isVisible = false
            return
        }
        slot.removeAllViews()
        val view = LayoutInflater.from(activity).inflate(R.layout.view_custom_banner, slot, false)
        val image = view.findViewById<ImageView>(R.id.custom_banner_image)
        loadBitmap(activity, data.image, image)
        var lastBannerClickTime = 0L
        view.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBannerClickTime < SafeNativeAdContainer.THROTTLE_MS) return@setOnClickListener
            lastBannerClickTime = now
            WebAds.openCustomBanner(activity, AdsRepository.config(activity))
        }
        slot.addView(view)
    }

    private fun loadBitmap(activity: Activity, url: String, target: ImageView) {
        if (url.isBlank() || activity !is LifecycleOwner) return
        activity.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { MediaThumbs.loadImage(url) }
            if (bitmap != null && !activity.isFinishing && !activity.isDestroyed) {
                target.setImageBitmap(bitmap)
            }
        }
    }
}

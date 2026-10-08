package com.example.ffdiamond.ads

import org.json.JSONArray
import org.json.JSONObject

data class AdsConfig(
    val adsFlag: Boolean,
    val googleAds: Boolean,
    val webAds: Boolean,
    val interstitialCount: Int,
    val interstitialAds: Boolean,
    val appOpenAds: Boolean,
    val appOpenCount: Int,
    val homeSwipeAds: Boolean,
    val homeSwipeCount: Int,
    val configUrl: String,
    val minVersionCode: Int,
    val updateUrl: String,
    val admobAppId: String,
    val appOpenUnit: String,
    val interstitialUnit: String,
    val nativeUnit: String,
    val bannerUnit: String,
    val webLinks: List<String>,
    val nativePool: NativePool,
    val bannerPool: List<CustomBanner>,
    val blogUrl: String,
    val facebookAppId: String,
    val facebookClientToken: String,
    /** Custom Tabs together when a funnel Next/Done/CTA shows web ads. -1 = 1 tab. 0 = none. */
    val webAdsCount: Int,
    /** Play referrer prefixes, comma-separated (`gclid=`, `gbraid=`, or both). */
    val gclId: String,
    /** 1 = check Play referrer. Anything else = organic / no ads. */
    val installReferrerControl: Int
) {
    val adsEnabled: Boolean get() = adsFlag && (googleAds || webAds)
    val googleEnabled: Boolean get() = adsFlag && googleAds
    val webEnabled: Boolean get() = adsFlag && webAds
    val interstitialEnabled: Boolean get() = googleEnabled && interstitialAds
    val appOpenEnabled: Boolean get() = googleEnabled && appOpenAds
    val homeSwipeEnabled: Boolean get() = adsFlag && webAds && homeSwipeAds && homeSwipeCount > 0

    fun randomCustomNative(): CustomNative = nativePool.pick()

    fun randomCustomBanner(): CustomBanner =
        bannerPool.randomOrNull() ?: CustomBanner()

    val customBanner: CustomBanner get() = randomCustomBanner()

    /** Every http URL custom native can open on click (pool + creatives). */
    fun customNativeClickLinks(): List<String> = nativePool.allHttpLinks()

    /** Every http URL custom banner can open on click. */
    fun customBannerClickLinks(): List<String> =
        bannerPool.map { it.link }.filter { it.startsWith("http") }.distinct()
}

data class CustomNative(
    val icon: String = "",
    val image: String = "",
    val title: String = "",
    val description: String = "",
    val button: String = "",
    val link: String = ""
)

data class NativePool(
    val creatives: List<CustomNative> = emptyList(),
    val icons: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val titles: List<String> = emptyList(),
    val descriptions: List<String> = emptyList(),
    val buttons: List<String> = emptyList(),
    val links: List<String> = emptyList()
) {
    fun pick(): CustomNative {
        if (creatives.size > 1 && images.isEmpty() && titles.isEmpty()) {
            return creatives.random()
        }
        val base = creatives.randomOrNull() ?: CustomNative()
        return CustomNative(
            icon = icons.randomOrNull() ?: base.icon,
            image = images.randomOrNull() ?: base.image,
            title = titles.randomOrNull() ?: base.title,
            description = descriptions.randomOrNull() ?: base.description,
            button = buttons.randomOrNull() ?: base.button.ifBlank { "Install" },
            link = links.randomOrNull() ?: base.link
        )
    }

    fun allHttpLinks(): List<String> {
        val fromPool = links.filter { it.startsWith("http") }
        val fromCreatives = creatives.map { it.link }.filter { it.startsWith("http") }
        return (fromPool + fromCreatives).distinct()
    }
}

data class CustomBanner(
    val image: String = "",
    val link: String = ""
)

object AdsConfigParser {

    fun parse(raw: String): AdsConfig {
        val root = JSONObject(raw)
        val units = root.optJSONObject("units") ?: JSONObject()
        return AdsConfig(
            adsFlag = root.optBoolean("ads_flag", true),
            googleAds = root.optBoolean("google_ads", true),
            webAds = root.optBoolean("web_ads", true),
            interstitialCount = root.optInt("interstitial_count", 1),
            interstitialAds = root.optBoolean("interstitial", true),
            appOpenAds = root.optBoolean("app_open", true),
            appOpenCount = root.optInt("app_open_count", 1),
            homeSwipeAds = root.optBoolean("home_swipe", true),
            homeSwipeCount = root.optInt("home_swipe_count", 1),
            configUrl = root.optString("config_url"),
            minVersionCode = root.optInt("min_version_code", 0),
            updateUrl = root.optString("update_url"),
            admobAppId = root.optString("admob_app_id"),
            appOpenUnit = units.optString("app_open"),
            interstitialUnit = units.optString("interstitial"),
            nativeUnit = units.optString("native"),
            bannerUnit = units.optString("banner"),
            webLinks = stringList(root.optJSONArray("web_links")),
            nativePool = parseNativePool(root.opt("custom_native")),
            bannerPool = parseBanners(root.opt("custom_banner")),
            blogUrl = root.optString("blog_url"),
            facebookAppId = root.optString("facebook_app_id").trim(),
            facebookClientToken = root.optString("facebook_client_token").trim(),
            webAdsCount = if (root.has("web_ads_count")) root.optInt("web_ads_count") else -1,
            gclId = firstString(root, "gcl_id", "GCL ID"),
            installReferrerControl = firstInt(root, "control_install_referrer", "Control InstallReferrerClient")
        )
    }

    private fun firstString(root: JSONObject, vararg keys: String): String {
        for (key in keys) {
            if (!root.has(key)) continue
            val value = root.optString(key).trim()
            if (value.isNotEmpty()) return value
        }
        return ""
    }

    private fun firstInt(root: JSONObject, vararg keys: String): Int {
        for (key in keys) {
            if (!root.has(key)) continue
            val asInt = root.optInt(key, Int.MIN_VALUE)
            if (asInt != Int.MIN_VALUE) return asInt
            return root.optString(key).trim().toIntOrNull() ?: 0
        }
        return 0
    }

    private fun parseNativePool(node: Any?): NativePool {
        when (node) {
            is JSONArray -> {
                val creatives = buildList {
                    for (i in 0 until node.length()) {
                        val item = node.optJSONObject(i) ?: continue
                        add(parseNative(item))
                    }
                }
                return NativePool(creatives = creatives)
            }
            is JSONObject -> {
                val items = node.optJSONArray("items")
                val creatives = if (items != null) {
                    buildList {
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            add(parseNative(item))
                        }
                    }
                } else {
                    listOf(parseNative(node))
                }
                return NativePool(
                    creatives = creatives,
                    icons = stringList(node.optJSONArray("icons")).ifEmpty {
                        listOfNotNull(node.optString("icon").takeIf { it.isNotBlank() })
                    },
                    images = stringList(node.optJSONArray("images")).ifEmpty {
                        listOfNotNull(node.optString("image").takeIf { it.isNotBlank() })
                    },
                    titles = stringList(node.optJSONArray("titles")).ifEmpty {
                        listOfNotNull(node.optString("title").takeIf { it.isNotBlank() })
                    },
                    descriptions = stringList(node.optJSONArray("descriptions")).ifEmpty {
                        listOfNotNull(node.optString("description").takeIf { it.isNotBlank() })
                    },
                    buttons = stringList(node.optJSONArray("buttons")).ifEmpty {
                        listOfNotNull(node.optString("button").takeIf { it.isNotBlank() })
                    },
                    links = stringList(node.optJSONArray("links")).ifEmpty {
                        listOfNotNull(node.optString("link").takeIf { it.isNotBlank() })
                    }
                )
            }
            else -> return NativePool()
        }
    }

    private fun parseBanners(node: Any?): List<CustomBanner> {
        when (node) {
            is JSONArray -> {
                return buildList {
                    for (i in 0 until node.length()) {
                        val item = node.optJSONObject(i) ?: continue
                        val banner = CustomBanner(
                            image = item.optString("image"),
                            link = item.optString("link")
                        )
                        if (banner.image.isNotBlank() || banner.link.isNotBlank()) add(banner)
                    }
                }
            }
            is JSONObject -> {
                val images = stringList(node.optJSONArray("images")).ifEmpty {
                    listOfNotNull(node.optString("image").takeIf { it.isNotBlank() })
                }
                val links = stringList(node.optJSONArray("links")).ifEmpty {
                    listOfNotNull(node.optString("link").takeIf { it.isNotBlank() })
                }
                if (images.isEmpty() && links.isEmpty()) return emptyList()
                val count = maxOf(images.size, links.size, 1)
                return List(count) { index ->
                    CustomBanner(
                        image = images.getOrElse(index) { images.randomOrNull().orEmpty() },
                        link = links.getOrElse(index) { links.randomOrNull().orEmpty() }
                    )
                }
            }
            else -> return emptyList()
        }
    }

    private fun parseNative(obj: JSONObject) = CustomNative(
        icon = obj.optString("icon"),
        image = obj.optString("image"),
        title = obj.optString("title"),
        description = obj.optString("description"),
        button = obj.optString("button", "Install"),
        link = obj.optString("link")
    )

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotBlank()) add(value)
            }
        }
    }
}

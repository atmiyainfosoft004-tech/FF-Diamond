package com.example.ffdiamond.ads

import android.content.Context
import android.os.Bundle
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.BaseAdView
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.firebase.analytics.FirebaseAnalytics
import android.util.Log

object AdImpressions {

    const val BANNER = "banner"
    const val NATIVE = "native"
    const val INTERSTITIAL = "interstitial"
    const val APP_OPEN = "app_open"

    fun attach(context: Context, ad: BaseAdView, adUnit: String) {
        ad.setOnPaidEventListener(listener(context, BANNER, adUnit) { ad.responseInfo })
    }

    fun attach(context: Context, ad: NativeAd, adUnit: String) {
        ad.setOnPaidEventListener(listener(context, NATIVE, adUnit) { ad.responseInfo })
    }

    fun attach(context: Context, ad: InterstitialAd, adUnit: String) {
        ad.setOnPaidEventListener(listener(context, INTERSTITIAL, adUnit) { ad.responseInfo })
    }

    fun attach(context: Context, ad: AppOpenAd, adUnit: String) {
        ad.setOnPaidEventListener(listener(context, APP_OPEN, adUnit) { ad.responseInfo })
    }

    private fun listener(
        context: Context,
        format: String,
        adUnit: String,
        responseInfo: () -> ResponseInfo?
    ): OnPaidEventListener {
        val app = context.applicationContext
        return OnPaidEventListener { value -> send(app, value, format, adUnit, responseInfo()) }
    }

    internal fun revenue(valueMicros: Long): Double = valueMicros / 1_000_000.0

    private fun send(
        context: Context,
        adValue: AdValue,
        format: String,
        adUnit: String,
        responseInfo: ResponseInfo?
    ) {
        val revenue = revenue(adValue.valueMicros)
        val bundle = Bundle().apply {
            putDouble(FirebaseAnalytics.Param.VALUE, revenue)
            putDouble("value", revenue)
            putString(FirebaseAnalytics.Param.CURRENCY, adValue.currencyCode)
            putString("currency", adValue.currencyCode)
            putString("ad_unit", format)
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnit)
            putString(FirebaseAnalytics.Param.AD_PLATFORM, "AdMob")
            putString(FirebaseAnalytics.Param.AD_FORMAT, format)
            responseInfo?.loadedAdapterResponseInfo?.adSourceName?.let { source ->
                putString(FirebaseAnalytics.Param.AD_SOURCE, source)
            }
        }
        Log.i(
            FirebaseBoot.TAG,
            "ad_impression format=$format unit=$adUnit revenue=$revenue ${adValue.currencyCode} source=${responseInfo?.loadedAdapterResponseInfo?.adSourceName}"
        )
        runCatching {
            FirebaseAnalytics.getInstance(context).logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, bundle)
            Log.i(FirebaseBoot.TAG, "ad_impression sent to Analytics")
        }.onFailure { Log.e(FirebaseBoot.TAG, "ad_impression Analytics failed", it) }
    }
}






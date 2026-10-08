package com.example.ffdiamond.guide

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.example.ffdiamond.R
import com.example.ffdiamond.ads.AdsBinder
import com.example.ffdiamond.databinding.ActivitySettingsBinding
import com.example.ffdiamond.util.applySystemBarInsetsAsPadding
import com.example.ffdiamond.util.setOnSafeClickListener

class SettingsActivity : GuideActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.llSettingsRoot.applySystemBarInsetsAsPadding()
        bindHeader(binding.header, R.string.ff_settings)
        AdsBinder.bindBanner(this, binding.adBanner.root)

        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull().orEmpty()
        binding.txtVersion.text = getString(R.string.ff_settings_version, version)

        binding.rowNotifications.setOnSafeClickListener { openNotificationSettings() }
        binding.rowShare.setOnSafeClickListener { shareApp() }
        binding.rowRate.setOnSafeClickListener { rateApp() }
    }

    private fun storeUrl(): String = "https://play.google.com/store/apps/details?id=$packageName"

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        }
        runCatching { startActivity(intent) }
    }

    private fun shareApp() {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.ff_share_text, storeUrl()))
        runCatching { startActivity(Intent.createChooser(send, getString(R.string.ff_settings_share))) }
    }

    private fun rateApp() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (_: ActivityNotFoundException) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl()))) }
        }
    }
}

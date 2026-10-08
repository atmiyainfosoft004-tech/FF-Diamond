package com.example.ffdiamond.guide

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.ffdiamond.R
import com.example.ffdiamond.ads.AdsBinder
import com.example.ffdiamond.ads.AdsGate
import com.example.ffdiamond.ads.AdsRepository
import com.example.ffdiamond.ads.PushNotifications
import com.example.ffdiamond.ads.WebAds
import com.example.ffdiamond.databinding.FragmentDiamondHomeBinding
import com.example.ffdiamond.databinding.ItemHomeTileBinding
import com.example.ffdiamond.util.applySystemBarInsetsAsPadding
import com.example.ffdiamond.util.enableMarqueeLabels
import com.example.ffdiamond.util.setOnSafeClickListener
import com.example.ffdiamond.util.startActivityWithSlide

class DiamondHomeFragment : Fragment() {

    private var binding: FragmentDiamondHomeBinding? = null
    private var isNavigating = false
    private var rationaleDialog: Dialog? = null
    private var hasCheckedNotificationsOnEntry = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            context?.let { PushNotifications.start(it) }
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Return from system settings
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val created = FragmentDiamondHomeBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding ?: return
        binding.root.applySystemBarInsetsAsPadding()
        binding.root.enableMarqueeLabels()
        AdsBinder.bindNative(
            requireActivity(),
            binding.adNative.root,
            owner = this
        )

        binding.tileCharacters.setOnSafeClickListener { openGallery(CharactersActivity::class.java) }
        bindTile(binding.tilePets, R.drawable.img_home_pets, R.string.ff_pets) {
            openGallery(PetsActivity::class.java)
        }
        bindTile(binding.tileBundles, R.drawable.img_home_bundles, R.string.ff_bundles) {
            openGallery(BundlesActivity::class.java)
        }
        bindTile(binding.tileWeapons, R.drawable.img_home_weapons, R.string.ff_weapons) {
            openGallery(WeaponsActivity::class.java)
        }
        bindTile(binding.tileVehicles, R.drawable.img_home_vehicles, R.string.ff_vehicles) {
            openGallery(VehiclesActivity::class.java)
        }
        bindTile(binding.tilePlay1, R.drawable.img_home_play_game, R.string.ff_play_game, adBadge = true) {
            playGame()
        }
        bindTile(binding.tilePlay2, R.drawable.img_home_play_game, R.string.ff_play_game, adBadge = true) {
            playGame()
        }
        bindTile(binding.tileEmotes, R.drawable.img_home_emotes, R.string.ff_emotes) {
            openGallery(EmotesActivity::class.java)
        }
        bindTile(binding.tileCalculator, R.drawable.img_home_calculator, R.string.ff_calculator) {
            openGallery(DiamondCalculatorActivity::class.java)
        }
        bindTile(binding.tileParachutes, R.drawable.img_home_parachutes, R.string.ff_parachutes) {
            openGallery(ParachutesActivity::class.java)
        }
        bindTile(binding.tileTips, R.drawable.img_home_tips, R.string.ff_tips_tricks) {
            openGallery(TipsTricksActivity::class.java)
        }
        binding.btnSettings.setOnSafeClickListener { openSettings() }

        checkNotificationPermissionOnEntry()
    }

    private fun bindTile(
        tile: ItemHomeTileBinding,
        @DrawableRes imageRes: Int,
        @StringRes labelRes: Int,
        adBadge: Boolean = false,
        onClick: () -> Unit
    ) {
        tile.imgTile.setImageResource(imageRes)
        tile.txtTile.setText(labelRes)
        tile.txtTile.isSelected = true
        tile.txtAdBadge.isVisible = adBadge
        tile.root.setOnSafeClickListener { onClick() }
    }

    private fun checkNotificationPermissionOnEntry() {
        if (hasCheckedNotificationsOnEntry) return
        hasCheckedNotificationsOnEntry = true

        val ctx = context ?: return
        if (PushNotifications.areNotificationsEnabled(ctx)) return

        view?.postDelayed({
            if (!isAdded || isDetached) return@postDelayed
            showNotificationRationale()
        }, 600)
    }

    private fun showNotificationRationale() {
        val ctx = context ?: return
        val act = activity ?: return
        if (act.isFinishing || act.isDestroyed) return

        rationaleDialog?.dismiss()
        rationaleDialog = NotificationRationaleDialog.show(
            context = ctx,
            onEnable = {
                handleEnableNotification()
            }
        )
    }

    private fun handleEnableNotification() {
        val act = activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val canShowDialog = ActivityCompat.shouldShowRequestPermissionRationale(
                act,
                Manifest.permission.POST_NOTIFICATIONS
            ) || !PushNotifications.hasAskedOnce(act)

            if (canShowDialog) {
                PushNotifications.setAskedOnce(act)
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings()
            }
        } else {
            openNotificationSettings()
        }
    }

    private fun openNotificationSettings() {
        val ctx = context ?: return
        val pkg = ctx.packageName
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", pkg, null)
            }
        }
        runCatching {
            settingsLauncher.launch(intent)
        }.onFailure {
            runCatching {
                settingsLauncher.launch(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", pkg, null)
                    }
                )
            }
        }
    }

    fun resetScrollToTop() {
        val sv = binding?.svContent ?: return
        sv.post {
            sv.scrollTo(0, 0)
            sv.fullScroll(View.FOCUS_UP)
        }
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
        resetScrollToTop()
    }

    private fun openGallery(screen: Class<*>) {
        if (isNavigating) return
        isNavigating = true
        AdsGate.onInterOrWeb(requireActivity()) {
            if (!isAdded) {
                isNavigating = false
                return@onInterOrWeb
            }
            requireActivity().startActivityWithSlide(Intent(requireContext(), screen))
        }
    }

    private fun openSettings() {
        if (!isAdded || isNavigating) return
        requireActivity().startActivityWithSlide(Intent(requireContext(), SettingsActivity::class.java))
    }

    private fun playGame() {
        if (!isAdded) return
        val activity = requireActivity()
        val url = AdsRepository.config(activity).webLinks
            .filter { it.startsWith("http") }
            .randomOrNull() ?: return
        WebAds.open(activity, url)
    }

    override fun onDestroyView() {
        isNavigating = false
        AdsBinder.release(this)
        rationaleDialog?.let {
            if (it.isShowing) {
                runCatching { it.dismiss() }
            }
        }
        rationaleDialog = null
        binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        rationaleDialog?.let {
            if (it.isShowing) {
                runCatching { it.dismiss() }
            }
        }
        rationaleDialog = null
        super.onDestroy()
    }
}

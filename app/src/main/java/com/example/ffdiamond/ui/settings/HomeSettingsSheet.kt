package com.example.ffdiamond.ui.settings

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import com.example.ffdiamond.R
import com.example.ffdiamond.databinding.SheetHomeSettingsBinding
import com.example.ffdiamond.databinding.ViewSheetRowBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** The sheet raised by a long press on empty space on the home screen. */
class HomeSettingsSheet : BottomSheetDialogFragment() {

    private var onWidgets: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        val binding = SheetHomeSettingsBinding.inflate(layoutInflater)

        binding.rowWallpapers.configure(
            R.drawable.ic_wallpaper,
            R.string.home_wallpapers,
            R.string.home_wallpapers_body
        ) {
            openWallpaperPicker()
            dismiss()
        }

        binding.rowThemes.configure(
            R.drawable.ic_palette,
            R.string.home_themes,
            R.string.home_themes_body
        ) {
            HomeOptionsSheet.show(parentFragmentManager, HomeOptionsSheet.Section.THEMES)
            dismiss()
        }

        binding.rowWidgets.configure(
            R.drawable.ic_widgets,
            R.string.home_widgets,
            R.string.home_widgets_body
        ) {
            onWidgets?.invoke()
            dismiss()
        }

        binding.rowHomeSettings.configure(
            R.drawable.ic_tune,
            R.string.home_grid_settings,
            R.string.home_grid_settings_body
        ) {
            HomeOptionsSheet.show(parentFragmentManager, HomeOptionsSheet.Section.HOME)
            dismiss()
        }

        dialog.setContentView(binding.root)
        return dialog
    }

    private fun ViewSheetRowBinding.configure(
        @DrawableRes icon: Int,
        @StringRes title: Int,
        @StringRes body: Int,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null
    ) {
        sheetRowIcon.setImageResource(icon)
        sheetRowTitle.setText(title)
        sheetRowBody.setText(body)
        root.isEnabled = enabled
        root.alpha = if (enabled) 1f else DISABLED_ALPHA
        if (enabled && onClick != null) {
            root.setOnClickListener { onClick() }
        } else {
            root.isClickable = false
        }
    }

    /**
     * The system picker, not our own. Wallpapers are an OS-level setting and every device ships a
     * picker that already knows about live wallpapers, crops and lock-screen variants.
     */
    private fun openWallpaperPicker() {
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SET_WALLPAPER),
            getString(R.string.home_wallpapers)
        )
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(
                requireContext(),
                R.string.error_no_wallpaper_picker,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        private const val TAG = "home_settings"
        private const val DISABLED_ALPHA = 0.4f

        fun show(manager: FragmentManager, onWidgets: (() -> Unit)? = null) {
            if (manager.findFragmentByTag(TAG) != null) return
            HomeSettingsSheet().apply { this.onWidgets = onWidgets }.show(manager, TAG)
        }
    }
}

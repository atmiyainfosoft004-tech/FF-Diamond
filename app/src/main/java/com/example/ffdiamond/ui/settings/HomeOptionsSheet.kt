package com.example.ffdiamond.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.example.ffdiamond.R
import com.example.ffdiamond.data.HomeSettings
import com.example.ffdiamond.data.LauncherPreferences
import com.example.ffdiamond.databinding.SheetHomeOptionsBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The second level of the home-screen menu.
 *
 * "Themes" and "Home screen settings" are one class because they are the same thing: a short list
 * of switches over the preferences that shape the workspace. Every change writes straight to
 * DataStore and the launcher reacts to the flow, so there is no apply button and no state to hand
 * back to the caller.
 */
class HomeOptionsSheet : BottomSheetDialogFragment() {

    enum class Section { THEMES, HOME }

    private val section: Section
        get() = Section.valueOf(requireArguments().getString(ARG_SECTION) ?: Section.HOME.name)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        val binding = SheetHomeOptionsBinding.inflate(layoutInflater)
        val context = requireContext().applicationContext
        val themes = section == Section.THEMES

        binding.sheetTitle.setText(
            if (themes) R.string.home_themes else R.string.home_grid_settings
        )
        binding.gridSection.isVisible = !themes
        binding.switchIconLabels.isVisible = !themes
        binding.switchThemedIcons.isVisible = themes
        binding.optionBody.setText(
            if (themes) R.string.themed_icons_body else R.string.icon_labels_body
        )

        HomeSettings.GRID_OPTIONS.forEach { (columns, rows) ->
            binding.gridOptions.addView(
                MaterialButton(
                    requireContext(),
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
                ).apply {
                    id = gridOptionId(columns, rows)
                    text = getString(R.string.grid_size_option, columns, rows)
                    minHeight = resources.getDimensionPixelSize(R.dimen.grid_option_height)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
            )
        }

        // Read once and check the current values before wiring listeners, so restoring state does
        // not look like a user edit and write the same values straight back.
        lifecycleScope.launch {
            val current = LauncherPreferences.homeSettings(context).first()
            binding.gridOptions.check(gridOptionId(current.columns, current.rows))
            binding.switchIconLabels.isChecked = current.showLabels
            binding.switchThemedIcons.isChecked = current.themedIcons

            binding.gridOptions.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val option = HomeSettings.GRID_OPTIONS
                    .firstOrNull { gridOptionId(it.first, it.second) == checkedId }
                    ?: return@addOnButtonCheckedListener
                lifecycleScope.launch {
                    LauncherPreferences.setGrid(context, option.first, option.second)
                }
            }
            binding.switchIconLabels.setOnCheckedChangeListener { _, checked ->
                lifecycleScope.launch { LauncherPreferences.setShowLabels(context, checked) }
            }
            binding.switchThemedIcons.setOnCheckedChangeListener { _, checked ->
                lifecycleScope.launch { LauncherPreferences.setThemedIcons(context, checked) }
            }
        }

        dialog.setContentView(binding.root)
        return dialog
    }

    companion object {
        private const val TAG = "home_options"
        private const val ARG_SECTION = "section"

        /**
         * A stable view id per grid shape, so the toggle group can check the saved one. Offset
         * well clear of the range View.generateViewId hands out for the dialog's other children.
         */
        private const val GRID_ID_BASE = 0x00AA0000

        private fun gridOptionId(columns: Int, rows: Int) = GRID_ID_BASE + columns * 100 + rows

        fun show(manager: FragmentManager, section: Section) {
            if (manager.findFragmentByTag(TAG) != null) return
            HomeOptionsSheet()
                .apply { arguments = bundleOf(ARG_SECTION to section.name) }
                .show(manager, TAG)
        }
    }
}

package com.example.ffdiamond.ui.folder

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ffdiamond.R
import com.example.ffdiamond.databinding.SheetFolderAddAppsBinding
import com.example.ffdiamond.icons.IconCache
import com.example.ffdiamond.model.HomeItem
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * Picks an app already on the home screen and moves it into the open folder.
 *
 * The drawer is not built yet, so the only apps the user can add are ones they can already see —
 * desktop, dock, or another folder. Tapping one writes through the repository; reconciliation
 * dissolves a folder that this emptied down to a single member.
 */
class FolderAddAppsSheet : BottomSheetDialogFragment() {

    private var candidates: List<HomeItem.App> = emptyList()
    private var iconCache: IconCache? = null
    private var themed = false
    private var onPick: ((HomeItem.App) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        val binding = SheetFolderAddAppsBinding.inflate(layoutInflater)
        binding.addAppsList.layoutManager = LinearLayoutManager(requireContext())
        binding.addAppsList.layoutParams.height =
            (resources.displayMetrics.heightPixels * 0.45f).toInt()
        binding.addAppsList.adapter = Adapter(candidates) { app ->
            onPick?.invoke(app)
            dismiss()
        }
        dialog.setContentView(binding.root)
        return dialog
    }

    private inner class Adapter(
        private val items: List<HomeItem.App>,
        private val onClick: (HomeItem.App) -> Unit
    ) : RecyclerView.Adapter<Adapter.Holder>() {

        inner class Holder(parent: ViewGroup) : RecyclerView.ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.view_sheet_row, parent, false)
        ) {
            val icon: ImageView = itemView.findViewById(R.id.sheet_row_icon)
            val title: TextView = itemView.findViewById(R.id.sheet_row_title)
            val body: TextView = itemView.findViewById(R.id.sheet_row_body)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(parent)

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val app = items[position]
            holder.title.text = app.title
            holder.body.text = app.info.packageName
            holder.icon.imageTintList = null
            holder.icon.setImageBitmap(iconCache?.placeholderBitmap())
            val cache = iconCache
            if (cache != null) {
                holder.itemView.tag = app.id
                lifecycleScope.launch {
                    val bitmap = cache.get(app.info, themed)
                    if (holder.itemView.tag == app.id) holder.icon.setImageBitmap(bitmap)
                }
            }
            holder.itemView.setOnClickListener { onClick(app) }
        }
    }

    companion object {
        private const val TAG = "folder_add_apps"

        fun show(
            fragmentManager: FragmentManager,
            candidates: List<HomeItem.App>,
            iconCache: IconCache,
            themed: Boolean,
            onPick: (HomeItem.App) -> Unit
        ) {
            val existing = fragmentManager.findFragmentByTag(TAG) as? FolderAddAppsSheet
            existing?.dismiss()
            FolderAddAppsSheet().apply {
                this.candidates = candidates.sortedBy { it.title.lowercase() }
                this.iconCache = iconCache
                this.themed = themed
                this.onPick = onPick
            }.show(fragmentManager, TAG)
        }
    }
}

package com.example.ffdiamond.widget

import android.app.Dialog
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ffdiamond.R
import com.example.ffdiamond.databinding.SheetWidgetPickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** Lists installed home-screen widgets so one can be bound onto the grid. */
class WidgetPickerSheet : BottomSheetDialogFragment() {

    private var providers: List<AppWidgetProviderInfo> = emptyList()
    private var onPick: ((AppWidgetProviderInfo) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        val binding = SheetWidgetPickerBinding.inflate(layoutInflater)
        binding.widgetPickerList.layoutManager = LinearLayoutManager(requireContext())
        binding.widgetPickerList.layoutParams.height =
            (resources.displayMetrics.heightPixels * 0.55f).toInt()
        binding.widgetPickerList.adapter = Adapter(providers) { info ->
            val pick = onPick
            dismiss()
            pick?.invoke(info)
        }
        dialog.setContentView(binding.root)
        return dialog
    }

    private inner class Adapter(
        private val items: List<AppWidgetProviderInfo>,
        private val onClick: (AppWidgetProviderInfo) -> Unit
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
            val info = items[position]
            val pm = holder.itemView.context.packageManager
            holder.title.text = info.loadLabel(pm)
            holder.body.text = info.provider.packageName
            holder.icon.imageTintList = null
            holder.icon.setImageDrawable(
                info.loadPreviewImage(holder.itemView.context, 0)
                    ?: info.loadIcon(holder.itemView.context, 0)
                    ?: pm.getApplicationIcon(info.provider.packageName)
            )
            holder.itemView.setOnClickListener { onClick(info) }
        }
    }

    companion object {
        private const val TAG = "widget_picker"

        fun show(
            manager: FragmentManager,
            providers: List<AppWidgetProviderInfo>,
            onPick: (AppWidgetProviderInfo) -> Unit
        ) {
            if (manager.findFragmentByTag(TAG) != null) return
            WidgetPickerSheet().apply {
                this.providers = providers
                this.onPick = onPick
            }.show(manager, TAG)
        }
    }
}

package com.example.ffdiamond.ui.drawer

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.BitmapDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import com.example.ffdiamond.R
import com.example.ffdiamond.apps.AppLauncher
import com.example.ffdiamond.apps.AppShortcuts
import com.example.ffdiamond.apps.SuggestedApps
import com.example.ffdiamond.icons.IconCache
import com.example.ffdiamond.model.AppInfo
import com.example.ffdiamond.util.Motion
import com.example.ffdiamond.widget.GoogleIntents
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Search screens 04 (empty) and 05 (typing). Lives on top of the drawer chrome for as long as the
 * field has focus or text; closing it is Back, Home, or the clear-and-dismiss path from the drawer.
 */
class SearchPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val scroll: ScrollView
    private val empty: View
    private val results: View
    private val suggestedGrid: LinearLayout
    private val topicsChips: ChipGroup
    private val appsCard: View
    private val appsGrid: LinearLayout
    private val shortcutsCard: View
    private val shortcutsList: LinearLayout
    private val webRow: TextView
    private val settingsCard: View
    private val settingsList: LinearLayout
    private val fieldBar: View
    private val field: EditText
    private val clear: ImageButton

    private lateinit var iconCache: IconCache
    private lateinit var scope: CoroutineScope
    private lateinit var appsProvider: () -> List<AppInfo>

    private var apps: List<AppInfo> = emptyList()
    private var themed = false
    private var iconJobs = mutableListOf<Job>()

    val isOpen: Boolean get() = isVisible

    var pendingClearOnReturn = false
        private set

    init {
        LayoutInflater.from(context).inflate(R.layout.view_search_panel, this, true)
        scroll = findViewById(R.id.search_scroll)
        empty = findViewById(R.id.search_empty)
        results = findViewById(R.id.search_results)
        suggestedGrid = findViewById(R.id.suggested_grid)
        topicsChips = findViewById(R.id.topics_chips)
        appsCard = findViewById(R.id.apps_card)
        appsGrid = findViewById(R.id.apps_grid)
        shortcutsCard = findViewById(R.id.shortcuts_card)
        shortcutsList = findViewById(R.id.shortcuts_list)
        webRow = findViewById(R.id.web_search_row)
        settingsCard = findViewById(R.id.settings_card)
        settingsList = findViewById(R.id.settings_list)
        fieldBar = findViewById(R.id.search_field_bar)
        field = findViewById(R.id.search_field)
        clear = findViewById(R.id.search_clear)

        isVisible = false
        field.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            clear.isVisible = query.isNotEmpty()
            render(query)
        }
        field.setOnEditorActionListener { _, actionId, event ->
            val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isSearchAction) {
                val query = field.text?.toString()?.trim().orEmpty()
                if (query.isNotEmpty()) {
                    performDirectSearch(query)
                }
                true
            } else {
                false
            }
        }
        field.setOnClickListener { showKeyboard() }
        fieldBar.setOnClickListener {
            field.requestFocus()
            showKeyboard()
        }
        clear.setOnClickListener {
            field.text = null
            field.requestFocus()
            showKeyboard()
        }
        fieldBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                ViewCompat.getRootWindowInsets(this)?.let { applySearchInsets(it) }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            applySearchInsets(insets)
            insets
        }
    }

    private fun applySearchInsets(insets: WindowInsetsCompat) {
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        val extra = resources.getDimensionPixelSize(R.dimen.space_sm)
        val fieldH = fieldBar.height.takeIf { it > 0 }
            ?: resources.getDimensionPixelSize(R.dimen.drawer_search_field_height)
        val bottomGap = maxOf(ime.bottom + extra, bars.bottom, extra)
        fieldBar.updateLayoutParams<MarginLayoutParams> {
            bottomMargin = bottomGap
        }
        scroll.setPadding(
            scroll.paddingLeft,
            bars.top + extra,
            scroll.paddingRight,
            fieldH + bottomGap + extra
        )
    }

    fun attach(iconCache: IconCache, scope: CoroutineScope, appsProvider: () -> List<AppInfo>) {
        this.iconCache = iconCache
        this.scope = scope
        this.appsProvider = appsProvider
    }

    fun setApps(apps: List<AppInfo>, themed: Boolean) {
        this.apps = apps
        this.themed = themed
        if (isOpen) render(field.text?.toString().orEmpty())
    }

    fun open() {
        isVisible = true
        alpha = 0f
        field.text = null
        render("")
        field.requestFocus()
        requestApplyInsets()
        ViewCompat.getRootWindowInsets(this)?.let { applySearchInsets(it) }
        showKeyboard()
        val duration = Motion.duration(context, Motion.DURATION_STANDARD)
        if (duration == 0L) {
            alpha = 1f
        } else {
            animate().alpha(1f).setDuration(duration).setInterpolator(Motion.STANDARD).start()
        }
    }

    fun close(immediate: Boolean = false) {
        if (!isOpen) return
        hideKeyboard()
        field.clearFocus()
        val duration = if (immediate) 0L else Motion.duration(context, Motion.DURATION_STANDARD)
        if (duration == 0L) {
            hide()
        } else {
            animate()
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(Motion.STANDARD)
                .withEndAction { hide() }
                .start()
        }
    }

    private fun hide() {
        animate().cancel()
        isVisible = false
        alpha = 1f
        field.text = null
        pendingClearOnReturn = false
        cancelIconJobs()
    }

    private fun render(query: String) {
        if (!::iconCache.isInitialized) return
        val needle = query.trim()
        if (needle.isEmpty()) {
            empty.isVisible = true
            results.isVisible = false
            bindSuggested()
            bindTopicChips()
        } else {
            empty.isVisible = false
            results.isVisible = true
            bindResults(needle)
        }
    }

    private fun bindSuggested() {
        suggestedGrid.removeAllViews()
        cancelIconJobs()
        val catalog = when {
            apps.isNotEmpty() -> apps
            ::appsProvider.isInitialized -> appsProvider()
            else -> emptyList()
        }
        val suggested = SuggestedApps.load(context, catalog)
        suggested.chunked(4).forEach { rowApps ->
            suggestedGrid.addView(appRow(rowApps, highlight = null))
        }
    }

    private fun bindTopicChips() {
        topicsChips.removeAllViews()
        val padH = (12 * resources.displayMetrics.density).toInt()
        val padV = (8 * resources.displayMetrics.density).toInt()
        SettingsTopics.all(context).take(CHIP_LIMIT).forEach { topic ->
            val icon = ContextCompat.getDrawable(context, R.drawable.ic_settings_dot)?.mutate()?.apply {
                setTint(ContextCompat.getColor(context, R.color.on_dark))
            }
            topicsChips.addView(
                TextView(context).apply {
                    text = topic.title
                    setBackgroundResource(R.drawable.bg_search_chip)
                    setTextColor(ContextCompat.getColor(context, R.color.on_dark))
                    textSize = 13f
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    includeFontPadding = false
                    minHeight = resources.getDimensionPixelSize(R.dimen.chip_height)
                    setPadding(padH, padV, padH, padV)
                    compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
                    setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
                    setOnClickListener { SettingsTopics.open(context, topic) }
                }
            )
        }
    }

    private fun bindResults(query: String) {
        cancelIconJobs()
        val matched = apps.filter { it.label.contains(query, ignoreCase = true) }.take(8)
        appsCard.isVisible = matched.isNotEmpty()
        appsGrid.removeAllViews()
        matched.chunked(4).forEach { rowApps ->
            appsGrid.addView(appRow(rowApps, highlight = query))
        }

        val shortcuts = matched.flatMap { AppShortcuts.list(context, it, query) }.take(6)
        shortcutsCard.isVisible = shortcuts.isNotEmpty()
        shortcutsList.removeAllViews()
        shortcuts.forEach { shortcut ->
            val row = LayoutInflater.from(context)
                .inflate(R.layout.item_search_shortcut, shortcutsList, false)
            row.findViewById<TextView>(R.id.shortcut_label).text =
                highlight(shortcut.shortLabel?.toString().orEmpty(), query)
            val iconView = row.findViewById<ImageView>(R.id.shortcut_icon)
            runCatching {
                context.getSystemService(LauncherApps::class.java)
                    ?.getShortcutIconDrawable(shortcut, resources.displayMetrics.densityDpi)
                    ?.let { iconView.setImageDrawable(it) }
            }
            row.setOnClickListener {
                pendingClearOnReturn = true
                AppShortcuts.start(it, shortcut)
            }
            shortcutsList.addView(row)
        }

        webRow.isVisible = true
        webRow.text = context.getString(R.string.search_web, query)
        webRow.setOnClickListener { performDirectSearch(query) }

        val topics = SettingsTopics.matching(context, query)
        settingsCard.isVisible = topics.isNotEmpty()
        settingsList.removeAllViews()
        topics.forEach { topic ->
            val label = TextView(context).apply {
                text = highlight(topic.title, query)
                setTextColor(ContextCompat.getColor(context, R.color.on_dark))
                textSize = 13f
                minHeight = resources.getDimensionPixelSize(R.dimen.popup_row_height)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setOnClickListener { SettingsTopics.open(context, topic) }
            }
            settingsList.addView(label)
        }
    }

    private fun appRow(rowApps: List<AppInfo>, highlight: String?): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rowApps.forEach { app ->
            val cell = LayoutInflater.from(context).inflate(R.layout.item_search_app, row, false)
            val icon = cell.findViewById<ImageView>(R.id.app_icon)
            val label = cell.findViewById<TextView>(R.id.app_label)
            label.text = if (highlight.isNullOrEmpty()) app.label else highlight(app.label, highlight)
            icon.setImageBitmap(iconCache.placeholderBitmap())
            val cached = iconCache.peek(app, themed)
            if (cached != null) {
                icon.setImageDrawable(BitmapDrawable(resources, cached))
            } else {
                iconJobs += scope.launch {
                    icon.setImageDrawable(BitmapDrawable(resources, iconCache.get(app, themed)))
                }
            }
            cell.setOnClickListener {
                pendingClearOnReturn = true
                AppLauncher.launch(it, app)
            }
            row.addView(cell)
        }
        repeat(4 - rowApps.size) {
            row.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })
        }
        return row
    }

    private fun highlight(text: String, query: String): CharSequence {
        val start = text.indexOf(query, ignoreCase = true)
        if (start < 0) return text
        val color = ContextCompat.getColor(context, R.color.search_match)
        return SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(color),
                start,
                start + query.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun performDirectSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val launched = GoogleIntents.directSearch(context, trimmed)
        if (launched) {
            pendingClearOnReturn = true
            hideKeyboard()
        }
    }

    fun clearSearchIfPending() {
        if (pendingClearOnReturn) {
            pendingClearOnReturn = false
            field.text = null
            field.clearFocus()
            hideKeyboard()
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            clearSearchIfPending()
        }
    }

    private fun showKeyboard() {
        field.post {
            val imm = context.getSystemService(InputMethodManager::class.java) ?: return@post
            imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(field.windowToken, 0)
    }

    private fun cancelIconJobs() {
        iconJobs.forEach { it.cancel() }
        iconJobs.clear()
    }

    private companion object {
        const val CHIP_LIMIT = 8
    }
}

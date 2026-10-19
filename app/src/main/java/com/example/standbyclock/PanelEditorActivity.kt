package com.example.standbyclock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * Edit mode for the Panels face: how many panels, their sizes, what each one shows (built-in items
 * and home-screen widgets), the rotation and yoga options, and the look of each panel.
 * It uses the same components as the settings pages, and every change is applied straight away.
 */
class PanelEditorActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var ui: SettingsUi
    private lateinit var scaffold: SettingsScaffold
    private val stacks = mutableListOf<MutableList<String>>()
    private val providers: List<AppWidgetProviderInfo> by lazy {
        AppWidgetManager.getInstance(this).installedProviders.sortedBy { it.loadLabel(packageManager) }
    }
    private var searchBox: View? = null
    private var searchText = ""
    private var results: LinearLayout? = null
    private var addToPanel = 0
    private var pendingWidgetId = -1
    private var note: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        ui = SettingsUi(this)
        scaffold = SettingsScaffold(this, ui)
        loadStacks()
        setContentView(scaffold.root)
        render()
    }

    private fun loadStacks() {
        stacks.clear()
        settings.panelStacks().forEach { stacks += it.toMutableList() }
    }

    private fun persist() {
        settings.savePanelStacks(stacks.map { it.toList() })
    }

    /** Rebuilds the page, keeping the scroll position. */
    private fun render() {
        scaffold.show("Panels", "Widgets", buildViews(), { finish() }, 0, scaffold.scrollPosition())
    }

    private fun buildViews(): List<View> {
        val views = mutableListOf<View>()
        val count = settings.panelCount
        if (addToPanel >= count) addToPanel = 0

        // Layout
        val counts = listOf(2, 3, 4)
        val weights = settings.panelWeights()
        val layoutRows = mutableListOf<View>()
        layoutRows += ui.segmentedRow("Number of panels", listOf("Two", "Three", "Four"), counts.indexOf(count)) {
            val chosen = counts[it]
            if (chosen != settings.panelCount) {
                settings.panelCount = chosen
                while (stacks.size < chosen) stacks.add(mutableListOf())
                persist()
                render()
            }
        }
        for (index in 0 until count) {
            layoutRows += ui.sliderRow("Panel ${index + 1} size", 1, 6, weights[index].toInt().coerceIn(1, 6), { "$it" }) { value ->
                val updated = settings.panelWeights().toMutableList()
                updated[index] = value.toFloat()
                settings.panelSplit = updated.joinToString(",") { it.toInt().toString() }
            }
        }
        val rotateValues = intArrayOf(0, 5, 10, 30, 60)
        val rotateLabels = listOf("Hold first", "5 seconds", "10 seconds", "30 seconds", "1 minute")
        layoutRows += ui.sliderRow(
            "Move on after", 0, rotateValues.lastIndex,
            rotateValues.indexOf(settings.panelRotateSeconds).coerceAtLeast(0), { rotateLabels[it] },
        ) { settings.panelRotateSeconds = rotateValues[it] }
        layoutRows += ui.switchRow("Yoga layout", "The first panel grows while nothing is playing", settings.panelYoga) {
            settings.panelYoga = it
        }
        views += ui.section(
            "Layout",
            "Panel sizes are relative: each panel takes its number out of the total. Stacked items move on after the chosen time.",
            layoutRows,
        )

        // Contents
        stacks.take(count).forEachIndexed { panel, items ->
            val rows = mutableListOf<View>()
            if (items.isEmpty()) rows += ui.emptyRow("Empty. The panel stays blank.")
            items.forEachIndexed { index, id ->
                val actions = mutableListOf<Pair<String, () -> Unit>>()
                if (index > 0) actions += "Up" to { move(panel, index, -1) }
                if (index < items.lastIndex) actions += "Down" to { move(panel, index, 1) }
                actions += "Remove" to {
                    val removed = stacks[panel].removeAt(index)
                    if (removed.startsWith(PanelsView.WIDGET_PREFIX)) {
                        removed.removePrefix(PanelsView.WIDGET_PREFIX).toIntOrNull()?.let {
                            WidgetHostHolder.get(this).deleteAppWidgetId(it)
                        }
                    }
                    persist()
                    render()
                }
                rows += ui.itemRow(itemLabel(id), null, actions)
            }
            views += ui.section(
                "Panel ${panel + 1}",
                if (panel == 0) "Each panel shows one item, or a stack that moves through its items." else null,
                rows,
            )
        }

        // Add an item
        views += ui.section(
            "Add an item",
            note ?: "Search built-in items and installed home-screen widgets.",
            listOf(ui.segmentedRow("Add to", List(count) { "Panel ${it + 1}" }, addToPanel) { addToPanel = it }),
        )
        if (searchBox == null) {
            searchBox = ui.searchBar("") {
                searchText = it
                showResults()
            }.first
        }
        views += searchBox!!
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        views += results!!
        showResults()

        // Look of each panel
        for (index in 0 until count) {
            val style = settings.panelStyle(index)
            fun update(change: (PanelStyle) -> PanelStyle) = settings.setPanelStyle(index, change(settings.panelStyle(index)))
            views += ui.section("Panel ${index + 1} look", null, listOf(
                ui.sliderRow("Corner roundness", 0, 40, style.corner, { "$it dp" }) { v -> update { it.copy(corner = v) } },
                ui.sliderRow("Fill opacity", 0, 100, style.alpha) { v -> update { it.copy(alpha = v) } },
                ui.swatchRow(
                    "Border colour",
                    Settings.BORDER_COLOURS.map { it.first },
                    Settings.BORDER_COLOURS.map { it.second },
                    style.border,
                ) { v -> update { it.copy(border = v) } },
                ui.sliderRow("Border thickness", 0, 6, style.thickness, { "$it dp" }) { v -> update { it.copy(thickness = v) } },
                ui.sliderRow("Text zoom", 50, 200, style.zoom) { v -> update { it.copy(zoom = v) } },
                ui.sliderRow("Spacing around the panel", 0, 40, style.spacing, { "$it dp" }) { v -> update { it.copy(spacing = v) } },
            ))
        }
        return views
    }

    private fun move(panel: Int, index: Int, step: Int) {
        val items = stacks[panel]
        val target = index + step
        if (target !in items.indices) return
        items[index] = items[target].also { items[target] = items[index] }
        persist()
        render()
    }

    private fun itemLabel(id: String): String {
        if (!id.startsWith(PanelsView.WIDGET_PREFIX)) {
            return Settings.PANEL_ITEMS.firstOrNull { it.first == id }?.second ?: id
        }
        val widget = id.removePrefix(PanelsView.WIDGET_PREFIX).toIntOrNull()
        val info = widget?.let { AppWidgetManager.getInstance(this).getAppWidgetInfo(it) }
        return "Widget: " + (info?.loadLabel(packageManager) ?: "not installed")
    }

    /** Matches built-in items and installed widgets against the search text. */
    private fun showResults() {
        val holder = results ?: return
        holder.removeAllViews()
        val needle = searchText.trim().lowercase()
        val rows = mutableListOf<View>()
        Settings.PANEL_ITEMS.filter { needle.isEmpty() || it.second.lowercase().contains(needle) }.forEach { (id, name) ->
            rows += ui.actionRow("Add $name", plus = true) { addItem(id) }
        }
        if (needle.isNotEmpty()) {
            providers.filter { it.loadLabel(packageManager).lowercase().contains(needle) }.take(15).forEach { provider ->
                rows += ui.actionRow("Add widget: ${provider.loadLabel(packageManager)}", plus = true) { bindProvider(provider) }
            }
        }
        holder.addView(
            if (rows.isEmpty()) ui.note("Nothing matches \"${searchText.trim()}\".") else ui.section(null, null, rows),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun addItem(id: String) {
        stacks[addToPanel] += id
        note = null
        persist()
        render()
    }

    /** Binds a widget to a new id, asking the system for permission if needed, then configures it. */
    private fun bindProvider(provider: AppWidgetProviderInfo) {
        val manager = AppWidgetManager.getInstance(this)
        val id = WidgetHostHolder.get(this).allocateAppWidgetId()
        pendingWidgetId = id
        if (manager.bindAppWidgetIdIfAllowed(id, provider.provider)) {
            afterBind(id)
        } else {
            startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            }, REQUEST_BIND)
        }
    }

    private fun afterBind(id: Int) {
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        val configure = info?.configure
        if (configure != null) {
            startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }, REQUEST_CONFIGURE)
        } else {
            addWidget(id)
        }
    }

    private fun addWidget(id: Int) {
        stacks[addToPanel] += PanelsView.WIDGET_PREFIX + id
        note = null
        persist()
        render()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_BIND -> if (resultCode == RESULT_OK) {
                afterBind(pendingWidgetId)
            } else {
                WidgetHostHolder.get(this).deleteAppWidgetId(pendingWidgetId)
                note = "That widget was not allowed to be added."
                render()
            }
            REQUEST_CONFIGURE -> if (resultCode == RESULT_OK) {
                addWidget(pendingWidgetId)
            } else {
                WidgetHostHolder.get(this).deleteAppWidgetId(pendingWidgetId)
            }
        }
    }

    companion object {
        private const val REQUEST_BIND = 21
        private const val REQUEST_CONFIGURE = 22
    }
}

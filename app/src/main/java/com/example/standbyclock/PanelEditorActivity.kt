package com.example.standbyclock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Edit mode for the Panels face: how many panels, their sizes, what each one shows (built-in items
 * and home-screen widgets), the rotation and yoga options, and the look of each panel.
 * Every change is saved straight away.
 */
class PanelEditorActivity : Activity() {

    private lateinit var settings: Settings
    private val stacks = mutableListOf<MutableList<String>>()
    private val providers: List<AppWidgetProviderInfo> by lazy {
        AppWidgetManager.getInstance(this).installedProviders.sortedBy { it.loadLabel(packageManager) }
    }
    private lateinit var results: LinearLayout
    private lateinit var panelChooser: Spinner
    private lateinit var itemsHolder: LinearLayout
    private var addToPanel = 0
    private var pendingWidgetId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        loadStacks()
        setContentView(buildUi())
    }

    private fun loadStacks() {
        stacks.clear()
        settings.panelStacks().forEach { stacks += it.toMutableList() }
    }

    private fun persist() {
        settings.savePanelStacks(stacks.map { it.toList() })
    }

    private fun buildUi(): View {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        content.addView(section("Layout"))
        val counts = intArrayOf(2, 3, 4)
        content.addView(label("Number of panels"))
        content.addView(spinner(arrayOf("Two", "Three", "Four"), counts.indexOf(settings.panelCount)) {
            val chosen = counts[it]
            if (chosen != settings.panelCount) {
                settings.panelCount = chosen
                while (stacks.size < chosen) stacks.add(mutableListOf())
                persist()
                recreate()
            }
        })
        content.addView(label("Panel sizes, as relative numbers separated by commas (for example 2,1,1). Each panel uses its number of the total."))
        val split = EditText(this).apply {
            setSingleLine()
            setText(settings.panelSplit)
        }
        content.addView(split)
        content.addView(button("Save panel sizes") {
            val parsed = split.text.toString().split(",").mapNotNull { it.trim().toFloatOrNull()?.takeIf { w -> w > 0f } }
            if (parsed.size < settings.panelCount) {
                Toast.makeText(this, "Enter one number for each panel", Toast.LENGTH_SHORT).show()
            } else {
                settings.panelSplit = parsed.joinToString(",")
                Toast.makeText(this, "Panel sizes saved", Toast.LENGTH_SHORT).show()
            }
        })
        val rotateValues = intArrayOf(0, 5, 10, 30, 60)
        content.addView(label("Stacked items move on after"))
        content.addView(spinner(
            arrayOf("Hold the first item", "5 seconds", "10 seconds", "30 seconds", "1 minute"),
            rotateValues.indexOf(settings.panelRotateSeconds).coerceAtLeast(0),
        ) { settings.panelRotateSeconds = rotateValues[it] })
        content.addView(toggle("Yoga layout: the first panel grows while nothing is playing", settings.panelYoga) {
            settings.panelYoga = it
        })

        content.addView(section("Panel contents"))
        content.addView(label("Each panel shows one item, or a stack that moves through its items. Use the arrows to reorder and the cross to remove."))
        itemsHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(itemsHolder)
        renderItems()

        content.addView(section("Add an item"))
        content.addView(label("Search built-in items and installed home-screen widgets."))
        panelChooser = spinner(
            Array(settings.panelCount) { "Panel ${it + 1}" },
            addToPanel,
        ) { addToPanel = it }
        content.addView(panelChooser)
        val search = EditText(this).apply {
            hint = "Search, for example weather or calendar"
            setSingleLine()
        }
        content.addView(search)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = showResults(s?.toString().orEmpty())
        })
        showResults("")

        content.addView(section("Look of each panel"))
        for (index in 0 until settings.panelCount) {
            content.addView(label("Panel ${index + 1}"))
            content.addView(styleControls(index))
        }

        content.addView(section("Panel layout tips"))
        content.addView(label("Widgets are drawn at the size the panel gives them. Some widgets need more room; a larger panel shows more of them."))

        return ScrollView(this).apply { addView(content) }
    }

    /** One row per item in each panel, with reorder and remove buttons. */
    private fun renderItems() {
        itemsHolder.removeAllViews()
        stacks.take(settings.panelCount).forEachIndexed { panel, items ->
            itemsHolder.addView(section("Panel ${panel + 1}"))
            if (items.isEmpty()) itemsHolder.addView(label("Empty. The panel stays blank."))
            items.forEachIndexed { index, id ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(label(itemLabel(id)).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(button("↑") { move(panel, index, -1) })
                row.addView(button("↓") { move(panel, index, 1) })
                row.addView(button("✕") {
                    val removed = stacks[panel].removeAt(index)
                    if (removed.startsWith(PanelsView.WIDGET_PREFIX)) {
                        removed.removePrefix(PanelsView.WIDGET_PREFIX).toIntOrNull()?.let {
                            WidgetHostHolder.get(this).deleteAppWidgetId(it)
                        }
                    }
                    persist()
                    renderItems()
                })
                itemsHolder.addView(row)
            }
        }
    }

    private fun move(panel: Int, index: Int, step: Int) {
        val items = stacks[panel]
        val target = index + step
        if (target !in items.indices) return
        items[index] = items[target].also { items[target] = items[index] }
        persist()
        renderItems()
    }

    private fun itemLabel(id: String): String {
        if (!id.startsWith(PanelsView.WIDGET_PREFIX)) {
            return "• " + (Settings.PANEL_ITEMS.firstOrNull { it.first == id }?.second ?: id)
        }
        val widget = id.removePrefix(PanelsView.WIDGET_PREFIX).toIntOrNull()
        val info = widget?.let { AppWidgetManager.getInstance(this).getAppWidgetInfo(it) }
        return "• Widget: " + (info?.loadLabel(packageManager) ?: "not installed")
    }

    /** Matches built-in items and installed widgets against the search text. */
    private fun showResults(query: String) {
        results.removeAllViews()
        val needle = query.trim().lowercase()
        Settings.PANEL_ITEMS.filter { needle.isEmpty() || it.second.lowercase().contains(needle) }.forEach { (id, name) ->
            results.addView(button("Add $name") { addItem(id) })
        }
        if (needle.isEmpty()) return
        providers.filter { it.loadLabel(packageManager).lowercase().contains(needle) }.take(15).forEach { provider ->
            results.addView(button("Add widget: ${provider.loadLabel(packageManager)}") { bindProvider(provider) })
        }
    }

    private fun addItem(id: String) {
        stacks[addToPanel] += id
        persist()
        renderItems()
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
        persist()
        renderItems()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_BIND -> if (resultCode == RESULT_OK) {
                afterBind(pendingWidgetId)
            } else {
                WidgetHostHolder.get(this).deleteAppWidgetId(pendingWidgetId)
                Toast.makeText(this, "Widget was not allowed", Toast.LENGTH_SHORT).show()
            }
            REQUEST_CONFIGURE -> if (resultCode == RESULT_OK) {
                addWidget(pendingWidgetId)
            } else {
                WidgetHostHolder.get(this).deleteAppWidgetId(pendingWidgetId)
            }
        }
    }

    /** Corner, opacity, border, thickness, zoom and spacing for one panel. */
    private fun styleControls(index: Int): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val style = settings.panelStyle(index)
        fun update(change: (PanelStyle) -> PanelStyle) = settings.setPanelStyle(index, change(settings.panelStyle(index)))
        box.addView(slider("Corner roundness (dp)", 0, 40, style.corner) { value -> update { it.copy(corner = value) } })
        box.addView(slider("Opacity (%)", 0, 100, style.alpha) { value -> update { it.copy(alpha = value) } })
        box.addView(label("Border colour"))
        box.addView(spinner(Settings.BORDER_COLOURS.map { it.first }.toTypedArray(), style.border) { value ->
            update { it.copy(border = value) }
        })
        box.addView(slider("Border thickness (dp)", 0, 6, style.thickness) { value -> update { it.copy(thickness = value) } })
        box.addView(slider("Text zoom (%)", 50, 200, style.zoom) { value -> update { it.copy(zoom = value) } })
        box.addView(slider("Spacing around the panel (dp)", 0, 40, style.spacing) { value -> update { it.copy(spacing = value) } })
        return box
    }

    private fun section(text: String): TextView = label(text).apply {
        textSize = 20f
        setPadding(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(0xFFFFFFFF.toInt())
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun toggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit): Switch =
        Switch(this).apply {
            this.text = text
            isChecked = checked
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }

    private fun slider(name: String, low: Int, high: Int, current: Int, onChange: (Int) -> Unit): LinearLayout {
        val readout = label("$name: $current")
        val bar = SeekBar(this).apply {
            max = high - low
            progress = current - low
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val value = progress + low
                    readout.text = "$name: $value"
                    if (fromUser) onChange(value)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(readout)
            addView(bar)
        }
    }

    private fun spinner(labels: Array<String>, selected: Int, onSelect: (Int) -> Unit): Spinner =
        Spinner(this).apply {
            adapter = ArrayAdapter(this@PanelEditorActivity, android.R.layout.simple_spinner_item, labels)
            setSelection(selected.coerceAtLeast(0), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    onSelect(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }

    companion object {
        private const val REQUEST_BIND = 21
        private const val REQUEST_CONFIGURE = 22
    }
}

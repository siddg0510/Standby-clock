package com.example.standbyclock

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle

/**
 * The settings, as one iOS-style stack of pages: a root list of categories (with search), a page for each
 * category, and below that only choice pages and list pages. The back arrow and the system back button both
 * go up one page. Every change is applied the moment it is made.
 */
class SettingsActivity : Activity() {

    lateinit var settings: Settings
        private set

    private lateinit var ui: SettingsUi
    private lateinit var scaffold: SettingsScaffold
    private lateinit var pages: SettingsPages

    /** A page on the stack, with the scroll position it had when another page was opened over it. */
    private class Entry(val key: String, var title: String, var scrollY: Int = 0)

    private val stack = ArrayList<Entry>()
    private var resumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        ui = SettingsUi(this)
        scaffold = SettingsScaffold(this, ui)
        pages = SettingsPages(this, ui)
        setContentView(scaffold.root)

        val saved = savedInstanceState?.getStringArrayList(STATE_STACK)
        val keys = when {
            saved != null && saved.isNotEmpty() -> saved
            else -> pathFor(intent.getStringExtra(EXTRA_PAGE))
        }
        keys.forEach { key -> stack += Entry(key, pages.build(key)?.title ?: "Settings") }
        if (stack.isEmpty()) stack += Entry(SettingsPages.ROOT, "Settings")
        show(0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_STACK, ArrayList(stack.map { it.key }))
    }

    override fun onResume() {
        super.onResume()
        // Permissions and other system settings may have changed while another screen was open.
        if (resumedOnce) refresh()
        resumedOnce = true
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!back()) super.onBackPressed()
    }

    // ---- Navigation ----

    private fun pathFor(page: String?): List<String> = when {
        page == null || page == SettingsPages.ROOT -> listOf(SettingsPages.ROOT)
        page.contains('/') && !page.startsWith("pick/") -> listOf(SettingsPages.ROOT, page.substringBefore('/'), page)
        page.startsWith("pick/") -> listOf(SettingsPages.ROOT)
        else -> listOf(SettingsPages.ROOT, page)
    }

    /** Opens a page on top of the current one. */
    fun open(key: String) {
        if (pages.build(key) == null) return
        stack.lastOrNull()?.scrollY = scaffold.scrollPosition()
        stack += Entry(key, "")
        show(1)
    }

    /** Opens several pages in order, for example a category and then a page inside it (used by search). */
    fun openPath(keys: List<String>) {
        stack.lastOrNull()?.scrollY = scaffold.scrollPosition()
        keys.forEach { key -> if (pages.build(key) != null) stack += Entry(key, "") }
        show(1)
    }

    /** Goes up one page. Returns false when already on the root page. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        show(-1)
        return true
    }

    /** Rebuilds the current page, keeping its scroll position. */
    fun refresh() {
        if (!::scaffold.isInitialized || isDestroyed || stack.isEmpty()) return
        stack.last().scrollY = scaffold.scrollPosition()
        show(0)
    }

    private fun show(direction: Int) {
        var entry = stack.last()
        var page = pages.build(entry.key)
        while (page == null && stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            entry = stack.last()
            page = pages.build(entry.key)
        }
        val shown = page ?: pages.build(SettingsPages.ROOT) ?: return
        entry.title = shown.title
        val backLabel = if (stack.size > 1) stack[stack.size - 2].title.ifEmpty { "Settings" } else null
        scaffold.show(shown.title, backLabel, shown.views, { back() }, direction, if (direction == 1) 0 else entry.scrollY)
    }

    fun openClock() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    // ---- System screens and permissions ----

    /** Starts a system screen, falling back to this app's details page when the device has no such screen. */
    fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            openAppDetails()
        }
    }

    fun openAppDetails() {
        launch(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
    }

    fun fixNotifications() {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!runtimeGranted) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        } else {
            launch(
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
    }

    fun fixFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            launch(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:$packageName"),
                ),
            )
        } else {
            openAppDetails()
        }
    }

    fun fixNotificationAccess() {
        launch(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun fixCalendar() {
        requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQUEST_CALENDAR)
    }

    fun fixLocation() {
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_LOCATION_ALL,
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty()) return
        val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        // The system stops showing its dialog after repeated refusals. Then the only way is the app's own settings page.
        if (!granted && permissions.isNotEmpty() && !shouldShowRequestPermissionRationale(permissions[0])) openAppDetails()
        if (requestCode == REQUEST_LOCATION && granted) pages.fetchLocation()
        refresh()
    }

    // ---- Photos ----

    fun pickPhoto() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_PHOTO)
    }

    fun pickPictures() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQUEST_PICTURES)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQUEST_PHOTO -> {
                val uri = data.data ?: return
                // Keep read access across reboots so the photo face still works.
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                settings.photoUri = uri.toString()
            }
            REQUEST_PICTURES -> {
                val uris = mutableListOf<Uri>()
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
                }
                data.data?.let { uris += it }
                uris.forEach { uri ->
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (uris.isNotEmpty()) {
                    settings.photoList = uris.joinToString("\n") { it.toString() }
                    settings.photoUri = uris.first().toString()
                }
            }
        }
        refresh()
    }

    companion object {
        /** Open a page directly, for example "charging" or "sound/stations". */
        const val EXTRA_PAGE = "page"

        private const val STATE_STACK = "stack"
        private const val REQUEST_PHOTO = 1
        private const val REQUEST_CALENDAR = 2
        private const val REQUEST_NOTIFICATIONS = 3
        const val REQUEST_LOCATION = 4
        private const val REQUEST_LOCATION_ALL = 5
        private const val REQUEST_PICTURES = 10
    }
}

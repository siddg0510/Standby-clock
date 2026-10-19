package com.example.standbyclock

import android.content.Context

/**
 * Named profiles. Each space keeps its own settings (faces, layouts, launch rules).
 * "Home" uses the original settings file, so existing settings carry over.
 */
object Spaces {

    private const val INDEX_FILE = "spaces"
    private const val DEFAULT = "Home"

    private fun index(context: Context) = context.getSharedPreferences(INDEX_FILE, Context.MODE_PRIVATE)

    fun names(context: Context): List<String> {
        val stored = index(context).getString("names", null)
        val list = stored?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        return if (list.isEmpty()) listOf(DEFAULT) else list
    }

    fun current(context: Context): String = index(context).getString("current", DEFAULT) ?: DEFAULT

    fun switchTo(context: Context, name: String) {
        index(context).edit().putString("current", name).apply()
    }

    /** Adds a space. Returns false when the name is blank or already exists. */
    fun add(context: Context, name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || names(context).any { it.equals(clean, ignoreCase = true) }) return false
        index(context).edit().putString("names", (names(context) + clean).joinToString("\n")).apply()
        return true
    }

    /** The settings file for the current space. */
    fun prefsName(context: Context): String {
        val name = current(context)
        if (name == DEFAULT) return "standby"
        return "standby_" + name.lowercase().filter { it.isLetterOrDigit() }
    }
}

package com.example.standbyclock

/** The most recent notifications from the apps you allow (focus mode), shown in Tools. */
object NotificationsStack {

    private const val LIMIT = 30
    private val items = ArrayDeque<String>()

    @Synchronized
    fun add(line: String) {
        items.addFirst(line)
        while (items.size > LIMIT) items.removeLast()
    }

    @Synchronized
    fun recent(): List<String> = items.toList()
}

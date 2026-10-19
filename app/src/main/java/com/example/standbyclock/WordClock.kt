package com.example.standbyclock

/** English word clock, rounded to the nearest five minutes: "It's ten past two". */
object WordClock {

    private val hours = arrayOf(
        "twelve", "one", "two", "three", "four", "five",
        "six", "seven", "eight", "nine", "ten", "eleven",
    )
    private val words = mapOf(5 to "five", 10 to "ten", 15 to "quarter", 20 to "twenty", 25 to "twenty-five", 30 to "half")

    fun describe(hour24: Int, minute: Int): String {
        val rounded = (minute + 2) / 5 * 5
        val hourBase = hour24 + rounded / 60
        val minutes = rounded % 60
        // After the half hour, the phrase names the next hour ("twenty to three").
        val hour = hours[(if (minutes > 30) hourBase + 1 else hourBase) % 12]
        return when {
            minutes == 0 -> "It's $hour o'clock"
            minutes <= 30 -> "It's ${words.getValue(minutes)} past $hour"
            else -> "It's ${words.getValue(60 - minutes)} to $hour"
        }
    }
}

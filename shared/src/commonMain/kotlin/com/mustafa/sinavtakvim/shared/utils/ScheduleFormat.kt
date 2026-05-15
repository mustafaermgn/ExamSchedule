package com.mustafa.sinavtakvim.shared.utils

fun slotLabel(slotId: Int): String = when (slotId) {
    1 -> "09:00"
    2 -> "11:00"
    3 -> "14:00"
    4 -> "16:00"
    else -> "Oturum $slotId"
}

fun examDateLabel(timestamp: Long): String {
    if (timestamp <= 0L) return "Tarih bekliyor"
    val localEpochDay = (timestamp + TURKEY_OFFSET_MILLIS) / DAY_MILLIS
    val date = dateFromEpochDay(localEpochDay)
    return "${date.day} ${MONTH_NAMES[date.month - 1]} ${date.year}"
}

fun timeLabel(timestamp: Long): String {
    if (timestamp <= 0L) return "--:--"
    val localMillisOfDay = ((timestamp + TURKEY_OFFSET_MILLIS) % DAY_MILLIS + DAY_MILLIS) % DAY_MILLIS
    val totalMinutes = (localMillisOfDay / 60_000L).toInt()
    val hour = totalMinutes / 60
    val minute = totalMinutes % 60
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

private fun dateFromEpochDay(epochDay: Long): SimpleDate {
    var remainingDays = epochDay
    var year = 1970

    while (true) {
        val daysInYear = if (isLeapYear(year)) 366 else 365
        if (remainingDays < daysInYear) break
        remainingDays -= daysInYear
        year += 1
    }

    var month = 1
    while (true) {
        val daysInMonth = daysInMonth(year, month)
        if (remainingDays < daysInMonth) break
        remainingDays -= daysInMonth
        month += 1
    }

    return SimpleDate(year, month, remainingDays.toInt() + 1)
}

private fun daysInMonth(year: Int, month: Int): Int {
    return when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 30
    }
}

private fun isLeapYear(year: Int): Boolean {
    return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
}

private data class SimpleDate(val year: Int, val month: Int, val day: Int)

private const val DAY_MILLIS = 86_400_000L
private const val TURKEY_OFFSET_MILLIS = 3 * 60 * 60 * 1000L

private val MONTH_NAMES = listOf(
    "Ocak",
    "Şubat",
    "Mart",
    "Nisan",
    "Mayıs",
    "Haziran",
    "Temmuz",
    "Ağustos",
    "Eylül",
    "Ekim",
    "Kasım",
    "Aralık"
)

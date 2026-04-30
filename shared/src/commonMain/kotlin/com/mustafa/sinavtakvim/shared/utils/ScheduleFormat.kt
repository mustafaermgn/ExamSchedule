package com.mustafa.sinavtakvim.shared.utils

private const val firstExamDay = 1_777_960_800_000L
private const val dayMillis = 86_400_000L

fun slotLabel(slotId: Int): String = when (slotId) {
    1 -> "09:00"
    2 -> "11:00"
    3 -> "14:00"
    4 -> "16:00"
    else -> "Oturum $slotId"
}

fun examDateLabel(timestamp: Long): String {
    if (timestamp <= 0L) return "Tarih bekliyor"
    val dayOffset = ((timestamp - firstExamDay) / dayMillis).toInt().coerceAtLeast(0)
    return "${5 + dayOffset} Mayıs 2026"
}

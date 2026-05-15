package com.mustafa.sinavtakvim.shared.utils

import java.awt.Desktop
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

actual fun addToCalendar(title: String, description: String, startTime: Long) {
    val downloads = File(System.getProperty("user.home"), "Downloads").apply { mkdirs() }
    val file = File(downloads, "${title.fileSafe()}_$startTime.ics")
    val endTime = startTime + 90 * 60 * 1000L

    file.writeText(
        buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//SinavTakvim//Exam Schedule//TR")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:${UUID.randomUUID()}@sinavtakvim")
            appendLine("DTSTAMP:${icsTime(System.currentTimeMillis())}")
            appendLine("DTSTART:${icsTime(startTime)}")
            appendLine("DTEND:${icsTime(endTime)}")
            appendLine("SUMMARY:${title.icsEscape()}")
            appendLine("DESCRIPTION:${description.icsEscape()}")
            appendLine("END:VEVENT")
            appendLine("END:VCALENDAR")
        }
    )

    runCatching {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(file)
        }
    }
}

private fun icsTime(timestamp: Long): String {
    return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)
        .format(Instant.ofEpochMilli(timestamp))
}

private fun String.icsEscape(): String {
    return replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
        .replace("\r", "")
}

private fun String.fileSafe(): String {
    return lowercase()
        .map { if (it.isLetterOrDigit()) it else '_' }
        .joinToString("")
        .trim('_')
        .ifBlank { "sinav" }
}

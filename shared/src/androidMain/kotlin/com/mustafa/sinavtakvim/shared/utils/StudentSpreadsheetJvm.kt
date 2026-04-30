package com.mustafa.sinavtakvim.shared.utils

import com.mustafa.sinavtakvim.shared.models.Student
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

actual fun parseStudentSpreadsheet(bytes: ByteArray, fileName: String): StudentImportResult {
    return if (fileName.endsWith(".xlsx", ignoreCase = true)) {
        parseXlsx(bytes)
    } else {
        parseDelimited(bytes.toString(Charsets.UTF_8))
    }
}

actual fun saveReportFile(fileName: String, bytes: ByteArray): String {
    val context = CalendarContext.context()
    val dir = context?.getExternalFilesDir(null) ?: File(System.getProperty("java.io.tmpdir") ?: ".")
    dir.mkdirs()
    val file = File(dir, fileName)
    file.writeBytes(bytes)
    return file.absolutePath
}

private fun parseDelimited(text: String): StudentImportResult {
    val warnings = mutableListOf<String>()
    val rows = text.replace("\uFEFF", "")
        .lines()
        .filter { it.isNotBlank() }
        .map { line ->
            val delimiter = when {
                line.contains(";") -> ";"
                line.contains("\t") -> "\t"
                else -> ","
            }
            line.split(delimiter).map { it.trim().trim('"') }
        }
    return rowsToStudents(rows, warnings)
}

private fun parseXlsx(bytes: ByteArray): StudentImportResult {
    val entries = mutableMapOf<String, String>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && (entry.name == "xl/sharedStrings.xml" || entry.name.startsWith("xl/worksheets/sheet"))) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
    }
    val sharedStrings = entries["xl/sharedStrings.xml"]
        ?.let { Regex("<t[^>]*>(.*?)</t>").findAll(it).map { match -> match.groupValues[1].xmlUnescape() }.toList() }
        ?: emptyList()
    val sheet = entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/sheet") }?.value ?: return StudentImportResult(emptyList(), listOf("XLSX içinde çalışma sayfası bulunamadı."))
    val rows = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(sheet).map { rowMatch ->
        Regex("<c([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL).findAll(rowMatch.groupValues[1]).map { cell ->
            val attrs = cell.groupValues[1]
            val body = cell.groupValues[2]
            val value = Regex("<v>(.*?)</v>").find(body)?.groupValues?.getOrNull(1)
            val inline = Regex("<t[^>]*>(.*?)</t>").find(body)?.groupValues?.getOrNull(1)?.xmlUnescape()
            when {
                attrs.contains("t=\"s\"") && value != null -> sharedStrings.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
                inline != null -> inline
                value != null -> value
                else -> ""
            }
        }.toList()
    }.toList()
    val warnings = mutableListOf<String>()
    return rowsToStudents(rows, warnings)
}

private fun rowsToStudents(rows: List<List<String>>, warnings: MutableList<String>): StudentImportResult {
    val dataRows = rows.dropWhile { row ->
        row.joinToString(" ").lowercase().let { it.contains("öğrenci") || it.contains("ogrenci") || it.contains("numara") || it.contains("ad") }
    }
    val students = dataRows.mapNotNull { row ->
        val number = row.firstOrNull { it.any(Char::isDigit) }?.filter { it.isLetterOrDigit() }.orEmpty()
        val name = row.firstOrNull { value -> value.any(Char::isLetter) && value != number }.orEmpty()
        val department = row.drop(2).firstOrNull().orEmpty()
        if (number.isBlank() || name.isBlank()) {
            warnings += "Atlanan satır: ${row.joinToString(" | ")}"
            null
        } else {
            Student(id = number, studentNumber = number, name = name, department = department)
        }
    }.distinctBy { it.studentNumber }
    return StudentImportResult(students, warnings)
}

private fun String.xmlUnescape(): String = replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")

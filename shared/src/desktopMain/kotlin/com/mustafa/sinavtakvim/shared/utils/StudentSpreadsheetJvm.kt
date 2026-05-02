package com.mustafa.sinavtakvim.shared.utils

import com.mustafa.sinavtakvim.shared.models.Student
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

actual fun parseStudentSpreadsheet(bytes: ByteArray, fileName: String): StudentImportResult {
    return try {
        if (fileName.endsWith(".xlsx", ignoreCase = true)) {
            parseXlsx(bytes)
        } else {
            parseDelimited(bytes.toString(Charsets.UTF_8))
        }
    } catch (t: Throwable) {
        StudentImportResult(emptyList(), listOf("Dosya ayrıştırılamadı: ${t.message ?: "beklenmeyen hata"}"))
    }
}

actual fun saveReportFile(fileName: String, bytes: ByteArray): String {
    val downloads = File(System.getProperty("user.home"), "Downloads")
    downloads.mkdirs()
    val file = File(downloads, fileName)
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
    if (bytes.size > MAX_XLSX_FILE_BYTES) {
        return StudentImportResult(emptyList(), listOf("Dosya çok büyük. Lütfen 10 MB altındaki bir Excel dosyası yükleyin."))
    }

    val entries = mutableMapOf<String, String>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && (entry.name == "xl/sharedStrings.xml" || entry.name.startsWith("xl/worksheets/sheet"))) {
                entries[entry.name] = zip.readEntryWithLimit(MAX_XML_ENTRY_BYTES).toString(Charsets.UTF_8)
            }
        }
    }
    val sharedStrings = entries["xl/sharedStrings.xml"]
        ?.let { Regex("<t[^>]*>(.*?)</t>").findAll(it).map { match -> match.groupValues[1].xmlUnescape() }.toList() }
        ?: emptyList()
    val sheet = entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/sheet") }?.value ?: return StudentImportResult(emptyList(), listOf("XLSX içinde çalışma sayfası bulunamadı."))
    return parseSheetRowsToStudents(sheet, sharedStrings)
}

private fun ZipInputStream.readEntryWithLimit(maxBytes: Int): ByteArray {
    val buffer = ByteArray(8 * 1024)
    val out = ByteArrayOutputStream()
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        total += read
        if (total > maxBytes) {
            throw IllegalArgumentException("Excel içeriği çok büyük (>${maxBytes / (1024 * 1024)} MB).")
        }
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
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

private fun parseSheetRowsToStudents(sheet: String, sharedStrings: List<String>): StudentImportResult {
    val warnings = mutableListOf<String>()
    val students = linkedMapOf<String, Student>()
    var headerSkipped = false
    val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
    val cellRegex = Regex("<c([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
    val valueRegex = Regex("<v>(.*?)</v>")
    val textRegex = Regex("<t[^>]*>(.*?)</t>")

    for (rowMatch in rowRegex.findAll(sheet).take(MAX_ROW_COUNT)) {
        val rowCells = cellRegex.findAll(rowMatch.groupValues[1]).map { cell ->
            val attrs = cell.groupValues[1]
            val body = cell.groupValues[2]
            val value = valueRegex.find(body)?.groupValues?.getOrNull(1)
            val inline = textRegex.find(body)?.groupValues?.getOrNull(1)?.xmlUnescape()
            when {
                attrs.contains("t=\"s\"") && value != null -> sharedStrings.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
                inline != null -> inline
                value != null -> value
                else -> ""
            }
        }.toList()

        if (!headerSkipped) {
            val flat = rowCells.joinToString(" ").lowercase()
            if (flat.contains("öğrenci") || flat.contains("ogrenci") || flat.contains("numara") || flat.contains("ad")) {
                continue
            }
            headerSkipped = true
        }

        val number = rowCells.firstOrNull { it.any(Char::isDigit) }?.filter { it.isLetterOrDigit() }.orEmpty()
        val name = rowCells.firstOrNull { value -> value.any(Char::isLetter) && value != number }.orEmpty()
        val department = rowCells.drop(2).firstOrNull().orEmpty()
        if (number.isBlank() || name.isBlank()) {
            warnings += "Atlanan satır: ${rowCells.joinToString(" | ")}"
            continue
        }
        students[number] = Student(id = number, studentNumber = number, name = name, department = department)
    }

    return StudentImportResult(students.values.toList(), warnings)
}

private fun String.xmlUnescape(): String = replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")

private const val MAX_XLSX_FILE_BYTES = 10 * 1024 * 1024
private const val MAX_XML_ENTRY_BYTES = 6 * 1024 * 1024
private const val MAX_ROW_COUNT = 12000

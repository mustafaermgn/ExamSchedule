package com.mustafa.sinavtakvim.shared.utils

import com.mustafa.sinavtakvim.shared.models.Student
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.math.BigDecimal
import java.nio.charset.Charset
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

actual fun parseStudentSpreadsheet(bytes: ByteArray, fileName: String): StudentImportResult {
    return try {
        if (fileName.endsWith(".xlsx", ignoreCase = true)) {
            parseXlsx(bytes)
        } else {
            parseDelimited(bytes)
        }
    } catch (t: Throwable) {
        StudentImportResult(emptyList(), listOf("Dosya ayrıştırılamadı: ${t.message ?: "beklenmeyen hata"}"))
    }
}

actual fun saveReportFile(fileName: String, bytes: ByteArray): String {
    val context = CalendarContext.context()
    if (context != null) {
        val mimeType = when {
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            fileName.endsWith(".xml", ignoreCase = true) -> "application/xml"
            fileName.endsWith(".csv", ignoreCase = true) -> "text/csv"
            else -> "application/octet-stream"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri.toString()
            }
        }

        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        publicDir.mkdirs()
        val file = File(publicDir, fileName)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    val fallback = File(System.getProperty("java.io.tmpdir") ?: ".", fileName)
    fallback.writeBytes(bytes)
    return fallback.absolutePath
}

private fun parseDelimited(bytes: ByteArray): StudentImportResult {
    val warnings = mutableListOf<String>()
    val rows = decodeDelimited(bytes).replace("\uFEFF", "")
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

private fun decodeDelimited(bytes: ByteArray): String {
    return when {
        bytes.take(3) == listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) -> bytes.drop(3).toByteArray().toString(Charsets.UTF_8)
        bytes.take(2) == listOf(0xFF.toByte(), 0xFE.toByte()) -> bytes.drop(2).toByteArray().toString(Charsets.UTF_16LE)
        bytes.take(2) == listOf(0xFE.toByte(), 0xFF.toByte()) -> bytes.drop(2).toByteArray().toString(Charsets.UTF_16BE)
        else -> {
            val utf8 = bytes.toString(Charsets.UTF_8)
            if ('\uFFFD' in utf8) bytes.toString(Charset.forName("windows-1254")) else utf8
        }
    }
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
    val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
    val sheets = entries
        .filterKeys { it.startsWith("xl/worksheets/sheet") }
        .entries
        .sortedBy { worksheetOrder(it.key) }

    if (sheets.isEmpty()) {
        return StudentImportResult(emptyList(), listOf("XLSX içinde çalışma sayfası bulunamadı."))
    }

    val warnings = mutableListOf<String>()
    val students = linkedMapOf<String, Student>()
    sheets.forEach { sheet ->
        val result = parseSheetRowsToStudents(sheet.value, sharedStrings)
        result.students.forEach { student -> students[student.studentNumber] = student }
        warnings += result.warnings
    }

    if (students.isEmpty() && warnings.isEmpty()) {
        warnings += "Excel içinde öğrenci satırı bulunamadı. Öğrenci numarası ve ad soyad sütunlarını kontrol edin."
    }
    return StudentImportResult(students.values.toList(), warnings.take(MAX_WARNING_COUNT))
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
    val students = linkedMapOf<String, Student>()
    var columns = StudentColumns()
    rows.forEach { row ->
        val normalizedRow = row.map { it.trim() }.filter { it.isNotBlank() }
        val detectedColumns = detectStudentColumns(row)
        if (detectedColumns.isStudentHeader) {
            columns = detectedColumns
            return@forEach
        }

        val number = columns.number
            ?.let { row.getOrNull(it) }
            ?.let(::studentNumberFromCell)
            ?: normalizedRow.firstNotNullOfOrNull(::studentNumberFromCell)
            .orEmpty()

        if (number.isBlank()) {
            if (normalizedRow.isNotEmpty() && !looksLikeHeaderOrMetadata(normalizedRow) && warnings.size < MAX_WARNING_COUNT) {
                warnings += "Atlanan satır: ${normalizedRow.joinToString(" | ")}"
            }
            return@forEach
        }

        val name = studentNameFromColumns(row, columns).ifBlank { studentNameFromRow(normalizedRow, number) }
        if (name.isBlank()) {
            if (warnings.size < MAX_WARNING_COUNT) warnings += "Ad soyad bulunamadı: ${normalizedRow.joinToString(" | ")}"
            return@forEach
        }

        val nameParts = name.split(" ").toSet()
        val department = columns.department
            ?.let { row.getOrNull(it)?.trim() }
            ?.takeIf { it.isNotBlank() }
            ?: normalizedRow.firstOrNull { looksLikeDepartmentCode(it) && it !in nameParts }.orEmpty()
        students[number] = Student(id = number, studentNumber = number, name = name, department = department)
    }
    return StudentImportResult(students.values.toList(), warnings.take(MAX_WARNING_COUNT))
}

private fun parseSheetRowsToStudents(sheet: String, sharedStrings: List<String>): StudentImportResult {
    val warnings = mutableListOf<String>()
    val rowRegex = Regex("<(?:\\w+:)?row[^>]*>(.*?)</(?:\\w+:)?row>", RegexOption.DOT_MATCHES_ALL)
    val cellRegex = Regex("<(?:\\w+:)?c([^>]*?)>(.*?)</(?:\\w+:)?c>|<(?:\\w+:)?c([^>]*?)/>", RegexOption.DOT_MATCHES_ALL)
    val valueRegex = Regex("<(?:\\w+:)?v[^>]*>(.*?)</(?:\\w+:)?v>", RegexOption.DOT_MATCHES_ALL)

    val rows = rowRegex.findAll(sheet).take(MAX_ROW_COUNT).map { rowMatch ->
        cellRegex.findAll(rowMatch.groupValues[1]).map { cell ->
            val attrs = cell.groupValues[1].ifBlank { cell.groupValues[3] }
            val body = cell.groupValues[2]
            val value = valueRegex.find(body)?.groupValues?.getOrNull(1)?.xmlUnescape()
            when {
                attrs.contains("t=\"s\"") && value != null -> sharedStrings.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
                attrs.contains("t=\"inlineStr\"") -> extractTextRuns(body)
                body.contains("<t") -> extractTextRuns(body)
                value != null -> value
                else -> ""
            }
        }.toList()
    }.toList()

    return rowsToStudents(rows, warnings)
}

private fun parseSharedStrings(xml: String): List<String> {
    val itemRegex = Regex("<(?:\\w+:)?si[^>]*>(.*?)</(?:\\w+:)?si>", RegexOption.DOT_MATCHES_ALL)
    return itemRegex.findAll(xml).map { match -> extractTextRuns(match.groupValues[1]) }.toList()
}

private fun extractTextRuns(xml: String): String {
    val textRegex = Regex("<(?:\\w+:)?t[^>]*?>(.*?)</(?:\\w+:)?t>", RegexOption.DOT_MATCHES_ALL)
    return textRegex.findAll(xml).joinToString("") { it.groupValues[1].xmlUnescape() }
}

private fun studentNumberFromCell(cell: String): String? {
    val compact = cell.trim().replace(" ", "")
    val decimalNumber = compact
        .replace(',', '.')
        .takeIf { it.matches(Regex("[+-]?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")) }
        ?.let { value ->
            runCatching {
                val plain = BigDecimal(value).toPlainString()
                if (plain.substringAfter('.', "").all { it == '0' }) plain.substringBefore('.') else null
            }.getOrNull()
        }

    val digits = decimalNumber ?: compact.filter(Char::isDigit)
    return digits.takeIf { it.length >= MIN_STUDENT_NUMBER_DIGITS }
}

private fun studentNameFromRow(row: List<String>, number: String): String {
    val numberIndex = row.indexOfFirst { studentNumberFromCell(it) == number }
    val searchRow = if (numberIndex >= 0) row.drop(numberIndex + 1) else row
    val candidates = searchRow.map { cell ->
        cell.replace(number, " ")
            .replace(Regex("\\d+"), " ")
            .replace(Regex("(?i)\\b(öğrenci|ogrenci|numara|numarası|numarasi|no|adı|adi|ad|soyadı|soyadi|soyad|tc|kimlik)\\b"), " ")
            .replace(Regex("[:;,_-]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }.filter { value ->
        value.length > 2 &&
            value.any(Char::isLetter) &&
            !value.contains("@") &&
            !looksLikeCourseCode(value) &&
            !looksLikeHeaderOrMetadata(listOf(value))
    }

    val preferred = candidates
        .take(2)
        .takeIf { it.size == 2 && it.all { value -> !value.contains(" ") && value.length <= 30 } }
        ?.joinToString(" ")
        ?: candidates.firstOrNull { it.contains(" ") }
        ?: candidates.joinToString(" ")
    return preferred.trim().replace(Regex("\\s+"), " ")
}

private data class StudentColumns(
    val number: Int? = null,
    val firstName: Int? = null,
    val lastName: Int? = null,
    val fullName: Int? = null,
    val department: Int? = null
) {
    val isStudentHeader: Boolean get() = number != null && (firstName != null || lastName != null || fullName != null)
}

private fun detectStudentColumns(row: List<String>): StudentColumns {
    var number: Int? = null
    var firstName: Int? = null
    var lastName: Int? = null
    var fullName: Int? = null
    var department: Int? = null

    row.forEachIndexed { index, value ->
        val label = value.searchFold().replace(Regex("\\s+"), " ").trim()
        when {
            ("ogrenci" in label && ("no" in label || "numara" in label)) || label == "student no" -> number = index
            label in setOf("ad", "adi", "isim", "first name") -> firstName = index
            label in setOf("soyad", "soyadi", "last name", "surname") -> lastName = index
            label in setOf("ad soyad", "adi soyadi", "ogrenci adi soyadi", "ogrenci ad soyad", "name") -> fullName = index
            label in setOf("bolum", "department", "program") -> department = index
        }
    }

    return StudentColumns(number, firstName, lastName, fullName, department)
}

private fun studentNameFromColumns(row: List<String>, columns: StudentColumns): String {
    val fullName = columns.fullName?.let { row.getOrNull(it)?.trim() }.orEmpty()
    if (fullName.isNotBlank()) return fullName

    return listOfNotNull(
        columns.firstName?.let { row.getOrNull(it)?.trim() },
        columns.lastName?.let { row.getOrNull(it)?.trim() }
    ).filter { it.isNotBlank() }.joinToString(" ")
}

private fun looksLikeHeaderOrMetadata(row: List<String>): Boolean {
    val flat = row.joinToString(" ").searchFold()
    if (flat.isBlank()) return true
    val tokens = flat.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    return tokens.any { it in HEADER_OR_METADATA_WORDS } || "ad soyad" in flat || "adi soyadi" in flat
}

private fun looksLikeDepartmentCode(value: String): Boolean {
    val letters = value.filter(Char::isLetter)
    return letters.length in 2..5 && letters.all(Char::isUpperCase)
}

private fun looksLikeCourseCode(value: String): Boolean {
    return value.matches(Regex("[A-ZÇĞİÖŞÜ]{2,}\\d{2,}[A-Z0-9-]*"))
}

private fun worksheetOrder(name: String): Int {
    return Regex("sheet(\\d+)\\.xml").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
}

private fun String.searchFold(): String = lowercase()
    .replace("ı", "i")
    .replace("ğ", "g")
    .replace("ü", "u")
    .replace("ş", "s")
    .replace("ö", "o")
    .replace("ç", "c")

private fun String.xmlUnescape(): String = replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")

private const val MAX_XLSX_FILE_BYTES = 10 * 1024 * 1024
private const val MAX_XML_ENTRY_BYTES = 6 * 1024 * 1024
private const val MAX_ROW_COUNT = 12000
private const val MIN_STUDENT_NUMBER_DIGITS = 5
private const val MAX_WARNING_COUNT = 20

private val HEADER_OR_METADATA_WORDS = setOf(
    "ogrenci",
    "numara",
    "liste",
    "sayfa",
    "sira",
    "adi",
    "soyadi",
    "tc",
    "kimlik",
    "fakulte",
    "bolum",
    "program",
    "ders",
    "toplam"
)

package com.mustafa.sinavtakvim.shared.utils

import com.mustafa.sinavtakvim.shared.models.Student

actual fun parseStudentSpreadsheet(bytes: ByteArray, fileName: String): StudentImportResult {
    if (fileName.endsWith(".xlsx", ignoreCase = true)) {
        return StudentImportResult(emptyList(), listOf("iOS hedefinde XLSX ayrıştırma bu sürümde desteklenmiyor. CSV kullanın."))
    }
    val rows = bytes.decodeToString()
        .replace("\uFEFF", "")
        .lines()
        .filter { it.isNotBlank() }
        .map { line -> line.split(if (line.contains(";")) ";" else ",").map { it.trim().trim('"') } }
    val students = rows.drop(1).mapNotNull { row ->
        val number = row.getOrNull(0).orEmpty()
        val name = row.getOrNull(1).orEmpty()
        if (number.isBlank() || name.isBlank()) null else Student(id = number, studentNumber = number, name = name, department = row.getOrNull(2).orEmpty())
    }
    return StudentImportResult(students)
}

actual fun saveReportFile(fileName: String, bytes: ByteArray): String = fileName

package com.mustafa.sinavtakvim.shared.utils

import com.mustafa.sinavtakvim.shared.models.Course
import com.mustafa.sinavtakvim.shared.models.Exam
import com.mustafa.sinavtakvim.shared.models.Room
import com.mustafa.sinavtakvim.shared.models.Student
import com.mustafa.sinavtakvim.shared.models.User

data class StudentImportResult(
    val students: List<Student>,
    val warnings: List<String> = emptyList()
)

expect fun parseStudentSpreadsheet(bytes: ByteArray, fileName: String): StudentImportResult

expect fun saveReportFile(fileName: String, bytes: ByteArray): String

fun buildScheduleCsv(
    exams: List<Exam>,
    courses: Map<String, Course>,
    rooms: Map<String, Room>,
    users: Map<String, User>
): ByteArray {
    val header = listOf("Ders Kodu", "Ders Adı", "Sınav Türü", "Akademik Dönem", "Tarih", "Saat", "Salon", "Gözetmen", "Öğrenci")
    return (listOf(header) + scheduleRows(exams, courses, rooms, users))
        .joinToString("\n") { row -> row.joinToString(";") { it.csvCell() } }
        .toByteArrayWithBom()
}

fun buildScheduleExcelXml(
    exams: List<Exam>,
    courses: Map<String, Course>,
    rooms: Map<String, Room>,
    users: Map<String, User>
): ByteArray {
    val rows = listOf(
        listOf("Ders Kodu", "Ders Adı", "Sınav Türü", "Akademik Dönem", "Tarih", "Saat", "Salon", "Gözetmen", "Öğrenci")
    ) + scheduleRows(exams, courses, rooms, users)

    val xml = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append('\n')
        append("""<?mso-application progid="Excel.Sheet"?>""")
        append('\n')
        append("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" """)
        append("""xmlns:o="urn:schemas-microsoft-com:office:office" """)
        append("""xmlns:x="urn:schemas-microsoft-com:office:excel" """)
        append("""xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">""")
        append('\n')
        append("""<Styles><Style ss:ID="Header"><Font ss:Bold="1"/><Interior ss:Color="#E5EEEA" ss:Pattern="Solid"/></Style></Styles>""")
        append('\n')
        append("""<Worksheet ss:Name="Sınav Programı"><Table>""")
        append('\n')
        rows.forEachIndexed { rowIndex, row ->
            append("<Row>")
            row.forEach { cell ->
                val style = if (rowIndex == 0) " ss:StyleID=\"Header\"" else ""
                append("""<Cell$style><Data ss:Type="String">${cell.xmlCell()}</Data></Cell>""")
            }
            append("</Row>\n")
        }
        append("</Table></Worksheet></Workbook>")
    }
    return xml.toByteArrayWithBom()
}

fun buildSchedulePdf(
    exams: List<Exam>,
    courses: Map<String, Course>,
    rooms: Map<String, Room>,
    users: Map<String, User>
): ByteArray {
    val term = exams.firstOrNull()?.academicTerm.orEmpty()
    val type = exams.firstOrNull()?.examType.orEmpty()
    val lines = mutableListOf("Sinav Programi", "Donem: $term  Tur: $type", "Oturum: ${exams.size}", "")
    exams.sortedWith(compareBy<Exam> { it.date }.thenBy { it.slotId }).forEach { exam ->
        val course = courses[exam.courseId]
        val roomNames = exam.assignments.mapNotNull { rooms[it.roomId]?.name }.joinToString(", ")
        val proctorNames = exam.assignments.mapNotNull { users[it.proctorId]?.name }.joinToString(", ")
        lines += "${course?.code ?: exam.courseId} - ${course?.name.orEmpty()}"
        lines += "${examDateLabel(exam.date)} ${slotLabel(exam.slotId)} | Salon: $roomNames"
        lines += "Gozetmen: $proctorNames | Ogrenci: ${course?.studentCount ?: 0}"
        lines += ""
    }
    return SimplePdfWriter.write(lines.map { it.toPdfSafe() })
}

private fun scheduleRows(
    exams: List<Exam>,
    courses: Map<String, Course>,
    rooms: Map<String, Room>,
    users: Map<String, User>
): List<List<String>> {
    return exams.sortedWith(compareBy<Exam> { it.date }.thenBy { it.slotId }).map { exam ->
        val course = courses[exam.courseId]
        val roomNames = exam.assignments.mapNotNull { rooms[it.roomId]?.name }.joinToString(" / ")
        val proctorNames = exam.assignments.mapNotNull { users[it.proctorId]?.name }.joinToString(" / ")
        listOf(
            course?.code.orEmpty(),
            course?.name.orEmpty(),
            exam.examType,
            exam.academicTerm,
            examDateLabel(exam.date),
            slotLabel(exam.slotId),
            roomNames,
            proctorNames,
            (course?.studentCount ?: 0).toString()
        )
    }
}

private fun String.csvCell(): String {
    val escaped = replace("\"", "\"\"")
    return if (escaped.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"$escaped\"" else escaped
}

private fun String.xmlCell(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun String.toByteArrayWithBom(): ByteArray {
    return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + encodeToByteArray()
}

private fun String.toPdfSafe(): String {
    return replace("ı", "i")
        .replace("İ", "I")
        .replace("ğ", "g")
        .replace("Ğ", "G")
        .replace("ü", "u")
        .replace("Ü", "U")
        .replace("ş", "s")
        .replace("Ş", "S")
        .replace("ö", "o")
        .replace("Ö", "O")
        .replace("ç", "c")
        .replace("Ç", "C")
        .replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)")
}

private object SimplePdfWriter {
    fun write(lines: List<String>): ByteArray {
        val chunks = lines.chunked(48).ifEmpty { listOf(listOf("Sinav Programi")) }
        val objects = mutableListOf<String>()
        objects += "<< /Type /Catalog /Pages 2 0 R >>"

        val pageObjectStart = 3
        val fontObjectNumber = pageObjectStart + chunks.size
        val contentObjectStart = fontObjectNumber + 1
        val pageRefs = chunks.indices.joinToString(" ") { "${pageObjectStart + it} 0 R" }
        objects += "<< /Type /Pages /Kids [$pageRefs] /Count ${chunks.size} >>"

        chunks.indices.forEach { index ->
            objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 $fontObjectNumber 0 R >> >> /Contents ${contentObjectStart + index} 0 R >>"
        }

        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"

        chunks.forEach { chunk ->
            val stream = buildString {
                append("BT\n/F1 12 Tf\n50 790 Td\n")
                chunk.forEachIndexed { index, line ->
                    if (index > 0) append("0 -16 Td\n")
                    append("($line) Tj\n")
                }
                append("ET")
            }
            objects += "<< /Length ${stream.encodeToByteArray().size} >>\nstream\n$stream\nendstream"
        }

        val result = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objects.forEachIndexed { index, obj ->
            offsets += result.length
            result.append("${index + 1} 0 obj\n$obj\nendobj\n")
        }
        val xref = result.length
        result.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { offset ->
            result.append(offset.toString().padStart(10, '0')).append(" 00000 n \n")
        }
        result.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF")
        return result.toString().encodeToByteArray()
    }
}

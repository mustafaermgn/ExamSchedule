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
    val rows = exams.sortedWith(compareBy<Exam> { it.date }.thenBy { it.slotId }).map { exam ->
        val course = courses[exam.courseId]
        val assignedRooms = exam.assignments.mapNotNull { rooms[it.roomId] }
        SchedulePdfRow(
            department = course?.departmentId?.ifBlank { "BELIRSIZ" } ?: "BELIRSIZ",
            courseCode = course?.code?.ifBlank { exam.courseId } ?: exam.courseId,
            courseName = course?.name.orEmpty(),
            date = examDateLabel(exam.date),
            time = exam.slotLabel.ifBlank { slotLabel(exam.slotId) },
            rooms = assignedRooms.joinToString(", ") { it.name },
            proctors = exam.assignments.mapNotNull { users[it.proctorId]?.name }.joinToString(", "),
            studentCount = course?.studentCount ?: 0,
            assignedCapacity = assignedRooms.sumOf { it.capacity }
        )
    }
    return ProfessionalSchedulePdfWriter.write(term = term, type = type, rows = rows)
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
            exam.slotLabel.ifBlank { slotLabel(exam.slotId) },
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

private data class SchedulePdfRow(
    val department: String,
    val courseCode: String,
    val courseName: String,
    val date: String,
    val time: String,
    val rooms: String,
    val proctors: String,
    val studentCount: Int,
    val assignedCapacity: Int
)

private fun String.toPdfDisplayText(): String {
    return replace('\r', ' ')
        .replace('\n', ' ')
        .replace("\u0131", "i")
        .replace("\u0130", "I")
        .replace("\u011f", "g")
        .replace("\u011e", "G")
        .replace("\u00fc", "u")
        .replace("\u00dc", "U")
        .replace("\u015f", "s")
        .replace("\u015e", "S")
        .replace("\u00f6", "o")
        .replace("\u00d6", "O")
        .replace("\u00e7", "c")
        .replace("\u00c7", "C")
}

private fun String.toPdfSafe(): String {
    return toPdfDisplayText()
        .replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)")
}

private object ProfessionalSchedulePdfWriter {
    private const val pageWidth = 595
    private const val pageHeight = 842
    private const val margin = 32
    private const val contentWidth = pageWidth - margin * 2
    private const val footerTop = 806
    private const val tableHeaderHeight = 24
    private val columnWidths = listOf(52, 132, 70, 38, 86, 109, 44)
    private val columnTitles = listOf("Kod", "Ders", "Tarih", "Saat", "Salon", "Gozetmen", "O/K")

    fun write(term: String, type: String, rows: List<SchedulePdfRow>): ByteArray {
        val pages = mutableListOf<PdfPage>()
        var page = newPage(pages)
        var cursor = drawChrome(page, term, type, rows, isFirstPage = true)

        if (rows.isEmpty()) {
            page.text("Planlanmis sinav bulunmuyor.", margin + 16, cursor + 22, 12, bold = true, color = ColorInk)
        } else {
            rows.groupBy { it.department }.toSortedMap().forEach { (department, departmentRows) ->
                val sectionStart = ensureSpace(pages, cursor, 78, term, type, rows)
                page = sectionStart.page
                cursor = sectionStart.top
                cursor = drawDepartmentHeader(page, cursor, department, departmentRows.size)
                cursor = drawTableHeader(page, cursor)

                departmentRows.forEachIndexed { index, row ->
                    val cellLines = row.cellLines()
                    val rowHeight = 20 + cellLines.maxOf { it.size }.coerceAtLeast(1) * 9
                    val rowStart = ensureSpace(pages, cursor, rowHeight, term, type, rows)
                    page = rowStart.page
                    cursor = rowStart.top
                    if (rowStart.isNewPage) {
                        cursor = drawDepartmentHeader(page, cursor, "$department (devam)", departmentRows.size)
                        cursor = drawTableHeader(page, cursor)
                    }
                    drawRow(page, cursor, rowHeight, cellLines, index % 2 == 1)
                    cursor += rowHeight
                }
                cursor += 8
            }
        }

        pages.forEachIndexed { index, pdfPage -> drawFooter(pdfPage, index + 1, pages.size) }
        return buildPdf(pages)
    }

    private fun newPage(pages: MutableList<PdfPage>): PdfPage {
        val page = PdfPage()
        pages += page
        return page
    }

    private fun ensureSpace(
        pages: MutableList<PdfPage>,
        cursor: Int,
        neededHeight: Int,
        term: String,
        type: String,
        rows: List<SchedulePdfRow>
    ): PageCursor {
        if (cursor + neededHeight <= footerTop - 18) return PageCursor(pages.last(), cursor, isNewPage = false)
        val next = newPage(pages)
        return PageCursor(next, drawChrome(next, term, type, rows, isFirstPage = false), isNewPage = true)
    }

    private fun drawChrome(page: PdfPage, term: String, type: String, rows: List<SchedulePdfRow>, isFirstPage: Boolean): Int {
        page.fill(margin, 28, contentWidth, 48, ColorInk)
        page.text("SINAV PROGRAMI", margin + 16, 42, 18, bold = true, color = ColorWhite)
        page.text("Akademik Donem: ${term.ifBlank { "-" }}   |   Sinav Turu: ${type.ifBlank { "-" }}", margin + 16, 63, 9, color = ColorMutedOnDark)
        page.text("Sinav Takvim Sistemi", pageWidth - margin - 118, 45, 9, bold = true, color = ColorMutedOnDark)

        if (!isFirstPage) {
            page.text("Program tablosu devam ediyor", margin, 94, 10, bold = true, color = ColorSteel)
            return 112
        }

        val totalStudents = rows.sumOf { it.studentCount }
        val totalCapacity = rows.sumOf { it.assignedCapacity }
        val departments = rows.map { it.department }.distinct().size
        val utilization = if (totalCapacity == 0) "0%" else "%${((totalStudents * 100) / totalCapacity).coerceAtMost(999)}"
        val cardTop = 96
        val cardGap = 10
        val cardWidth = (contentWidth - cardGap * 3) / 4
        drawMetricCard(page, margin, cardTop, cardWidth, "Oturum", rows.size.toString())
        drawMetricCard(page, margin + (cardWidth + cardGap), cardTop, cardWidth, "Ogrenci", totalStudents.toString())
        drawMetricCard(page, margin + (cardWidth + cardGap) * 2, cardTop, cardWidth, "Kapasite", totalCapacity.toString())
        drawMetricCard(page, margin + (cardWidth + cardGap) * 3, cardTop, cardWidth, "Doluluk", utilization)
        page.text("$departments bolum icin olusturulmus resmi sinav plani", margin, 160, 10, color = ColorSteel)
        return 178
    }

    private fun drawMetricCard(page: PdfPage, x: Int, top: Int, width: Int, label: String, value: String) {
        page.fill(x, top, width, 48, ColorSoft)
        page.stroke(x, top, width, 48, ColorBorder)
        page.text(label.uppercase(), x + 10, top + 10, 8, bold = true, color = ColorSteel)
        page.text(value, x + 10, top + 28, 15, bold = true, color = ColorInk)
    }

    private fun drawDepartmentHeader(page: PdfPage, top: Int, department: String, count: Int): Int {
        page.fill(margin, top, contentWidth, 26, ColorSection)
        page.text("Bolum: $department", margin + 10, top + 9, 10, bold = true, color = ColorInk)
        page.text("$count oturum", pageWidth - margin - 70, top + 9, 9, bold = true, color = ColorSteel)
        return top + 30
    }

    private fun drawTableHeader(page: PdfPage, top: Int): Int {
        page.fill(margin, top, contentWidth, tableHeaderHeight, ColorHeader)
        var x = margin
        columnTitles.forEachIndexed { index, title ->
            page.text(title, x + 5, top + 9, 8, bold = true, color = ColorWhite)
            x += columnWidths[index]
        }
        return top + tableHeaderHeight
    }

    private fun drawRow(page: PdfPage, top: Int, height: Int, lines: List<List<String>>, alternate: Boolean) {
        page.fill(margin, top, contentWidth, height, if (alternate) ColorAlternate else ColorWhite)
        page.stroke(margin, top, contentWidth, height, ColorBorder)
        var x = margin
        lines.forEachIndexed { index, cellLines ->
            if (index > 0) page.line(x, top, x, top + height, ColorBorder)
            cellLines.take(3).forEachIndexed { lineIndex, text ->
                page.text(text, x + 5, top + 8 + lineIndex * 9, if (index == 0 || index == 6) 8 else 7, bold = index == 0, color = ColorInk)
            }
            x += columnWidths[index]
        }
    }

    private fun SchedulePdfRow.cellLines(): List<List<String>> {
        return listOf(
            listOf(courseCode.ellipsize(10)),
            wrapCell(courseName.ifBlank { "-" }, columnWidths[1] - 10, 2),
            listOf(date.ellipsize(16)),
            listOf(time.ellipsize(8)),
            wrapCell(rooms.ifBlank { "-" }, columnWidths[4] - 10, 2),
            wrapCell(proctors.ifBlank { "-" }, columnWidths[5] - 10, 2),
            listOf("$studentCount/$assignedCapacity")
        )
    }

    private fun wrapCell(value: String, width: Int, maxLines: Int): List<String> {
        val safeWords = value.toPdfDisplayText().split(Regex("\\s+")).filter { it.isNotBlank() }
        val limit = (width / 4).coerceAtLeast(8)
        val lines = mutableListOf<String>()
        var line = ""
        safeWords.forEach { word ->
            val candidate = if (line.isBlank()) word else "$line $word"
            if (candidate.length <= limit) {
                line = candidate
            } else {
                if (line.isNotBlank()) lines += line
                line = word
            }
        }
        if (line.isNotBlank()) lines += line
        val clipped = lines.ifEmpty { listOf("-") }.take(maxLines).toMutableList()
        if (lines.size > maxLines) clipped[maxLines - 1] = clipped[maxLines - 1].ellipsize(limit)
        return clipped
    }

    private fun String.ellipsize(maxLength: Int): String {
        val safe = toPdfDisplayText()
        return if (safe.length <= maxLength) safe else safe.take((maxLength - 3).coerceAtLeast(1)) + "..."
    }

    private fun drawFooter(page: PdfPage, pageNumber: Int, pageCount: Int) {
        page.line(margin, footerTop, pageWidth - margin, footerTop, ColorBorder)
        page.text("Sinav Takvim Sistemi - otomatik olusturulmus rapor", margin, footerTop + 14, 8, color = ColorSteel)
        page.text("Sayfa $pageNumber / $pageCount", pageWidth - margin - 62, footerTop + 14, 8, color = ColorSteel)
    }

    private fun buildPdf(pages: List<PdfPage>): ByteArray {
        val objects = mutableListOf<String>()
        objects += "<< /Type /Catalog /Pages 2 0 R >>"

        val pageObjectStart = 3
        val regularFontObject = pageObjectStart + pages.size
        val boldFontObject = regularFontObject + 1
        val contentObjectStart = boldFontObject + 1
        val pageRefs = pages.indices.joinToString(" ") { "${pageObjectStart + it} 0 R" }
        objects += "<< /Type /Pages /Kids [$pageRefs] /Count ${pages.size} >>"

        pages.indices.forEach { index ->
            objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageWidth $pageHeight] /Resources << /Font << /F1 $regularFontObject 0 R /F2 $boldFontObject 0 R >> >> /Contents ${contentObjectStart + index} 0 R >>"
        }

        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>"

        pages.forEach { page ->
            val stream = page.content
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
        offsets.forEach { offset -> result.append(offset.toString().padStart(10, '0')).append(" 00000 n \n") }
        result.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF")
        return result.toString().encodeToByteArray()
    }

    private val ColorInk = PdfColor("0.06", "0.09", "0.16")
    private val ColorSteel = PdfColor("0.28", "0.33", "0.41")
    private val ColorSoft = PdfColor("0.95", "0.97", "0.99")
    private val ColorSection = PdfColor("0.90", "0.95", "0.93")
    private val ColorHeader = PdfColor("0.18", "0.25", "0.33")
    private val ColorBorder = PdfColor("0.82", "0.86", "0.91")
    private val ColorAlternate = PdfColor("0.98", "0.99", "1.00")
    private val ColorWhite = PdfColor("1.00", "1.00", "1.00")
    private val ColorMutedOnDark = PdfColor("0.78", "0.84", "0.91")

    private data class PdfColor(val r: String, val g: String, val b: String)
    private data class PageCursor(val page: PdfPage, val top: Int, val isNewPage: Boolean)

    private class PdfPage {
        private val stream = StringBuilder()
        val content: String get() = stream.toString()

        fun fill(x: Int, top: Int, width: Int, height: Int, color: PdfColor) {
            val bottom = pageHeight - top - height
            stream.append("q ${color.r} ${color.g} ${color.b} rg $x $bottom $width $height re f Q\n")
        }

        fun stroke(x: Int, top: Int, width: Int, height: Int, color: PdfColor) {
            val bottom = pageHeight - top - height
            stream.append("q ${color.r} ${color.g} ${color.b} RG 0.6 w $x $bottom $width $height re S Q\n")
        }

        fun line(x1: Int, top1: Int, x2: Int, top2: Int, color: PdfColor) {
            val y1 = pageHeight - top1
            val y2 = pageHeight - top2
            stream.append("q ${color.r} ${color.g} ${color.b} RG 0.6 w $x1 $y1 m $x2 $y2 l S Q\n")
        }

        fun text(value: String, x: Int, top: Int, size: Int, bold: Boolean = false, color: PdfColor) {
            val y = pageHeight - top - size
            val font = if (bold) "F2" else "F1"
            stream.append("BT /$font $size Tf ${color.r} ${color.g} ${color.b} rg 1 0 0 1 $x $y Tm (${value.toPdfSafe()}) Tj ET\n")
        }
    }
}

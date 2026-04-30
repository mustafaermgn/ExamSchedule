package com.mustafa.sinavtakvim.shared.data

import com.mustafa.sinavtakvim.shared.algorithms.GreedySolver
import com.mustafa.sinavtakvim.shared.models.Course
import com.mustafa.sinavtakvim.shared.models.DateRange
import com.mustafa.sinavtakvim.shared.models.Exam
import com.mustafa.sinavtakvim.shared.models.LogEntry
import com.mustafa.sinavtakvim.shared.models.Room
import com.mustafa.sinavtakvim.shared.models.User
import com.mustafa.sinavtakvim.shared.models.UserRole

object DemoData {
    private const val firstExamDay = 1_777_960_800_000L

    val courses = listOf(
        Course("c1", "MAT101", "Matematik I", 72, 1, "BIL", "Dr. Ayşe Demir"),
        Course("c2", "BIL101", "Programlama Temelleri", 58, 1, "BIL", "Dr. Kerem Yılmaz"),
        Course("c3", "FIZ102", "Fizik II", 44, 2, "BIL", "Dr. Elif Kaya"),
        Course("c4", "VYA201", "Veri Yapıları", 67, 3, "BIL", "Dr. Can Arslan"),
        Course("c5", "ALG202", "Algoritma Analizi", 52, 3, "BIL", "Dr. Nil Öz"),
        Course("c6", "VTB301", "Veritabanı Sistemleri", 39, 5, "BIL", "Dr. Deniz Acar"),
        Course("c7", "YMH401", "Yazılım Mimarisi", 31, 7, "BIL", "Dr. Bora Şahin"),
        Course("c8", "YZK402", "Yapay Zeka", 36, 7, "BIL", "Dr. Selen Aksoy")
    )

    val rooms = listOf(
        Room("r1", "D-101", 48, 1, 41.0082, 28.9784, "Mühendislik Fakültesi", listOf("Projeksiyon", "Klima")),
        Room("r2", "D-102", 42, 1, 41.0085, 28.9788, "Mühendislik Fakültesi", listOf("Projeksiyon")),
        Room("r3", "D-203", 36, 2, 41.0089, 28.9792, "Mühendislik Fakültesi", listOf("Akıllı tahta")),
        Room("r4", "Amfi A", 96, 0, 41.0077, 28.9779, "Merkez Derslik", listOf("Ses sistemi", "Erişilebilir giriş")),
        Room("r5", "Lab-1", 28, 3, 41.0093, 28.9795, "Bilgisayar Laboratuvarı", listOf("Bilgisayar", "Ağ")),
        Room("r6", "Seminer-2", 24, 2, 41.0079, 28.9789, "Merkez Derslik", listOf("Konferans düzeni"))
    )

    val users = listOf(
        User("u-admin", "Fakülte Sekreterliği", "admin@fakulte.edu.tr", UserRole.ADMIN, "BIL"),
        User("p1", "Arş. Gör. Mert Çelik", "mert.celik@fakulte.edu.tr", UserRole.PROCTOR, "BIL"),
        User("p2", "Arş. Gör. İrem Koç", "irem.koc@fakulte.edu.tr", UserRole.PROCTOR, "BIL", listOf(DateRange(firstExamDay + 2 * 60 * 60 * 1000L, firstExamDay + 4 * 60 * 60 * 1000L))),
        User("p3", "Öğr. Gör. Selin Güneş", "selin.gunes@fakulte.edu.tr", UserRole.PROCTOR, "BIL"),
        User("p4", "Arş. Gör. Ege Şen", "ege.sen@fakulte.edu.tr", UserRole.PROCTOR, "BIL"),
        User("p5", "Arş. Gör. Zeynep Korkmaz", "zeynep.korkmaz@fakulte.edu.tr", UserRole.PROCTOR, "BIL"),
        User("p6", "Öğr. Gör. Cem Uslu", "cem.uslu@fakulte.edu.tr", UserRole.PROCTOR, "BIL")
    )

    val logs = listOf(
        LogEntry("l1", "seed", "", "Demo veri seti yüklendi", "u-admin", firstExamDay),
        LogEntry("l2", "analysis", "", "DP ve sezgisel motor karşılaştırıldı", "u-admin", firstExamDay + 60_000L)
    )

    fun exams(): List<Exam> = GreedySolver().solve(courses, rooms, users).exams
}

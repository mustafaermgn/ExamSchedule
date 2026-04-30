# Sınav Takvim Sistemi

Kotlin Multiplatform ve Compose Multiplatform ile geliştirilen fakülte sınav takvimi karar destek sistemi.

## Demo Giriş

- Yönetici: `admin@fakulte.edu.tr`
- Gözetmen: `mert.celik@fakulte.edu.tr`
- Şifre: `123456`

Firebase erişimi yoksa desktop uygulama yerel demo veri setiyle çalışır. Android hedefinde Firebase Auth/Firestore kullanılabilir.

## Temel Akış

1. Yönetici dersleri açar.
2. Her ders için `.xlsx`, `.csv` veya `.tsv` öğrenci listesini yükler.
3. Sistem öğrenci numarası, ad soyad ve bölüm alanlarını okuyup dersi ilgili öğrencilerle eşleştirir.
4. Yönetici salon ve gözetmen havuzunu tamamlar.
5. Planlama ekranında DP Solver ve Sezgisel Solver birlikte çalıştırılır.
6. Yönetici istediği algoritma sonucunu seçip takvime uygular.
7. Sınav programı Excel uyumlu `.xls` veya `.pdf` olarak dışa aktarılır.

## Rol Bazlı Yetki

- Yönetici: panel, veri hazırlığı, planlama/analiz, tüm sınav takvimi, harita ve mazeret yönetimini görür.
- Gözetmen: yalnızca kendi görevlerini, görev takvimini ve görev aldığı salonları görür.
- Gözetmen ekranlarında algoritma karşılaştırması, veri yükleme, kullanıcı yönetimi ve resmi program üretme aksiyonları bulunmaz.

## Karşılanan İsterler

- Ön yüzler: giriş, yönetici paneli, veri hazırlığı, planlama/analiz, takvim, harita, mazeret yönetimi, gözetmen görev paneli.
- UI elemanları: Scaffold, LazyColumn, Card, BottomNavigation, NavigationRail, OutlinedTextField, Button, Status/Metric kartları.
- Algoritmalar: DP salon seçimi ve sezgisel salon/oturum ataması.
- İş kuralları: dönem çakışması, kapasite kontrolü, gözetmen mazereti, aynı oturum çakışması, en fazla 3 ardışık oturum.
- Servis/veri: Firestore koleksiyonları ve çevrimdışı demo veri fallback yapısı.
- Harita: Android hedefinde Google Maps Compose, desktop hedefinde koordinatlı salon görünümü.
- Cihaz özelliği: Android takvim intent'i ile sınavı takvime ekleme.

## Doğrulama

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
& 'C:\Users\musta\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat' :composeApp:compileKotlinDesktop --no-daemon --rerun-tasks
& 'C:\Users\musta\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat' :composeApp:compileDebugKotlinAndroid --no-daemon
& 'C:\Users\musta\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat' :composeApp:assembleDebug --no-daemon
```

Oluşan APK:

`composeApp/build/outputs/apk/debug/composeApp-debug.apk`

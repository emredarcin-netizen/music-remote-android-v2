# Music Remote — Android APK projesi

Spotify veya YouTube Music medya oturumunu algılamaya çalışan, albüm kapağı/parça bilgisi gösteren ve önceki/oynat-duraklat/sonraki komutlarını gönderen temel Android uygulaması.

## Gereksinimler
- Android Studio (Giraffe veya daha yeni önerilir)
- JDK 17
- Android SDK Platform 35 ve Android Build Tools
- İlk Gradle senkronizasyonu için internet bağlantısı

## APK oluşturma
1. ZIP dosyasını bilgisayarınıza çıkarın.
2. Android Studio > **Open** ile `MusicRemoteAndroid` klasörünü açın.
3. Gradle Sync tamamlanmasını bekleyin. SDK veya Gradle eklentisi indirme isteğini kabul edin.
4. **Build > Build Bundle(s) / APK(s) > Build APK(s)** seçin.
5. APK tipik olarak `app/build/outputs/apk/debug/app-debug.apk` konumunda oluşur.
6. APK'yı Carlinkit TBox'a USB bellek veya uygun bir dosya aktarımıyla taşıyın ve açın. Bilinmeyen kaynaklardan uygulama yükleme izni istenirse yalnızca bu dosya için izin verin.

## İlk kullanım
1. Music Remote uygulamasını açın.
2. **Medya erişimini etkinleştir** düğmesine basın.
3. Android ayarlarında Music Remote için bildirim erişimini etkinleştirin ve geri dönün.
4. Spotify veya YouTube Music'i açıp bir parça çalın.
5. Kapak, parça bilgisi ve kontrol düğmelerini deneyin.

## Önemli sınırlamalar
- Bu, ilk derleme ve cihaz testi için başlangıç projesidir; bu çalışma ortamında Android SDK/Gradle ile derlenip gerçek TBox üzerinde test edilmiş bir APK değildir.
- Bazı Spotify sürümlerinde yayınlanan medya bildirimleri/oturumları uygulama ayarlarına bağlıdır. YouTube Music veya cihaz yazılımı da medya oturumunu farklı sunabilir. Böyle bir durumda kaynak uygulama Android'in aktif oturum listesinde görünmeyebilir.
- Bu ilk sürüm parça konumunu göstermez ve arama/çalma listesi seçimi yapmaz.
- Derleme sırasında Android Gradle Plugin veya SDK sürümüyle ilgili hata çıkarsa Android Studio'nun önerdiği uyumlu SDK bileşenlerini yükleyin.

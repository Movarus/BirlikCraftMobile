<h1 align="center">BirlikCraft Mobile</h1>

<p align="center">
  <b>BirlikCraft için Android Minecraft: Java Edition başlatıcısı</b><br>
  Geliştiren: <b>Movarus &amp; TaislaGroup</b><br>
  Web sitesi: <a href="https://movarus.info">movarus.info</a> | Sunucu IP: <code>oyna.birlikcraft.com</code> | Discord: <a href="https://discord.gg/birlik">discord.gg/birlik</a>
</p>

[![Discord](https://img.shields.io/discord/1120748090807291975.svg?label=Discord&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/birlik)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

> [!IMPORTANT]
> BirlikCraft Mobile, [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2) projesinin **resmi olmayan değiştirilmiş bir sürümüdür**. Zalith Launcher ekibiyle bağlantılı değildir ve onlar tarafından desteklenmez. Bu sürümle ilgili sorunları Zalith Launcher ekibine değil, bize bildirin.

## Hakkında

BirlikCraft Mobile, BirlikCraft topluluğu için hazırlanmış bir Android Minecraft: Java Edition başlatıcısıdır. Temelinde, **Jetpack Compose** ve **Material Design 3** ile yazılmış modern bir başlatıcı olan [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2) var. Zalith Launcher 2 de oyunu başlatma motoru olarak [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) kullanır.

## Derleme (Geliştiriciler için)

### Gereksinimler
* Android Studio (güncel sürüm)
* JDK 21
* Android SDK (minimum API 26) ve Android NDK
* Rust toolchain ve [cargo-ndk](https://github.com/bbqsrc/cargo-ndk) (`PetLib` modülü için)

### Adımlar
```bash
git clone https://github.com/Movarus/BirlikCraftMobile.git
# Projeyi Android Studio ile açıp derleyin
# veya komut satırından:
./gradlew :ZalithLauncher:assembleDebug
```

GitHub Actions, her push'ta debug APK'ları otomatik olarak derler (`.github/workflows/push_ci.yml`).

### Uygulama adı ve kimliği
* Uygulama adı, kısa adı ve ana sayfa adresi: [`ZalithLauncher/gradle.properties`](ZalithLauncher/gradle.properties)
* Paket adı (`applicationId`): `com.taisla.birlikcraft` ([`ZalithLauncher/build.gradle.kts`](ZalithLauncher/build.gradle.kts))

### İmzalama
Release sürümleri `ZalithLauncher/birlikcraft_release.jks` anahtarıyla (alias: `birlikcraft`) imzalanır. Şifre repoda yok. GitHub Actions şifreyi `STORE_PASSWORD` ve `KEY_PASSWORD` adlı GitHub Secrets'tan okur (ikisine de aynı şifre girilir). Yerelde derlerken şifreyi `.store_password.txt` ve `.key_password.txt` dosyalarına yazabilirsiniz; bu dosyalar `.gitignore` ile dışarıda tutulur.

Debug sürümleri, şifresi `ZalithLauncher/gradle.properties` içinde açık olan `zalith_launcher_debug.jks` ile imzalanır.

## Lisans

Bu proje **[GPL-3.0](LICENSE)** lisansı altındadır. Zalith Launcher 2'nin GPLv3 7. maddesi kapsamındaki ek şartları da geçerlidir:

1. Değiştirilmiş sürümler, adlarında "ZalithLauncher", "ZL" veya bunlara karışabilecek benzer bir ad kullanamaz.
2. Değiştirilmiş sürümler, ana arayüzde **resmi olmayan sürüm** olduklarını açıkça belirtmelidir. BirlikCraft Mobile bunu ana ekranda küçük bir teşekkür satırıyla gösterir.
3. Programın gösterdiği telif hakkı bildirimleri kaldırılamaz.

Ayrıntılar ve kullanılan açık kaynak kütüphanelerin listesi için Zalith Launcher 2'nin orijinal README dosyasına bakın: [ZALITH_README_EN.md](ZALITH_README_EN.md).

## Teşekkürler

* **[Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2)**: MovTery ve katkıda bulunanlar
* **[PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)**: başlatma motoru
* **BirlikCraft özelleştirmeleri**: Movarus &amp; TaislaGroup

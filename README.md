<h1 align="center">BirlikCraft Mobile</h1>

<p align="center">
  <b>BirlikCraft sunucusu için Android Minecraft: Java Edition başlatıcısı</b><br>
  Geliştiren: <b>Movarus &amp; TaislaGroup</b><br>
  Web sitesi: <a href="https://movarus.info">movarus.info</a> | Sunucu IP: <code>oyna.birlikcraft.com</code> | Discord: <a href="https://discord.gg/birlik">discord.gg/birlik</a>
</p>

[![Discord](https://img.shields.io/discord/1120748090807291975.svg?label=Discord&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/birlik)
[![License: LGPL v3](https://img.shields.io/badge/License-LGPL%20v3-blue.svg)](LICENSE)

## Hakkında

BirlikCraft Mobile, BirlikCraft sunucusuna özel bir Android Minecraft: Java Edition başlatıcısıdır. Temelinde, PojavLauncher'ın resmi devamı olan açık kaynaklı [Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android) var. Amethyst, Java 25'i ve Minecraft 26.x sürümlerini destekliyor.

## Özellikler

- **Sabit sürümler:** Yalnızca **Minecraft 26.3** ve **26.2** (Fabric) seçilebilir. Fabric oyun başlatılırken otomatik kurulur.
- **FPS modları:** Lithium, FerriteCore, ModernFix, EntityCulling ve ImmediatelyFast (ve zorunlu bağımlılıkları) Modrinth'ten otomatik indirilir ve SHA-1 ile doğrulanır.
- **Mod koruması:** `mods` klasöründe BirlikCraft'ın kurmadığı bir mod varsa oyun başlatılmaz ve hangi dosyaların kaldırılması gerektiği gösterilir.
- **En yüksek FPS ayarları:** İlk açılışta oyun ve başlatıcı ayarları performansa göre yapılandırılır (VSync kapalı, sınırsız FPS, hızlı grafik, %70 çözünürlük, büyük CPU çekirdekleri).
- **Kaynak paketi ve shader indirme:** Modrinth üzerinden, seçili sürüme uygun paketler.
- **Sunucuya özel arayüz:** Yatay ekrana sabit, logoya uygun altın ve antrasit tema, açılış yükleme ekranı, oyun içi intro videosu.

Kodun BirlikCraft'a özel kısımları `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/birlik/` klasöründe.

## Derleme

GitHub Actions her push'ta debug APK'sını derler (`.github/workflows/android.yml`). Actions sayfasındaki çalışmanın **Artifacts** bölümünden `app-debug (recommended)` dosyasını indirin.

Yerelde derlemek için:
```bash
git clone --recursive https://github.com/Movarus/BirlikCraftMobile.git
cd BirlikCraftMobile
./gradlew :app_pojavlauncher:assembleDebug
```

### İmzalama
Release sürümleri `app_pojavlauncher/birlikcraft_release.jks` anahtarıyla (alias: `birlikcraft`) imzalanır. Şifre repoda yok: GitHub Actions şifreyi `STORE_PASSWORD` ve `KEY_PASSWORD` adlı GitHub Secrets'tan okur (ikisine de aynı şifre girilir). Bu secret'lar yoksa release adımı atlanır, debug APK yine derlenir.

## Lisans ve teşekkürler

Bu proje, Amethyst-Android ve PojavLauncher gibi **[GNU LGPL v3](LICENSE)** lisansı altındadır.

* **[Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android)**: AngelAuraMC ve katkıda bulunanlar
* **[PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)**: PojavLauncherTeam ve katkıda bulunanlar
* **BirlikCraft özelleştirmeleri**: Movarus &amp; TaislaGroup

Başlatıcının içerdiği bileşenlerin (OpenJDK, LWJGL, Mesa, ANGLE vb.) kendi lisansları vardır. Ayrıntılar için Amethyst-Android'in README dosyasına bakın.

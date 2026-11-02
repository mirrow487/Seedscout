# Seedscout

> 🇹🇷 Türkçe açıklama aşağıda — [Türkçe](#türkçe)

A **client-side** Fabric mod for Minecraft **26.3** (Java Edition) that adds a seed finder to the
**Create World** screen. The goal is not "OP/cheat" seeds but survival worlds with nice terrain and
useful structures close to spawn — good places to build on.

> **Status:** in development. Stage 1 (project setup + buttons on the Create World screen) is done.

## Requirements

| | |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.20.1 or newer |
| Fabric API | 0.165.0+26.3 |
| Java | 25 |

Client only — the mod is never needed on a server.

## Features (planned per stage)

1. **Create World screen** (World tab, under the seed box): `Seed Finder` button, mode selector
   (`Random` / `Speedrun/OP` / `Nice Terrain`) and a `Customize` button.
   *Random* is vanilla: Seedscout does not search, compute or change anything.
2. Structure & biome positions for one seed, computed with the game's own world generation code,
   without generating chunks (verified against `/locate`).
3. Chunkbase-style map preview with structure icons and a "nearest structures" panel.
4. Rule editor (any registered structure/biome, also from data packs and mods) and multi-threaded search.
5. Terrain scoring, presets, performance and polish.

## Building

```bash
./gradlew build
```

The mod jar is `build/libs/seedscout-<version>.jar`.

## Testing

```bash
./gradlew runClient
```

Automated in-game tests (they are not part of the mod jar):

```bash
./gradlew runClientGameTest
```

On a headless Linux machine (software OpenGL):

```bash
LIBGL_ALWAYS_SOFTWARE=1 SDL_VIDEO_FORCE_EGL=1 xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest
```

## Config

`config/seedscout/settings.json` — last selected mode and the number of search threads (`0` = automatic).

## License

MIT.

---

## Türkçe

Minecraft **26.3** (Java Edition) için **sadece istemci tarafında** çalışan bir Fabric modu. **Dünya Oluştur**
ekranına bir seed bulucu ekler. Amaç OP/hile seed'i bulmak değil; survival'da güzel araziye ve iyi yapılara
yakın, üstüne güzel inşaatlar yapılabilecek dünyalar bulmak.

> **Durum:** geliştiriliyor. Aşama 1 (proje kurulumu + Dünya Oluştur ekranındaki butonlar) tamamlandı.

### Gereksinimler

| | |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.20.1 veya daha yeni |
| Fabric API | 0.165.0+26.3 |
| Java | 25 |

Sadece istemci — sunucuya kurmaya gerek yok.

### Özellikler (aşamalara göre plan)

1. **Dünya Oluştur ekranı** (Dünya sekmesi, seed kutusunun altı): `Seed Bulucu` butonu, mod seçimi
   (`Rastgele` / `Speedrun/OP` / `Güzel Arazi`) ve `Özelleştir` butonu.
   *Rastgele* vanilla'dır: Seedscout hiçbir arama yapmaz, hiçbir şeyi değiştirmez.
2. Tek bir seed için yapı ve biyom konumları; chunk oluşturmadan, oyunun kendi dünya üretim koduyla
   (`/locate` ile karşılaştırılarak doğrulanır).
3. Chunkbase tarzı harita önizleme, yapı ikonları ve "en yakın yapılar" paneli.
4. Kural düzenleyici (datapack ve modlardakiler dahil kayıtlı tüm yapı/biyomlar) ve çok thread'li arama.
5. Arazi puanlama, preset'ler, performans ve cilalama.

### Derleme

```bash
./gradlew build
```

Mod dosyası: `build/libs/seedscout-<sürüm>.jar`.

### Test

```bash
./gradlew runClient
```

Otomatik oyun içi testler (mod jar'ına dahil değildir):

```bash
./gradlew runClientGameTest
```

### Ayarlar

`config/seedscout/settings.json` — son seçilen mod ve arama thread sayısı (`0` = otomatik).

### Lisans

MIT.

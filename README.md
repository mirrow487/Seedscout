# Seedscout

> 🇹🇷 Türkçe açıklama aşağıda — [Türkçe](#türkçe)

A **client-side** Fabric mod for Minecraft **26.3** (Java Edition) that adds a seed finder to the
**Create World** screen. The goal is not "OP/cheat" seeds but survival worlds with nice terrain and
useful structures close to spawn — good places to build on.

> **Status:** in development. Stage 1 (Create World screen controls) and stage 2 (structure / biome / spawn
> calculation, verified against `/locate`) are done.

## Requirements

| | |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.20.1 or newer |
| Fabric API | 0.165.0+26.3 |
| Java | 25 |

Client only — the mod is never needed on a server.

## Features (planned per stage)

1. ✅ **Create World screen** (World tab, under the seed box): `Seed Finder` button, mode selector
   (`Random` / `Speedrun/OP` / `Nice Terrain`), `Specify` button and `Analyze` button.
   *Random* is vanilla: Seedscout does not search or change anything.
2. ✅ **Seed analysis** (`Analyze`): spawn point, the nearest structure of every type and the nearest surface
   biomes of the seed the new world will get. Computed with the game's own world generation code, without
   generating chunks. Click a row to copy the matching `/locate` command.
3. Chunkbase-style map preview with structure icons and a "nearest structures" panel.
4. Rule editor (any registered structure/biome, also from data packs and mods) and multi-threaded search.
5. Terrain scoring, presets, performance and polish.

## How it works (no chunks are generated)

Everything is read from the Create World screen: the selected world type's overworld generator
(normal, large biomes, amplified, …), the registries loaded with the enabled data packs (so data pack and
mod structures/biomes are included) and the "Generate Structures" switch.

For a seed, Seedscout builds the same `RandomState` and `ChunkGeneratorStructureState` as the server, then:

1. **Placement**: the structure set's own placement code gives the candidate chunks (spread regions,
   stronghold rings), including frequency and exclusion zones.
2. **Selection**: for sets with several structures (e.g. the 5 village types), the same weighted random as
   the game picks one.
3. **Validity**: `Structure.findValidGenerationPoint`, the check `/locate` uses (biome, terrain height from
   the noise, jigsaw start piece).
4. **Biomes**: the world's biome source with the climate sampler; **spawn**: `ChunkGenerator.getOrigin`,
   like `MinecraftServer.setInitialSpawn`.

An automated test creates real worlds (normal and large biomes) and compares spawn, 48 structure types,
2000 biome samples and `/locate biome` results with the integrated server: all identical.

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

> **Durum:** geliştiriliyor. Aşama 1 (Dünya Oluştur ekranı) ve Aşama 2 (`/locate` ile doğrulanmış yapı / biyom /
> spawn hesabı) tamamlandı.

### Gereksinimler

| | |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.20.1 veya daha yeni |
| Fabric API | 0.165.0+26.3 |
| Java | 25 |

Sadece istemci — sunucuya kurmaya gerek yok.

### Özellikler (aşamalara göre plan)

1. ✅ **Dünya Oluştur ekranı** (Dünya sekmesi, seed kutusunun altı): `Seed Bulucu` butonu, mod seçimi
   (`Rastgele` / `Speedrun/OP` / `Güzel Arazi`), `Belirle` butonu ve `Analiz` butonu.
   *Rastgele* vanilla'dır: Seedscout hiçbir arama yapmaz, hiçbir şeyi değiştirmez.
2. ✅ **Seed analizi** (`Analiz`): yeni dünyanın alacağı seed için spawn noktası, her yapı türünün en yakını ve
   yüzeydeki en yakın biyomlar. Chunk oluşturmadan, oyunun kendi dünya üretim koduyla hesaplanır. Bir satıra
   tıklayınca ilgili `/locate` komutu kopyalanır.
3. Chunkbase tarzı harita önizleme, yapı ikonları ve "en yakın yapılar" paneli.
4. Kural düzenleyici (datapack ve modlardakiler dahil kayıtlı tüm yapı/biyomlar) ve çok thread'li arama.
5. Arazi puanlama, preset'ler, performans ve cilalama.

### Nasıl çalışır (chunk oluşturulmaz)

Her şey Dünya Oluştur ekranından okunur: seçilen dünya tipinin overworld üreticisi (normal, büyük biyomlar,
amplified…), etkin datapack'lerle yüklenmiş registry'ler (datapack ve mod yapıları/biyomları da dahil) ve
"Yapıları Oluştur" anahtarı.

Bir seed için sunucuyla aynı `RandomState` ve `ChunkGeneratorStructureState` kurulur, sonra:

1. **Yerleşim**: yapı setinin kendi yerleşim kodu aday chunk'ları verir (dağılım bölgeleri, stronghold
   halkaları); sıklık ve dışlama bölgeleri dahil.
2. **Seçim**: birden fazla yapılı setlerde (ör. 5 köy tipi) oyundaki ağırlıklı rastgele seçimle aynısı yapılır.
3. **Geçerlilik**: `/locate`'in kullandığı `Structure.findValidGenerationPoint` (biyom, gürültüden arazi
   yüksekliği, jigsaw başlangıç parçası).
4. **Biyomlar**: dünyanın biyom kaynağı ve iklim örnekleyicisi; **spawn**: `MinecraftServer.setInitialSpawn`
   gibi `ChunkGenerator.getOrigin`.

Otomatik bir test gerçek dünyalar (normal ve büyük biyomlar) oluşturup spawn, 48 yapı türü, 2000 biyom örneği
ve `/locate biome` sonuçlarını entegre sunucuyla karşılaştırır: hepsi birebir aynı.

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

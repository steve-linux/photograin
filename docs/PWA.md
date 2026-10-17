# Photograin per iPhone/iPad (PWA) — piano

Stato: **pianificato**, da avviare quando la versione Android è stabile (1.0).
Questo documento fissa le decisioni e le regole da rispettare *già da ora* nel codice Android,
così il porting web non richiederà riscritture della logica.

## Obiettivo

Una Progressive Web App installabile dalla Home di iOS/iPadOS (e usabile in qualunque browser
moderno), stessa licenza GPL-3.0-or-later, ospitata come sito statico (es. GitHub Pages) con
link al sorgente. Niente App Store: i suoi termini sono storicamente incompatibili con la GPL.

## Requisiti minimi della piattaforma

| Funzione | Serve per | Disponibile su iOS |
|---|---|---|
| WebGL2 (GLSL ES 3.00) | pipeline GPU | Safari 15+ |
| `EXT_color_buffer_float` | target RGBA16F | Safari 15+ (con fallback RGBA8) |
| WasmGC | Kotlin/Wasm, Compose web | Safari 18.2+ |
| Wasm SIMD | decodifica RAW più veloce | Safari 16.4+ |
| Web Share API con file | "Salva in Foto" | Safari 15+ |
| OPFS / IndexedDB | ricette, cache | Safari 17+ |
| WebGPU (opzionale) | filtri pesanti futuri | Safari 26 |

Versione minima proposta: **iOS 18.2**.

## Architettura

```
shared/      Kotlin Multiplatform (commonMain): model/, math/, Shaders, ShaderParams
             target: android/jvm + wasmJs
app/         Android (com'è oggi), dipende da :shared
web/         Kotlin/Wasm + Compose Multiplatform: UI, renderer WebGL2, I/O del browser
native/      LibRaw, LittleCMS, libjxl: CMake comune, compilati per NDK e per Emscripten
```

### Cosa si condivide (verificato sul codice attuale)

Non importano nulla di Android e dipendono solo dalla stdlib Kotlin e da `kotlinx.serialization`
(che è multipiattaforma), quindi possono passare a `commonMain` senza modifiche:

- `model/` — regolazioni, curve, HSL, geometria/ritaglio, cronologia, ricetta JSON
- `math/` — trasformazioni affini
- `render/Shaders.kt` — già GLSL ES 3.00, identico a WebGL2
- `render/ShaderParams.kt` — calcolo degli uniform

I test di `CurvesTest`, `GeometryTest`, `ModelTest` vanno convertiti da JUnit a `kotlin.test`
(sostituzione quasi meccanica degli `assert*`).

### Cosa va riscritto per il web

| Android | Web |
|---|---|
| `render/GlUtil`, `Pipeline`, `PreviewRenderer`, `OffscreenGl` (GLES30) | stessa logica su `WebGL2RenderingContext`, dietro un'interfaccia `Gpu` comune |
| `render/Exporter` + `data/RegionSource` (export a riquadri) | stesso algoritmo a riquadri; `readPixels` → `OffscreenCanvas.convertToBlob` |
| `data/ImageLoader` (EXIF, decodifica) | `<input type=file accept=image/*>` + `createImageBitmap`; HEIC/JXL decodificati da Safari |
| `data/ExportWriter` (MediaStore) | `navigator.share({files})` → "Salva immagine"; download come ripiego |
| `data/RecipeStore`, `Preferences` | OPFS / IndexedDB |
| `ui/` Compose Android | Compose Multiplatform web; i componenti senza API Android si spostano in comune |

## Vincoli iOS da progettare fin dall'inizio

- **Memoria**: Safari termina la pagina sopra ~1–1,5 GB. Anteprima sempre su immagine ridotta;
  export sempre a riquadri, mai l'intera foto in un'unica texture float.
- **Dimensione texture/canvas**: leggere `MAX_TEXTURE_SIZE` (come fa già `GlUtil`) e non
  assumere più di 4096 px per lato sui dispositivi vecchi.
- **Niente accesso alle cartelle**: le foto si importano come copia; la ricetta si associa
  all'hash del file, non al percorso/URI.
- **Storage**: va chiesto `navigator.storage.persist()`; da browser (non installata) iOS può
  cancellare i dati dopo 7 giorni di inutilizzo.
- **RAW in Wasm**: più lento del nativo; decodifica in un Web Worker con barra di avanzamento.
  Il multithread Wasm richiede header COOP/COEP (impostabili su GitHub Pages solo con un
  service worker; meglio un hosting che permetta header personalizzati).

## Regole da rispettare già ora nel codice Android

Servono a non accumulare debito; non richiedono di spostare file oggi.

1. In `model/` e `math/` niente import `android.*`, `java.*` o `androidx.*`: solo stdlib Kotlin,
   `kotlin.math` e `kotlinx.*`.
2. Shader solo in GLSL ES 3.00 standard, senza estensioni `GL_OES_*`/`samplerExternalOES`;
   tenere sempre il fallback RGBA8 quando RGBA16F non è renderizzabile.
3. La ricetta JSON (`EditState`) è il formato di scambio tra le due app: ogni modifica deve
   restare retrocompatibile (campi nuovi con valore di default).
4. Le librerie native vanno integrate con CMake senza dipendenze dall'NDK nel codice C/C++,
   così lo stesso sorgente compila con Emscripten.
5. Nuove dipendenze Kotlin: preferire quelle multipiattaforma (es. `kotlinx-*`).

## Passi, quando si parte

1. Creare il modulo `:shared` (KMP) e spostarvi `model/`, `math/`, `Shaders`, `ShaderParams`
   mantenendo gli stessi package; test in `kotlin.test`. L'app Android non cambia comportamento.
2. Introdurre l'interfaccia `Gpu` in comune e adattare il renderer Android a usarla.
3. Aggiungere il target `wasmJs` e il modulo `:web` con il renderer WebGL2: anteprima di una foto
   con le regolazioni base.
4. Import, ricette su OPFS, export a riquadri con condivisione in Foto.
5. Manifest PWA, service worker (offline), icone, deploy automatico da GitHub Actions.
6. LibRaw/LittleCMS/libjxl via Emscripten, in un Web Worker.

Il target `wasmJs` va attivato solo nel modulo `:web` (o dietro una proprietà Gradle), così la
build F-Droid dell'APK non scarica Node/Binaryen e resta riproducibile.

## Licenze

Tutto resta GPL-3.0-or-later. Le librerie previste sono compatibili anche compilate in Wasm:
LibRaw (LGPL-2.1 / CDDL-1.0), LittleCMS (MIT), libjxl (BSD-3-Clause). Il sito deve mostrare il
link al sorgente corrispondente alla versione pubblicata.

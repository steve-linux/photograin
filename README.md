# Photograin

Editor fotografico non distruttivo per Android, libero (GPL-3.0-or-later) e pensato per F-Droid.
*A non-destructive photo editor for Android, free software (GPL-3.0-or-later), built for F-Droid.*

## Cosa fa (versione 0.1, Fase 1)

- **Anteprima in tempo reale su GPU** (OpenGL ES 3.0) di tutte le regolazioni.
- **Non distruttivo**: l'originale non viene mai modificato; ogni foto ha una "ricetta" JSON
  salvata nello spazio dell'app, con annulla/ripeti illimitati.
- **Luce**: esposizione, contrasto, luci, ombre, bianchi, neri.
- **Colore**: temperatura, tinta, vividezza, saturazione.
- **Dettaglio**: nitidezza, chiarezza, rimozione foschia.
- **Effetti**: vignettatura, grana pellicola.
- **Stili**: preset pronti (Vivace, Caldo, Freddo, Morbido, Pellicola, B/N…) e preset personali
  salvabili; importazione di LUT `.cube` (3D e 1D) applicati sulla GPU con intensità regolabile.
- **Modifica in serie**: scegli più foto e applica a tutte un preset o le regolazioni copiate;
  ogni foto mantiene il suo ritaglio, e puoi esportarle tutte in una cartella.
- **Ritaglio**: proporzioni predefinite, raddrizzamento ±45°, rotazione, specchio.
- **Confronto prima/dopo**: tieni premuto sulla foto, oppure vista divisa con cursore.
- **Copia/incolla** delle regolazioni tra foto.
- **Esportazione a piena risoluzione**, elaborata a tasselli per foto più grandi del limite della GPU;
  JPEG, PNG, WebP, AVIF, JPEG XL; i dati EXIF utili vengono mantenuti, il GPS rimosso per impostazione predefinita.
- **AVIF e JPEG XL**: esportazione in entrambi i formati (JPEG XL anche senza perdita, a qualità 100),
  con EXIF e profilo colore; i file `.jxl` si possono anche aprire e modificare.
- **Profili colore**: le foto con profilo ICC ad ampia gamma (Display P3, Adobe RGB…) e i RAW si
  modificano in Display P3 senza perdere i colori più saturi; si esporta in sRGB, Display P3 o
  Adobe RGB con il profilo ICC incorporato.
- **File RAW** (DNG, CR2, CR3, NEF, ARW, RAF, ORF, RW2 e altri) sviluppati con LibRaw:
  anteprima rapida a metà risoluzione, esportazione a piena risoluzione con demosaicizzazione AHD;
  i dati restano a 16 bit per canale fino alla GPU, così ombre e luci si recuperano senza scalini.
- **Nessun permesso** richiesto: si apre e si salva tramite le finestre di sistema.

## Architettura

```
app/src/main/java/io/github/steve_linux/photograin/
├── math/      trasformazioni affini (pure Kotlin)
├── model/     ricetta, geometria, ritaglio, cronologia (pure Kotlin, testati sulla JVM)
├── render/    pipeline GPU: geometria → sfocatura separabile → colore; anteprima ed export a tasselli
├── data/      decodifica (anche per regioni e RAW), salvataggio ricette, esportazione
└── ui/        Jetpack Compose: home, editor, pannelli strumenti, overlay di ritaglio
app/src/main/cpp/
├── LibRaw/          sottomodulo git, versione 0.22.2
├── libjxl/          sottomodulo git, JPEG XL 0.12.0
├── libavif/, aom/   sottomoduli git, AVIF 1.4.2 con l'encoder AV1 libaom 3.15.2
├── raw_decoder.*    C++ portabile, senza dipendenze Android
├── image_codec.*    C++ portabile: codifica JPEG XL/AVIF, decodifica JPEG XL
└── *_jni.cpp        collegamento JNI con l'app
```

La pipeline ha tre passaggi: **geometria** (orientamento EXIF, rotazione, raddrizzamento, ritaglio),
**sfocatura** su una copia ridotta (luminanza locale per luci/ombre, chiarezza e foschia) e **colore**
(tutte le regolazioni). I primi due dipendono solo dalla geometria, quindi muovere un cursore di colore
ricalcola soltanto l'ultimo passaggio.

Anteprima ed esportazione usano lo stesso codice e parametri relativi alla dimensione dell'immagine,
così il risultato esportato corrisponde a quello visto a schermo.

## Compilare

Serve JDK 17 o successivo, l'Android SDK (API 36) e l'NDK 27.0.12077973 con CMake 3.22.1
(Gradle li scarica da solo se mancano). LibRaw, libjxl, libavif e libaom sono sottomoduli git:

```
git submodule update --init    # dopo il clone, una volta
git -C app/src/main/cpp/libjxl submodule update --init third_party/highway third_party/brotli third_party/skcms
./gradlew assembleDebug        # APK di debug
./gradlew testDebugUnitTest    # test unitari
```

Ogni push su GitHub viene compilato da GitHub Actions (`.github/workflows/build.yml`), che pubblica
l'APK di debug come artefatto scaricabile.

## Licenza

Photograin è software libero: puoi ridistribuirlo e/o modificarlo secondo i termini della
GNU General Public License, versione 3 o (a tua scelta) qualsiasi versione successiva. Vedi [LICENSE](LICENSE).

Tutte le dipendenze (AndroidX, Jetpack Compose, Kotlin, kotlinx) sono Apache-2.0, compatibili con la GPL-3.0.
LibRaw è distribuita con doppia licenza LGPL-2.1 o CDDL-1.0; Photograin la usa secondo la LGPL-2.1,
compatibile con la GPL-3.0.
libjxl (con highway, brotli e skcms), libavif e libaom sono BSD, compatibili con la GPL-3.0.
Nessuna dipendenza da Google Play Services o servizi proprietari.

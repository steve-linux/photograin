# Modelli inclusi

## Real-ESRGAN (ingrandimento con IA)

`app/src/main/assets/models/realesr_general_x4v3.ncnn.{param,bin}` è il modello
**realesr-general-x4v3** di [Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN)
(Xintao Wang et al.), architettura SRVGGNetCompact (32 convoluzioni da 64 canali, PReLU,
ingrandimento 4×), convertito per [ncnn](https://github.com/Tencent/ncnn) con pesi in fp16.

- **Licenza**: BSD-3-Clause, come il codice di Real-ESRGAN con cui i pesi sono pubblicati.
- **Pesi originali**: <https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-general-x4v3.pth>
  SHA-256 `8dc7edb9ac80ccdc30c3a5dca6616509367f05fbc184ad95b731f05bece96292`
- **Conversione**: [`tools/realesrgan/convert.py`](../tools/realesrgan/convert.py) con
  torch 2.14.1 e pnnx 20260526 (`pip install torch==2.14.1 pnnx==20260526`).
  L'architettura è copiata da Real-ESRGAN in `tools/realesrgan/srvgg_arch.py`.
- **File prodotti**:
  - `realesr_general_x4v3.ncnn.param` SHA-256 `b41dac5ec6a213cc6983792f5e66b066e906aead0e6eaed9bc4644b281186921`
  - `realesr_general_x4v3.ncnn.bin` SHA-256 `8fdd6052f8931091164a570ee7b5c5b2d1dcd991112d464eb9ae1a5b52ece553`

Per rigenerarli:

```sh
cd tools/realesrgan
curl -LO https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-general-x4v3.pth
python convert.py
cp realesr_general_x4v3.ncnn.param realesr_general_x4v3.ncnn.bin ../../app/src/main/assets/models/
```

Verificato rispetto a PyTorch: su un'immagine di prova l'uscita di ncnn differisce di al più
2 livelli su 255 (media 0,5).

## U²-Net-p (scontorno con IA)

`app/src/main/assets/models/u2netp.ncnn.{param,bin}` è il modello **u2netp**, la versione
leggera (1,1 milioni di parametri) di [U²-Net](https://github.com/xuebinqin/U-2-Net)
(Xuebin Qin et al., «U²-Net: Going Deeper with Nested U-Structure for Salient Object
Detection», 2020), convertito per ncnn. Trova il soggetto principale di una foto vista a
320×320 pixel; i pesi restano in fp32, perché in fp16 la maschera si sposta fino a 44 livelli su
255 lungo alcuni bordi.

- **Licenza**: Apache-2.0, come il codice di U²-Net con cui i pesi sono pubblicati.
- **Pesi originali**: `u2netp.pth` di U²-Net, nella versione ONNX pubblicata da
  [rembg](https://github.com/danielgatis/rembg) (MIT):
  <https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx>
  SHA-256 `309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8`
- **Conversione**: [`tools/u2netp/convert.sh`](../tools/u2netp/convert.sh) con pnnx 20260526
  (`pip install pnnx==20260526`; per l'ONNX basta l'eseguibile, non serve torch).
- **File prodotti**:
  - `u2netp.ncnn.param` SHA-256 `faf6e6aa63b8934e7077f4100deeac5f590f64931da03b0000133e5895d1dc42`
  - `u2netp.ncnn.bin` SHA-256 `7e18d54080bc4560dff8f9b39f4a5b0392f6a104cf4d59b4c5d86bd24402c908`

Per rigenerarli:

```sh
cd tools/u2netp
curl -LO https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx
sh convert.sh
cp u2netp.ncnn.param u2netp.ncnn.bin ../../app/src/main/assets/models/
```

Verificato rispetto a ONNX Runtime sulla stessa immagine 320×320: l'uscita di ncnn differisce di
al più 0,5 livelli su 255. La conversione è riproducibile: due esecuzioni danno file identici.

Per F-Droid, `u2netp.ncnn.bin` va aggiunto a `scanignore` nella ricetta di build, come il modello
Real-ESRGAN.

## MI-GAN (rimozione oggetti con IA)

`app/src/main/assets/models/migan.ncnn.{param,bin}` è il modello **MI-GAN** di Picsart AI
Research (Andranik Sargsyan et al., «MI-GAN: A Simple Baseline for Image Inpainting on Mobile
Devices», ICCV 2023), addestrato su Places2 a 512×512, convertito per ncnn con pesi in fp16.
Riempie con lo sfondo circostante la zona dipinta col pennello «Rimuovi»; l'app gli passa un
quadrato attorno alle pennellate, ridotto o ingrandito a 512 pixel, e incolla solo la parte
dipinta.

- **Licenza**: MIT, come il codice e i pesi di [MI-GAN](https://github.com/Picsart-AI-Research/MI-GAN).
- **Pesi originali**: l'esportazione TorchScript dei pesi Places2 a 512 pubblicata da
  [IOPaint](https://github.com/Sanster/IOPaint) (Apache-2.0):
  <https://github.com/Sanster/models/releases/download/migan/migan_traced.pt>
  SHA-256 `038b82d90416f1d1af232486d76d07665aecdd988e0f16090b1e11a61820b800`.
  Ingresso: 4 canali 512×512, cioè `0.5 - maschera` (1 nei buchi) e l'immagine RGB in -1..1 con
  i buchi azzerati; uscita: l'immagine RGB in -1..1.
- **Conversione**: [`tools/migan/convert.sh`](../tools/migan/convert.sh) con pnnx 20260526 e
  torch 2.9.0 (`pip install torch==2.9.0 pnnx==20260526`).
- **File prodotti**:
  - `migan.ncnn.param` SHA-256 `8b61bfd2457b5621bcef343333240a0b146fe5ccc2fdb6b8df5e4a748ad4a692`
  - `migan.ncnn.bin` SHA-256 `e63d3d1d6215b4c41994857dfc4f81bc5e6c7f0d4985230ba5a40adbf51be807`

Per rigenerarli:

```sh
cd tools/migan
curl -LO https://github.com/Sanster/models/releases/download/migan/migan_traced.pt
sh convert.sh
cp migan.ncnn.param migan.ncnn.bin ../../app/src/main/assets/models/
```

Verificato rispetto a PyTorch sulla stessa immagine con un buco di 150×140 pixel: con i pesi in
fp32 l'uscita di ncnn differisce di al più 0,002 livelli su 255; con i pesi in fp16, quelli
inclusi, di al più 11 livelli nella zona riempita (media 0,4), una differenza che non si vede in
un'area inventata dal modello e che dimezza il file (15 MB invece di 27). La conversione è
riproducibile: due esecuzioni danno file identici.

Per F-Droid, anche `migan.ncnn.bin` va aggiunto a `scanignore`.

## MobileSAM (selezione di un oggetto con un tocco)

`app/src/main/assets/models/mobilesam_encoder.ncnn.{param,bin}`,
`mobilesam_decoder.ncnn.{param,bin}` e `mobilesam_prompt.bin` sono il modello **MobileSAM**
(Chaoning Zhang et al., «Faster Segment Anything: Towards Lightweight SAM for Mobile
Applications», 2023), una versione leggera di Segment Anything di Meta con un encoder TinyViT,
convertito per ncnn con pesi in fp16. Su una maschera «Soggetto», un tocco sulla foto seleziona
l'oggetto sotto il dito al posto del soggetto principale; con «Cancella» un tocco esclude una
parte. L'encoder legge la foto una volta (1024 pixel sul lato lungo, dritta secondo l'EXIF),
qualche secondo; ogni tocco dopo passa solo dal decoder, una frazione di secondo.

- **Licenza**: Apache-2.0, come il codice e i pesi di
  [MobileSAM](https://github.com/ChaoningZhang/MobileSAM) e di
  [Segment Anything](https://github.com/facebookresearch/segment-anything) da cui deriva.
- **Pesi originali**: `weights/mobile_sam.pt` del repository MobileSAM, commit
  `f706ad9c4eb7f219c00d9050e46328518ffb65d2`,
  SHA-256 `6dbb90523a35330fedd7f1d3dfc66f995213d81b29a5ca8108dbcdd4e37d6c2f`.
- **Divisione**: [`tools/mobilesam/export.py`](../tools/mobilesam/export.py) esporta
  - l'encoder: immagine RGB normalizzata (media 123,675 / 116,28 / 103,53, deviazione 58,395 /
    57,12 / 57,375) in un quadrato 1024×1024, in alto a sinistra; uscita 256×64×64;
  - il decoder senza maschera di ingresso: l'immagine codificata, un token da 256 valori per
    tocco più uno «nessun punto», e la codifica di posizione delle 64×64 celle; uscita quattro
    maschere 256×256 con i loro punteggi. Con un tocco l'app prende la migliore delle tre
    alternative, con più tocchi la maschera unica;
  - `mobilesam_prompt.bin`: le 2×128 frequenze casuali della codifica di posizione, poi i token di
    un punto escluso, uno incluso, due angoli di riquadro e «nessun punto» (5×256), in float32
    little-endian. La codifica dei tocchi la calcola l'app (`app/src/main/cpp/selector.cpp`),
    così il decoder accetta un numero qualsiasi di tocchi.

  Lo script riscrive due funzioni di TinyViT senza cambiarne il risultato: la divisione in
  finestre usa al più 5 dimensioni (ncnn ne ha 4 oltre al batch) e l'attenzione tiene le
  finestre dietro un batch di 1, altrimenti pnnx ne appiattisce i livelli lineari.
- **Conversione**: [`tools/mobilesam/convert.sh`](../tools/mobilesam/convert.sh) con pnnx
  20260526, torch 2.9.0, torchvision 0.24.0 e timm 1.0.30.
- **File prodotti**:
  - `mobilesam_encoder.ncnn.param` SHA-256 `86de20e97f8090b4941bd64c2e8cad4a38842a5708822e3302dc9da1908fb007`
  - `mobilesam_encoder.ncnn.bin` SHA-256 `8615f54f7f3e4a2ee8a184495b0ba5089698df6d47ac8942622154455f88e4b1`
  - `mobilesam_decoder.ncnn.param` SHA-256 `bf0b0531d55d8a40e2768875a1a7b05674b96cfd2f11fc82cd876acbf664016f`
  - `mobilesam_decoder.ncnn.bin` SHA-256 `d6d93019aa3dd4259fa7d0644b4e0f3a2e127c3804cc36d7acd8df54d46d2486`
  - `mobilesam_prompt.bin` SHA-256 `713f6f9d53996e52b2e71a87edef77c0043279b20498c106e563e067e071e2d4`

Per rigenerarli:

```sh
cd tools/mobilesam
git clone https://github.com/ChaoningZhang/MobileSAM
git -C MobileSAM checkout f706ad9c4eb7f219c00d9050e46328518ffb65d2
cp MobileSAM/weights/mobile_sam.pt .
sh convert.sh
cp mobilesam_encoder.ncnn.* mobilesam_decoder.ncnn.* mobilesam_prompt.bin ../../app/src/main/assets/models/
```

Verificato rispetto a `SamPredictor` di PyTorch su una foto, con un tocco, con tre tocchi diversi
e con un tocco incluso più uno escluso, eseguendo `selector.cpp` con ncnn compilato con gli
stessi livelli dell'app: le maschere coincidono su più del 99,7% dei pixel (le differenze sono sul
bordo) e i valori differiscono di al più 0,09; i punteggi di al più 0,006. Sul PC (4 thread)
l'encoder richiede circa 2 secondi e il decoder 0,08. La conversione è riproducibile: due esecuzioni danno file identici.

Per F-Droid, anche `mobilesam_encoder.ncnn.bin`, `mobilesam_decoder.ncnn.bin` e
`mobilesam_prompt.bin` vanno aggiunti a `scanignore`.

# Pacchetti di modelli (da importare)

I modelli più grandi non sono nell'APK: si scaricano col browser dalla release
[`models-1`](https://github.com/steve-linux/photograin/releases/tag/models-1) e si importano da
Impostazioni → Modelli IA. L'app non ha il permesso INTERNET e non scarica nulla da sola.

Un pacchetto è uno zip, non compresso e con date fisse, che contiene i file ncnn del modello.
L'app lo accetta solo se il suo SHA-256 è quello scritto in
[`ModelPack.kt`](../app/src/main/java/io/github/steve_linux/photograin/model/ModelPack.kt),
così esegue esattamente il file pubblicato qui; lo estrae nella memoria privata dell'app, da cui
si può rimuovere in qualsiasi momento.

## IS-Net (scontorno preciso)

`photograin-isnet.zip` contiene `isnet.ncnn.param` e `isnet.ncnn.bin`: il modello **IS-Net**
(Xuebin Qin et al., «Highly Accurate Dichotomous Image Segmentation», ECCV 2022), nella versione
«general use» addestrata per lo scontorno di oggetti e persone, convertito per ncnn con pesi in
fp16. Quando è installato, la maschera «Soggetto» e «Rimuovi sfondo» usano lui invece di
U²-Net-p: i bordi su capelli e pelo sono più fini, ma lavora su 1024×1024 pixel e richiede circa
320 miliardi di operazioni, quindi sul telefono da qualche secondo a mezzo minuto.

- **Licenza**: Apache-2.0, come il codice e i pesi di [DIS](https://github.com/xuebinqin/DIS).
- **Pesi originali**: l'esportazione ONNX di `isnet-general-use` pubblicata da
  [rembg](https://github.com/danielgatis/rembg) (MIT):
  <https://github.com/danielgatis/rembg/releases/download/v0.0.0/isnet-general-use.onnx>
  SHA-256 `60920e99c45464f2ba57bee2ad08c919a52bbf852739e96947fbb4358c0d964a`.
  Ingresso: RGB 1024×1024, `v / 255 - 0,5`; uscita: la maschera 0..1 (la prima delle sette).
- **Conversione**: [`tools/isnet/convert.sh`](../tools/isnet/convert.sh) con pnnx 20260526.
- **File prodotti**:
  - `isnet_general_use.ncnn.param` SHA-256 `57e6bbcd1add009257da9c0821c57a159a7ed9fc92f4a07bd593d7f918d2abaa`
  - `isnet_general_use.ncnn.bin` SHA-256 `f623708a9a0b58acb4d1edbf9387a7ef0359a8d441bde451494f99328dc7f7fb`
  - `photograin-isnet.zip` (88 MB) SHA-256 `bfbde8053f32c2fdcd5115a0d7818ee1b2d0c49f3ec935028e7453ce6b180e73`

Per rigenerarlo:

```sh
cd tools/isnet
curl -LO https://github.com/danielgatis/rembg/releases/download/v0.0.0/isnet-general-use.onnx
sh convert.sh
```

e caricare `photograin-isnet.zip` nella release `models-1`. Verificato rispetto a onnxruntime
sulla stessa immagine, eseguendo `segmenter.cpp` con ncnn compilato con gli stessi livelli
dell'app: la maschera differisce in media di 0,0008 (su 1) e coincide su più del 99,9% dei
pixel. Sul PC (4 thread) richiede circa 2,5 secondi. La conversione è riproducibile: due
esecuzioni danno lo stesso zip.

A 1024 pixel le attivazioni sono grandi: con le convoluzioni veloci di ncnn (Winograd e sgemm)
il picco di memoria arriva a 2,3 GB sul PC, e su un Galaxy A52s (5,5 GB) il sistema chiudeva
l'app. `segmenter.cpp` le disattiva per i modelli dei pacchetti e legge i pesi dal file senza
tenerne una copia intera: il picco scende a circa 1 GB. Conservare le attivazioni in fp16 lo
dimezzerebbe ancora, ma sui telefoni (non in qemu) rompeva la maschera in strisce orizzontali.
Se il telefono ha meno di 1,5 GB libero, l'app usa U²-Net-p per quella volta.

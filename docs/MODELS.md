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

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

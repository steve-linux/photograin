#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Converts IS-Net (isnet-general-use.onnx, as published by rembg) to the model pack that
# Photograin imports from Settings: photograin-isnet.zip, with isnet.ncnn.param and .bin.
#
# Usage, from this folder, with pnnx installed (see docs/MODELS.md):
#     curl -LO https://github.com/danielgatis/rembg/releases/download/v0.0.0/isnet-general-use.onnx
#     sh convert.sh
# The weights are stored in fp16, which halves them (88 MB); the mask moves by less than a
# thousandth on average, and the model computes in fp32. The zip is stored, not deflated,
# with fixed dates, so the same model always gives the same file and the same checksum.
set -e

WEIGHTS=isnet-general-use.onnx
WEIGHTS_SHA256=60920e99c45464f2ba57bee2ad08c919a52bbf852739e96947fbb4358c0d964a

echo "$WEIGHTS_SHA256  $WEIGHTS" | sha256sum -c -
# The 1024x1024 square the model was trained on.
pnnx "$WEIGHTS" inputshape=[1,3,1024,1024] fp16=1
python3 - <<'PY'
import zipfile

with zipfile.ZipFile("photograin-isnet.zip", "w", zipfile.ZIP_STORED) as z:
    for src, name in [("isnet_general_use.ncnn.param", "isnet.ncnn.param"), ("isnet_general_use.ncnn.bin", "isnet.ncnn.bin")]:
        info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
        info.external_attr = 0o644 << 16
        with open(src, "rb") as f:
            z.writestr(info, f.read())
PY
sha256sum photograin-isnet.zip

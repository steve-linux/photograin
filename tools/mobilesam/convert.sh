#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Converts MobileSAM (Apache-2.0) to the ncnn models in app/src/main/assets/models.
#
# Usage, from this folder, with pnnx, torch 2.9, torchvision 0.24 and timm installed (see
# docs/MODELS.md):
#     git clone https://github.com/ChaoningZhang/MobileSAM
#     git -C MobileSAM checkout f706ad9c4eb7f219c00d9050e46328518ffb65d2
#     cp MobileSAM/weights/mobile_sam.pt .
#     sh convert.sh
# It writes mobilesam_encoder.ncnn.*, mobilesam_decoder.ncnn.* and mobilesam_prompt.bin (plus
# pnnx's intermediate files). The weights are stored in fp16, which halves them; the masks
# match the original on more than 99% of their pixels, and the models compute in fp32.
set -e

WEIGHTS=mobile_sam.pt
WEIGHTS_SHA256=6dbb90523a35330fedd7f1d3dfc66f995213d81b29a5ca8108dbcdd4e37d6c2f

echo "$WEIGHTS_SHA256  $WEIGHTS" | sha256sum -c -
python3 export.py
# The encoder sees the photo padded to a 1024 square; the decoder takes the encoded image,
# the taps (two here, any number at run time) and the position encoding of the image cells.
pnnx mobilesam_encoder.pt inputshape=[1,3,1024,1024] fp16=1
pnnx mobilesam_decoder.pt inputshape=[1,256,64,64],[1,2,256],[1,256,64,64] \
    inputshape2=[1,256,64,64],[1,3,256],[1,256,64,64] fp16=1

#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Converts MI-GAN (migan_traced.pt, the TorchScript export of IOPaint) to the ncnn model in
# app/src/main/assets/models.
#
# Usage, from this folder, with pnnx installed (see docs/MODELS.md):
#     curl -LO https://github.com/Sanster/models/releases/download/migan/migan_traced.pt
#     sh convert.sh
# It writes migan.ncnn.param and .bin (plus pnnx's intermediate files). The weights are stored
# in fp16, which halves them; the filled area moves by less than half a level out of 255 on
# average, and the model computes in fp32 anyway.
set -e

WEIGHTS=migan_traced.pt
WEIGHTS_SHA256=038b82d90416f1d1af232486d76d07665aecdd988e0f16090b1e11a61820b800

echo "$WEIGHTS_SHA256  $WEIGHTS" | sha256sum -c -
# The input is the 512x512 square the model was trained on: the hole mask, then the image.
pnnx "$WEIGHTS" inputshape=[1,4,512,512] fp16=1
mv migan_traced.ncnn.param migan.ncnn.param
mv migan_traced.ncnn.bin migan.ncnn.bin

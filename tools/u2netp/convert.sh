#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Converts U^2-Net-p (u2netp.onnx) to the ncnn model in app/src/main/assets/models.
#
# Usage, from this folder, with pnnx installed (see docs/MODELS.md):
#     sh convert.sh
# It writes u2netp.ncnn.param and .bin (plus pnnx's intermediate files). The weights stay in
# fp32: in fp16 the mask moves by up to 44 levels out of 255 on some edges.
set -e

WEIGHTS=u2netp.onnx
WEIGHTS_SHA256=309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8

echo "$WEIGHTS_SHA256  $WEIGHTS" | sha256sum -c -
# pnnx reads ONNX directly; the input size is fixed because the model always sees 320x320.
pnnx "$WEIGHTS" inputshape=[1,3,320,320] fp16=0

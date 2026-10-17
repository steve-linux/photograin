# SPDX-License-Identifier: GPL-3.0-or-later
"""Converts realesr-general-x4v3.pth to the ncnn model in app/src/main/assets/models.

Usage, from this folder, with torch and pnnx installed (see docs/MODELS.md):
    python convert.py
It writes realesr_general_x4v3.ncnn.param and .bin (plus pnnx's intermediate files).
"""
import hashlib
import sys

import pnnx
import torch

sys.path.insert(0, ".")
from srvgg_arch import SRVGGNetCompact  # noqa: E402

WEIGHTS = "realesr-general-x4v3.pth"
WEIGHTS_SHA256 = "8dc7edb9ac80ccdc30c3a5dca6616509367f05fbc184ad95b731f05bece96292"

with open(WEIGHTS, "rb") as f:
    digest = hashlib.sha256(f.read()).hexdigest()
if digest != WEIGHTS_SHA256:
    sys.exit(f"{WEIGHTS}: unexpected SHA-256 {digest}")

net = SRVGGNetCompact(num_in_ch=3, num_out_ch=3, num_feat=64, num_conv=32, upscale=4, act_type="prelu")
state = torch.load(WEIGHTS, map_location="cpu", weights_only=True)
net.load_state_dict(state["params"] if "params" in state else state["params_ema"], strict=True)
net.eval()
# Two input sizes so the exported graph accepts any size.
pnnx.export(net, "realesr_general_x4v3.pt", (torch.rand(1, 3, 64, 64),), (torch.rand(1, 3, 96, 80),), fp16=True)

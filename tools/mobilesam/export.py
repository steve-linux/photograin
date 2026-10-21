# SPDX-License-Identifier: GPL-3.0-or-later
"""Traces MobileSAM for ncnn: the image encoder and the mask decoder, plus the small weights
that turn taps into the decoder's input (mobilesam_prompt.bin). See convert.sh.

The prompt encoder is left to the app (app/src/main/cpp/selector.cpp): it is a few sines and
cosines, and the decoder takes any number of taps that way. The decoder takes the position
encoding of the image cells as an input for the same reason, instead of 4 MB of constants.
"""
import sys

import numpy as np
import torch
import torch.nn.functional as F

sys.path.insert(0, "MobileSAM")
from mobile_sam import sam_model_registry  # noqa: E402
from mobile_sam.modeling import tiny_vit_sam as tv  # noqa: E402


def block_forward(self, x):
    """TinyViTBlock.forward with at most 5 dimensions: ncnn has 4 besides the batch."""
    H, W = self.input_resolution
    B, L, C = x.shape
    res_x = x
    ws = self.window_size
    if H == ws and W == ws:
        x = self.attn(x)
    else:
        x = x.view(B, H, W, C)
        pad_b = (ws - H % ws) % ws
        pad_r = (ws - W % ws) % ws
        if pad_b or pad_r:
            x = F.pad(x, (0, 0, 0, pad_r, 0, pad_b))
        pH, pW = H + pad_b, W + pad_r
        nH, nW = pH // ws, pW // ws
        x = x.reshape(B, nH, ws, nW, ws * C).transpose(2, 3).reshape(B * nH * nW, ws * ws, C)
        x = self.attn(x)
        x = x.reshape(B, nH, nW, ws, ws * C).transpose(2, 3).reshape(B, pH, pW, C)
        if pad_b or pad_r:
            x = x[:, :H, :W].contiguous()
        x = x.reshape(B, L, C)
    x = res_x + x
    x = x.transpose(1, 2).reshape(B, C, H, W)
    x = self.local_conv(x)
    x = x.reshape(B, C, L).transpose(1, 2)
    return x + self.mlp(x)


def attention_forward(self, x):
    """Attention.forward with the windows behind a batch of 1: pnnx would otherwise turn its
    linear layers into ones that flatten all the windows together."""
    B, N, C = x.shape
    x = self.norm(x.reshape(1, B, N, C))
    qkv = self.qkv(x).reshape(1, B, N, self.num_heads, -1).permute(0, 1, 3, 2, 4)
    qkv = qkv.reshape(1, B * self.num_heads, N, -1)
    q, k, v = qkv.split([self.key_dim, self.key_dim, self.d], dim=3)
    attn = (q @ k.transpose(-2, -1)) * self.scale
    attn = attn.reshape(1, B, self.num_heads, N * N) + self.ab.reshape(self.num_heads, N * N)
    attn = attn.reshape(1, B * self.num_heads, N, N).softmax(dim=-1)
    x = (attn @ v).reshape(1, B, self.num_heads, N, self.d).permute(0, 1, 3, 2, 4).reshape(1, B, N, self.dh)
    return self.proj(x).reshape(B, N, -1)


tv.TinyViTBlock.forward = block_forward
tv.Attention.forward = attention_forward


class Decoder(torch.nn.Module):
    """The mask decoder without a mask prompt: image embedding, tap tokens and position
    encoding in, the four masks (256 x 256) and their scores out."""

    def __init__(self, sam):
        super().__init__()
        self.d = sam.mask_decoder
        self.register_buffer("dense", sam.prompt_encoder.no_mask_embed.weight.reshape(1, -1, 1, 1).clone())

    def forward(self, emb, sparse, pe):
        d = self.d
        tokens = torch.cat([d.iou_token.weight, d.mask_tokens.weight], dim=0).unsqueeze(0)
        tokens = torch.cat((tokens, sparse), dim=1)
        hs, src = d.transformer(emb + self.dense, pe, tokens)
        mask_tokens = hs[:, 1:1 + d.num_mask_tokens, :]
        src = src.transpose(1, 2).reshape(1, 256, 64, 64)
        up = d.output_upscaling(src)
        hyper = torch.stack([d.output_hypernetworks_mlps[i](mask_tokens[:, i, :]) for i in range(d.num_mask_tokens)], dim=1)
        masks = (hyper @ up.reshape(1, 32, 256 * 256)).reshape(1, -1, 256, 256)
        return masks, d.iou_prediction_head(hs[:, 0, :])


sam = sam_model_registry["vit_t"](checkpoint="mobile_sam.pt").eval()
with torch.no_grad():
    image = torch.randn(1, 3, 1024, 1024)
    torch.jit.trace(sam.image_encoder, image).save("mobilesam_encoder.pt")
    pe = sam.prompt_encoder
    torch.jit.trace(Decoder(sam), (sam.image_encoder(image), torch.randn(1, 2, 256), pe.get_dense_pe())).save("mobilesam_decoder.pt")
    # The position encoding's frequencies (2 x 128), then the embeddings of a left-out tap, an
    # included one, two box corners and no tap (5 x 256), as little-endian floats.
    weights = [pe.pe_layer.positional_encoding_gaussian_matrix.reshape(-1)]
    weights += [pe.point_embeddings[i].weight[0] for i in range(4)] + [pe.not_a_point_embed.weight[0]]
    np.concatenate([w.numpy() for w in weights]).astype("<f4").tofile("mobilesam_prompt.bin")

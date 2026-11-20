#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Reproduce the bundled sky model from pinned, MIT-licensed upstream files."""
import hashlib
from pathlib import Path
from urllib.request import urlopen

REVISION = '1f7811b32b64ddc957269defff84bc87a3f0b74f'
BASE = f'https://raw.githubusercontent.com/xiongzhu666/Sky-Segmentation-and-Post-processing/{REVISION}/'
FILES = {
    'skysegsmall_sim-opt-fp16.bin': 'ff1e18f71dc0a0cf8a56b634c59f68d296deffd015cbed27a6d5e302f991faf2',
    'skysegsmall_sim-opt-fp16.param': '7af3523130445db42d94503558c08ce991932f843b92e4e921c99d732768895e',
}
OUTPUT = Path(__file__).resolve().parents[2] / 'app/src/main/assets/models'

def main():
    for name, expected in FILES.items():
        with urlopen(BASE + name, timeout=60) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError(f'Checksum mismatch for {name}')
        if name.endswith('.param'):
            # The common runner uses in0/out0. No layers or weights are changed.
            data = ('\n'.join(' '.join({'input.1': 'in0', '1959': 'out0'}.get(t, t)
                                      for t in line.split())
                              for line in data.decode().splitlines()) + '\n').encode()
        suffix = '.param' if name.endswith('.param') else '.bin'
        path = OUTPUT / ('skyseg.ncnn' + suffix)
        path.write_bytes(data)
        print(hashlib.sha256(data).hexdigest(), path.name)

if __name__ == '__main__':
    main()

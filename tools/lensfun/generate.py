#!/usr/bin/env python3
"""Read Lensfun v0.3.4 data/db and emit the rectilinear profiles used by Photograin."""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

cameras = []
lenses = []
for path in sorted(Path(sys.argv[1]).glob("*.xml")):
    root = ET.parse(path).getroot()
    for item in root.findall("camera"):
        cameras.append(dict(make=item.findtext("maker"), model=item.findtext("model"),
                            mount=item.findtext("mount"), crop=float(item.findtext("cropfactor"))))
    for item in root.findall("lens"):
        if item.findtext("type", "rectilinear") != "rectilinear" or item.find("center") is not None:
            continue
        rows = []
        for calib in item.findall("calibration/*"):
            if calib.tag == "distortion":
                # Coefficients of the radial multiplier, from constant through r^4.
                k1, k2, a, b, c = (float(calib.get(k, "0")) for k in ("k1", "k2", "a", "b", "c"))
                models = dict(poly3=[1-k1, 0, k1, 0, 0], poly5=[1, 0, k1, 0, k2],
                              ptlens=[1-a-b-c, c, b, a, 0])
            elif calib.tag == "tca":
                vr, vb = [float(calib.get(k, "1")) for k in ("vr", "vb")]
                cr, cb, br, bb = [float(calib.get(k, "0")) for k in ("cr", "cb", "br", "bb")]
                models = dict(linear=[vr, 0, 0, vb, 0, 0], poly3=[vr, cr, br, vb, cb, bb])
            elif calib.tag == "vignetting":
                models = dict(pa=[float(calib.get(k, "0")) for k in ("k1", "k2", "k3")])
            else:
                continue
            coeff = models.get(calib.get("model"))
            if coeff is not None:
                rows.append(dict(kind=calib.tag, focal=float(calib.get("focal")),
                                 aperture=float(calib.get("aperture", "0")),
                                 distance=float(calib.get("distance", "0")),
                                 coeff=[round(v, 9) for v in coeff]))
        if rows:
            ratio = item.findtext("aspect-ratio", "3:2")
            parts = list(map(float, ratio.split(":")))
            lenses.append(dict(names=[n.text for n in item.findall("model") if n.text],
                               mounts=[m.text for m in item.findall("mount") if m.text],
                               crop=float(item.findtext("cropfactor")),
                               aspect=parts[0] / parts[1] if len(parts) == 2 else parts[0],
                               rows=rows))
def encode(row):
    return json.dumps(row, separators=(",", ":"), ensure_ascii=False, allow_nan=False)


print('{"cameras":[')
print(",\n".join(map(encode, cameras)))
print('],"lenses":[')
print(",\n".join(map(encode, lenses)))
print("]}")

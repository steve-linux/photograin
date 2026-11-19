#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Sample Lensfun corrections for explicit camera/lens pairs into fit.py's input."""
import argparse
import json
import math
from pathlib import Path


def exact_match(matches, maker, model):
    def normalize(value):
        return value.strip().casefold()

    found = [item for item in matches
             if normalize(item.maker) == normalize(maker)
             and normalize(item.model) == normalize(model)]
    if len(found) != 1:
        raise ValueError(f"Expected one exact Lensfun match for {maker} / {model}, found {len(found)}")
    return found[0]


def sample_profile(engine, database, entry):
    camera_make, camera_model = entry["lensfunCamera"]
    lens_make, lens_model = entry["lensfunLens"]
    camera = exact_match(database.find_cameras(camera_make, camera_model), camera_make, camera_model)
    lens = exact_match(database.find_lenses(camera, lens_make, lens_model), lens_make, lens_model)
    width, height = entry["width"], entry["height"]
    if not all(type(value) is int and 2 <= value <= 100_000 for value in (width, height)):
        raise ValueError("Invalid image dimensions")
    focal = entry["focalMm"]
    if not 0.01 <= focal <= 10000:
        raise ValueError("Invalid focal length")
    modifier = engine.Modifier(lens, camera.crop_factor, width, height)
    modifier.initialize(focal, aperture=8.0, scale=0.0,
                        flags=engine.ModifyFlags.DISTORTION | engine.ModifyFlags.SCALE)

    samples = []
    for y in (0.03, 0.18, 0.34, 0.5, 0.66, 0.82, 0.97):
        for x in (0.03, 0.18, 0.34, 0.5, 0.66, 0.82, 0.97):
            # Lensfun uses integer pixel centers. Photograin normalizes pixel edges.
            coordinates = modifier.apply_geometry_distortion(x * width - 0.5, y * height - 0.5, 1, 1)
            if coordinates is None:
                raise ValueError(f"Missing Lensfun distortion calibration for {lens_model} at {focal} mm")
            sx, sy = map(float, coordinates[0][0])
            sx, sy = (sx + 0.5) / width, (sy + 0.5) / height
            if not all(math.isfinite(v) and 0 <= v <= 1 for v in (sx, sy)):
                raise ValueError(f"Lensfun correction samples outside the source for {lens_model}")
            samples.append([x, y, sx, sy])

    return {key: value for key, value in entry.items() if key not in ("lensfunCamera", "lensfunLens")} | {
        "samples": samples,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mapping", type=Path)
    parser.add_argument("measurements", type=Path)
    parser.add_argument("--database", action="append", type=Path, default=[],
                        help="Lensfun XML file. Repeat to supply all required files.")
    args = parser.parse_args()
    try:
        import lensfunpy

        if args.database:
            database = lensfunpy.Database(paths=[str(path) for path in args.database],
                                          load_common=False, load_bundled=False)
        else:
            database = lensfunpy.Database()
        entries = json.loads(args.mapping.read_text(encoding="utf-8"))["profiles"]
        if not 1 <= len(entries) <= 2000:
            raise ValueError("Provide 1–2000 profiles")
        profiles = [sample_profile(lensfunpy, database, entry) for entry in entries]
        args.measurements.write_text(json.dumps({"profiles": profiles}, indent=2, ensure_ascii=False) + "\n",
                                     encoding="utf-8")
    except (ImportError, OSError, KeyError, TypeError, ValueError, RuntimeError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()

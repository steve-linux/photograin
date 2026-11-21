#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Fit Photograin profiles from measured output-to-source coordinate samples."""
import argparse
import json
import math
from pathlib import Path


def fit(entry, tolerance):
    width, height = entry["width"], entry["height"]
    if min(width, height) < 2:
        raise ValueError("Invalid image dimensions")
    ax, ay = width / max(width, height), height / max(width, height)
    corner_radius = (ax * ax + ay * ay) / 4
    samples = entry["samples"]
    if len(samples) < 12:
        raise ValueError("Provide at least 12 samples")
    observations = []
    for sample in samples:
        x, y, sx, sy = sample
        if not all(math.isfinite(v) and 0 <= v <= 1 for v in sample):
            raise ValueError("Sample coordinates must be normalized and inside the image")
        dx, dy = x - 0.5, y - 0.5
        radius = (dx * ax) ** 2 + (dy * ay) ** 2
        observations.append((dx, dy, radius, sx, sy))
    radii = {round(row[2] / corner_radius, 3) for row in observations}
    if len(radii) < 4 or min(radii) > 0.1 or max(radii) < 0.65:
        raise ValueError("Include multiple radii from near the center to the corners")

    def error(amount):
        k = 0.4 * amount
        zoom = 1 + max(0, k) * corner_radius
        errors = []
        for dx, dy, radius, sx, sy in observations:
            scale = (1 + k * radius) / zoom
            errors.append(math.hypot((0.5 + dx * scale - sx) * ax, (0.5 + dy * scale - sy) * ay))
        return sum(v * v for v in errors), max(errors)

    amount = min(range(-1000, 1001), key=lambda n: (error(n / 1000)[0], abs(n))) / 1000
    if error(amount)[1] > tolerance:
        raise ValueError(f"Profile exceeds maximum point error ({error(amount)[1]:.4%})")
    keys = ("cameraMake", "cameraModel", "lensModel", "focalMm", "focal35Mm")
    result = {key: entry[key] for key in keys if key in entry}
    if not str(result["cameraModel"]).strip() or not 0.01 <= result["focalMm"] <= 10000:
        raise ValueError("Invalid camera model or focal length")
    return {**result, "distortion": amount}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("measurements", type=Path)
    parser.add_argument("catalog", type=Path)
    parser.add_argument("--max-error", type=float, default=0.002,
                        help="Maximum point error as a fraction of the long edge")
    args = parser.parse_args()
    if not 0 < args.max_error <= 0.01:
        parser.error("--max-error must be greater than zero and at most 0.01")
    try:
        entries = json.loads(args.measurements.read_text(encoding="utf-8"))["profiles"]
        if not 1 <= len(entries) <= 2000:
            raise ValueError("Provide 1–2000 profiles")
        profiles = [fit(entry, args.max_error) for entry in entries]
        identifiers = {
            (str(p.get("cameraMake", "")).strip().lower(), str(p["cameraModel"]).strip().lower(),
             str(p.get("lensModel", "")).strip().lower(), f"{p['focalMm']:.2f}", p.get("focal35Mm", 0))
            for p in profiles
        }
        if len(identifiers) != len(profiles):
            raise ValueError("Duplicate profile")
        catalog = json.dumps({"version": 1, "profiles": profiles}, ensure_ascii=False, indent=2) + "\n"
        if len(catalog.encode("utf-8")) > 1_048_576:
            raise ValueError("Catalog exceeds the Android import limit")
        args.catalog.write_text(catalog, encoding="utf-8")
    except (OSError, ValueError, KeyError, TypeError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()

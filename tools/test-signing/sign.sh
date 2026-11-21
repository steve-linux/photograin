#!/usr/bin/env bash
set -euo pipefail
umask 077

if [[ $# != 2 || "$1" == "$2" || -e "$2" ]]; then
    printf 'Usage: %s input.apk new-output.apk\n' "$0" >&2
    exit 2
fi

key_dir="${XDG_DATA_HOME:-$HOME/.local/share}/photograin/test-signing"
if [[ -n ${PHOTOGRAIN_TEST_SIGNING:-} ]]; then
    key_dir=$(mktemp -d)
    trap 'rm -rf -- "$key_dir"' EXIT
    python3 - "$key_dir" <<'PY'
import base64
import json
import os
from pathlib import Path
import re
import sys

directory = Path(sys.argv[1])
try:
    credentials = json.loads(os.environ["PHOTOGRAIN_TEST_SIGNING"])
    password = credentials["password"]
    if not re.fullmatch("[0-9a-f]{64}", password):
        raise ValueError("Invalid password format")
    (directory / "photograin.p12").write_bytes(base64.b64decode(credentials["keystore"], validate=True))
    (directory / "password").write_text(password + "\n")
except (ValueError, KeyError, TypeError):
    sys.exit("Malformed Photograin test signing secret")
PY
    unset PHOTOGRAIN_TEST_SIGNING
fi

# Refuse production APKs even when this script is run by hand.
if ! "${AAPT:-aapt}" dump badging "$1" | sed -n '1p' | grep -q "^package: name='io.github.steve_linux.photograin.debug' "; then
    printf 'Expected the Photograin debug package.\n' >&2
    exit 1
fi

"${APKSIGNER:-apksigner}" sign --ks "$key_dir/photograin.p12" --ks-type PKCS12 \
    --ks-key-alias photograin-test --ks-pass "file:$key_dir/password" \
    --out "$2" "$1"
"${APKSIGNER:-apksigner}" verify --print-certs "$2"

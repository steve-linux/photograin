#!/usr/bin/env bash
set -euo pipefail
umask 077

repo=steve-linux/photograin
secret=PHOTOGRAIN_TEST_SIGNING
key_dir="${XDG_DATA_HOME:-$HOME/.local/share}/photograin/test-signing"

if [[ $# -gt 1 || (${1:-} != "" && ${1:-} != "--local") ]]; then
    printf 'Usage: %s [--local]\n' "$0" >&2
    exit 2
fi

if [[ ${1:-} != "--local" ]]; then
    secrets=$(gh secret list --repo "$repo" --json name --jq '.[].name')
    if grep -Fxq "$secret" <<< "$secrets"; then
        printf '%s is already configured in %s.\n' "$secret" "$repo"
        exit 0
    fi
fi

if [[ ! -e "$key_dir" ]]; then
    mkdir -p "${key_dir%/*}"
    staging=$(mktemp -d "${key_dir%/*}/.test-signing.XXXXXX")
    trap 'rm -rf -- "$staging"' EXIT
    openssl rand -hex 32 > "$staging/password"
    keytool -genkeypair -keystore "$staging/photograin.p12" -storetype PKCS12 \
        -storepass:file "$staging/password" -alias photograin-test -keyalg RSA -keysize 3072 \
        -validity 10000 -dname 'CN=Photograin Test' -noprompt
    # A concurrent setup must never replace an existing signing identity.
    if ! mv -T -n -- "$staging" "$key_dir" || [[ -d "$staging" ]]; then
        printf 'Another setup created the signing files. Run setup again.\n' >&2
        exit 1
    fi
fi

keytool -list -keystore "$key_dir/photograin.p12" -storepass:file "$key_dir/password" \
    -alias photograin-test >/dev/null
printf 'Local signing files: %s\n' "$key_dir"

if [[ ${1:-} != "--local" ]]; then
    python3 - "$key_dir" <<'PY' | gh secret set "$secret" --repo "$repo"
import base64
import json
from pathlib import Path
import sys

directory = Path(sys.argv[1])
print(json.dumps({
    "keystore": base64.b64encode((directory / "photograin.p12").read_bytes()).decode("ascii"),
    "password": (directory / "password").read_text().strip(),
}))
PY
fi

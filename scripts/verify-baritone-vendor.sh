#!/usr/bin/env bash

set -euo pipefail

if (( $# > 1 )); then
    printf 'Usage: %s [liquidbounce-release.jar]\n' "$0" >&2
    exit 64
fi

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
repository_root=$(cd -- "$script_dir/.." && pwd -P)
vendor_directory="$repository_root/third_party/baritone"
release_jar=${1:-}
enforce_license=${BARITONE_ENFORCE_LICENSE:-0}

for command_name in python3 sha256sum; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        printf '[baritone-vendor] missing required command: %s\n' "$command_name" >&2
        exit 69
    fi
done

if [[ ! -d "$vendor_directory" ]]; then
    printf '[baritone-vendor] missing vendor directory: %s\n' "$vendor_directory" >&2
    exit 66
fi

if [[ "$enforce_license" != 0 && "$enforce_license" != 1 ]]; then
    printf '[baritone-vendor] BARITONE_ENFORCE_LICENSE must be 0 or 1\n' >&2
    exit 64
fi

if [[ "$enforce_license" == 1 && ! -s "$vendor_directory/NETHER_PATHFINDER_LICENSE.txt" ]]; then
    printf '%s\n' \
        '[baritone-vendor] public release blocked: nether-pathfinder 1.6 has no verified license grant' \
        '[baritone-vendor] add reviewed third_party/baritone/NETHER_PATHFINDER_LICENSE.txt only after clarification' >&2
    exit 77
fi

(
    cd -- "$vendor_directory"
    sha256sum --check --strict SHA256SUMS
)

python3 -B "$script_dir/baritone_vendor/verify.py" "$vendor_directory" "$release_jar"

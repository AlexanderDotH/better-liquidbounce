from __future__ import annotations

import pathlib
import sys

from metadata import JAR_NAME, SOURCE_NAME, VerificationError
from release_artifact import verify_liquidbounce_release
from vendor_bundle import verify_baritone_jar, verify_notice_bundle, verify_source_archive


def main() -> None:
    vendor = pathlib.Path(sys.argv[1]).resolve(strict=True)
    release_argument = sys.argv[2]
    vendor_jar = vendor / JAR_NAME

    verify_baritone_jar(vendor_jar)
    verify_source_archive(vendor / SOURCE_NAME)
    verify_notice_bundle(vendor)

    if release_argument:
        release_path = pathlib.Path(release_argument).resolve(strict=True)
        verify_liquidbounce_release(release_path, vendor_jar)

    suffix = " and LiquidBounce nested-mod artifact" if release_argument else ""
    print(f"[baritone-vendor] verified pinned vendor bundle{suffix}")


try:
    main()
except (VerificationError, FileNotFoundError, OSError) as error:
    print(f"[baritone-vendor] ERROR: {error}", file=sys.stderr)
    raise SystemExit(1)

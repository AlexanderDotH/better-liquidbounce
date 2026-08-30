from __future__ import annotations

import pathlib
import zipfile

from metadata import (
    JAR_NAME,
    JAR_SHA256,
    RELEASE_LICENSE_ENTRY,
    RELEASE_NOTICE_ENTRY,
    RELEASE_ORIGIN_ENTRY,
    VerificationError,
    dependency_is_exact,
    read_json,
    require,
    sha256_bytes,
)


def verify_liquidbounce_release(path: pathlib.Path, vendor_jar: pathlib.Path) -> None:
    require(path.is_file(), f"missing LiquidBounce release JAR: {path}")
    nested_entry = f"META-INF/jars/{JAR_NAME}"

    try:
        with zipfile.ZipFile(path) as archive:
            require(archive.testzip() is None, f"corrupt ZIP entry in LiquidBounce release: {path}")
            names = set(archive.namelist())
            require(nested_entry in names, f"LiquidBounce release does not contain {nested_entry}")
            require(RELEASE_LICENSE_ENTRY in names, "LiquidBounce release lacks the Baritone LGPL-3.0 license")
            require(RELEASE_NOTICE_ENTRY in names, "LiquidBounce release lacks the Baritone third-party notice")
            require(RELEASE_ORIGIN_ENTRY in names, "LiquidBounce release lacks the Baritone origin metadata")
            require(
                not any(name.startswith("baritone/") for name in names),
                "Baritone classes were flattened into LiquidBounce",
            )
            nested_bytes = archive.read(nested_entry)
            require(sha256_bytes(nested_bytes) == JAR_SHA256, "LiquidBounce nested Baritone JAR was modified")
            require(
                nested_bytes == vendor_jar.read_bytes(),
                "LiquidBounce nested Baritone JAR differs from vendor input",
            )
            require(
                archive.read(RELEASE_LICENSE_ENTRY) == (vendor_jar.parent / "LICENSE").read_bytes(),
                "LiquidBounce release Baritone license differs from the reviewed vendor license",
            )
            require(
                archive.read(RELEASE_NOTICE_ENTRY) == (vendor_jar.parent / "NOTICE.md").read_bytes(),
                "LiquidBounce release Baritone notice differs from the reviewed vendor notice",
            )
            require(
                archive.read(RELEASE_ORIGIN_ENTRY) == (vendor_jar.parent / "ORIGIN.md").read_bytes(),
                "LiquidBounce release Baritone origin metadata differs from the reviewed vendor metadata",
            )

            descriptor = read_json(archive, "fabric.mod.json")
            require(
                dependency_is_exact(descriptor.get("depends", {}).get("baritone")),
                "LiquidBounce fabric.mod.json does not require the exact Baritone version",
            )
    except zipfile.BadZipFile as error:
        raise VerificationError(f"invalid LiquidBounce release JAR: {path}") from error

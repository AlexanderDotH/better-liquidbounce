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

python3 - "$vendor_directory" "$release_jar" <<'PY'
from __future__ import annotations

import hashlib
import io
import json
import pathlib
import sys
import tarfile
import zipfile


VERSION = "1.15.0-10-g2991d921"
COMMIT = "2991d9218050707df9c8daca5efd371091a92d36"
JAR_NAME = f"baritone-api-fabric-{VERSION}.jar"
SOURCE_NAME = f"baritone-{VERSION}-sources.tar.gz"
JAR_SHA256 = "ab779fd74cb995b89b0979e71adb0a1a839ff2d9a1b59d0813dab7a71759509f"
SOURCE_SHA256 = "d9a4994c3dd33ea1bb729305470a2bcad4f5cd677be21b0f860524b563f1bab8"
LICENSE_SHA256 = "a5681bf9b05db14d86776930017c647ad9e6e56ff6bbcfdf21e5848288dfaf1b"
NETHER_PATHFINDER_SHA256 = "2ab97a3ef0d828eb8fc53adcbf78e92c645409eab10a8cff2646d52f64b11210"
NETHER_PATHFINDER_ENTRY = "META-INF/jars/nether-pathfinder-1.6.jar"
RELEASE_LICENSE_ENTRY = "META-INF/licenses/baritone/LICENSE"
RELEASE_NOTICE_ENTRY = "META-INF/notices/baritone/NOTICE.md"
RELEASE_ORIGIN_ENTRY = "META-INF/notices/baritone/ORIGIN.md"
SOURCE_ROOT = f"baritone-{VERSION}/"

REQUIRED_BARITONE_ENTRIES = {
    "baritone/BaritoneProvider.class",
    "baritone/api/BaritoneAPI.class",
    "baritone/api/IBaritone.class",
    "baritone/api/IBaritoneProvider.class",
    "fabric.mod.json",
    "mixins.baritone.json",
    NETHER_PATHFINDER_ENTRY,
}
REQUIRED_MIXINS = {
    "MixinClientPlayerEntity",
    "MixinMinecraft",
    "MixinNetworkManager",
    "MixinWorldRenderer",
}
REQUIRED_NETHER_PATHFINDER_ENTRIES = {
    "dev/babbaj/pathfinder/NetherPathfinder.class",
    "dev/babbaj/pathfinder/Octree.class",
    "dev/babbaj/pathfinder/PathSegment.class",
    "fabric.mod.json",
}
REQUIRED_SOURCE_ENTRIES = {
    f"{SOURCE_ROOT}LICENSE",
    f"{SOURCE_ROOT}README.md",
    f"{SOURCE_ROOT}build.gradle",
    f"{SOURCE_ROOT}fabric/build.gradle",
    f"{SOURCE_ROOT}gradle.properties",
    f"{SOURCE_ROOT}gradlew",
    f"{SOURCE_ROOT}src/api/java/baritone/api/BaritoneAPI.java",
    f"{SOURCE_ROOT}src/main/java/baritone/BaritoneProvider.java",
}


class VerificationError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise VerificationError(message)


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_json(archive: zipfile.ZipFile, entry: str) -> dict:
    try:
        return json.loads(archive.read(entry))
    except KeyError as error:
        raise VerificationError(f"missing ZIP entry: {entry}") from error
    except json.JSONDecodeError as error:
        raise VerificationError(f"invalid JSON in ZIP entry {entry}: {error}") from error


def verify_baritone_jar(path: pathlib.Path) -> None:
    require(path.is_file(), f"missing Baritone JAR: {path}")
    require(sha256_file(path) == JAR_SHA256, f"unexpected SHA-256 for {path.name}")

    try:
        with zipfile.ZipFile(path) as archive:
            require(archive.testzip() is None, f"corrupt ZIP entry in {path.name}")
            names = set(archive.namelist())
            missing = sorted(REQUIRED_BARITONE_ENTRIES - names)
            require(not missing, f"missing Baritone entries: {', '.join(missing)}")

            descriptor = read_json(archive, "fabric.mod.json")
            require(descriptor.get("id") == "baritone", "unexpected Baritone Fabric mod id")
            require(descriptor.get("version") == VERSION, "unexpected Baritone Fabric version")
            require(descriptor.get("license") == "LGPL-3.0", "unexpected Baritone license metadata")
            require(descriptor.get("mixins") == ["mixins.baritone.json"], "unexpected Baritone mixin list")
            require(descriptor.get("depends", {}).get("minecraft") == ["26.2"], "unexpected Minecraft dependency")
            require(descriptor.get("depends", {}).get("fabricloader") == ">=0.19.3", "unexpected Loader dependency")
            require(
                descriptor.get("jars") == [{"file": NETHER_PATHFINDER_ENTRY}],
                "unexpected Baritone nested-JAR declaration",
            )

            mixins = read_json(archive, "mixins.baritone.json")
            require(mixins.get("required") is True, "Baritone mixins must remain required")
            require(mixins.get("package") == "baritone.launch.mixins", "unexpected Baritone mixin package")
            require(REQUIRED_MIXINS <= set(mixins.get("client", [])), "required Baritone client mixins are missing")
            require(
                b"baritone.BaritoneProvider" in archive.read("baritone/api/BaritoneAPI.class"),
                "BaritoneAPI reflection provider target is missing",
            )

            nested_bytes = archive.read(NETHER_PATHFINDER_ENTRY)
            require(sha256_bytes(nested_bytes) == NETHER_PATHFINDER_SHA256, "unexpected Nether Pathfinder digest")
            with zipfile.ZipFile(io.BytesIO(nested_bytes)) as nested:
                require(nested.testzip() is None, "corrupt Nether Pathfinder nested JAR")
                require(
                    REQUIRED_NETHER_PATHFINDER_ENTRIES <= set(nested.namelist()),
                    "required Nether Pathfinder entries are missing",
                )
    except zipfile.BadZipFile as error:
        raise VerificationError(f"invalid Baritone JAR: {path}") from error


def verify_source_archive(path: pathlib.Path) -> None:
    require(path.is_file(), f"missing source archive: {path}")
    require(sha256_file(path) == SOURCE_SHA256, "unexpected Baritone source archive digest")

    try:
        with tarfile.open(path, mode="r:gz") as archive:
            members = archive.getmembers()
            names = {member.name for member in members}
            require(REQUIRED_SOURCE_ENTRIES <= names, "source archive is missing build or API sources")
            for member in members:
                normalized = pathlib.PurePosixPath(member.name)
                require(not normalized.is_absolute(), f"absolute source archive entry: {member.name}")
                require(".." not in normalized.parts, f"traversal source archive entry: {member.name}")
                require(member.name == SOURCE_ROOT.rstrip("/") or member.name.startswith(SOURCE_ROOT),
                        f"entry outside source root: {member.name}")

            properties_member = archive.extractfile(f"{SOURCE_ROOT}gradle.properties")
            require(properties_member is not None, "missing upstream gradle.properties")
            properties = properties_member.read().decode("utf-8")
            require("java_version=25" in properties, "source archive does not pin Java 25")
            require("minecraft_version=26.2" in properties, "source archive does not pin Minecraft 26.2")
            require("fabric_version=0.19.3" in properties, "source archive does not pin Fabric Loader 0.19.3")
            require("nether_pathfinder_version=1.6" in properties, "source archive does not pin Nether Pathfinder 1.6")
    except (tarfile.TarError, OSError) as error:
        raise VerificationError(f"invalid source archive: {path}") from error


def verify_notice_bundle(vendor: pathlib.Path) -> None:
    license_path = vendor / "LICENSE"
    require(license_path.is_file(), "missing Baritone LGPL-3.0 license")
    require(sha256_file(license_path) == LICENSE_SHA256, "unexpected Baritone license digest")

    origin = (vendor / "ORIGIN.md").read_text(encoding="utf-8")
    notice = (vendor / "NOTICE.md").read_text(encoding="utf-8")
    require(COMMIT in origin, "origin metadata does not pin the full upstream commit")
    require("Java 25" in origin and ":fabric:build" in origin, "origin metadata lacks rebuild instructions")
    require("LGPL-3.0" in notice and SOURCE_NAME in notice, "third-party notice lacks license or source details")
    require("nether-pathfinder" in notice and "license/permission" in notice, "nested dependency license gap is unrecorded")


def dependency_is_exact(value: object) -> bool:
    accepted = {VERSION, f"={VERSION}"}
    if isinstance(value, str):
        return value in accepted
    if isinstance(value, list):
        return len(value) == 1 and value[0] in accepted
    return False


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
            require(not any(name.startswith("baritone/") for name in names), "Baritone classes were flattened into LiquidBounce")
            nested_bytes = archive.read(nested_entry)
            require(sha256_bytes(nested_bytes) == JAR_SHA256, "LiquidBounce nested Baritone JAR was modified")
            require(nested_bytes == vendor_jar.read_bytes(), "LiquidBounce nested Baritone JAR differs from vendor input")
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


def main() -> None:
    vendor = pathlib.Path(sys.argv[1]).resolve(strict=True)
    release_argument = sys.argv[2]
    vendor_jar = vendor / JAR_NAME

    verify_baritone_jar(vendor_jar)
    verify_source_archive(vendor / SOURCE_NAME)
    verify_notice_bundle(vendor)

    if release_argument:
        verify_liquidbounce_release(pathlib.Path(release_argument).resolve(strict=True), vendor_jar)

    suffix = " and LiquidBounce nested-mod artifact" if release_argument else ""
    print(f"[baritone-vendor] verified pinned vendor bundle{suffix}")


try:
    main()
except (VerificationError, FileNotFoundError, OSError) as error:
    print(f"[baritone-vendor] ERROR: {error}", file=sys.stderr)
    raise SystemExit(1)
PY

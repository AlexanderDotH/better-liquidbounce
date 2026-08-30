from __future__ import annotations

import io
import pathlib
import tarfile
import zipfile

from metadata import (
    COMMIT,
    JAR_SHA256,
    LICENSE_SHA256,
    NETHER_PATHFINDER_ENTRY,
    NETHER_PATHFINDER_SHA256,
    REQUIRED_BARITONE_ENTRIES,
    REQUIRED_MIXINS,
    REQUIRED_NETHER_PATHFINDER_ENTRIES,
    REQUIRED_SOURCE_ENTRIES,
    SOURCE_NAME,
    SOURCE_ROOT,
    SOURCE_SHA256,
    VERSION,
    VerificationError,
    read_json,
    require,
    sha256_bytes,
    sha256_file,
)


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
                require(
                    member.name == SOURCE_ROOT.rstrip("/") or member.name.startswith(SOURCE_ROOT),
                    f"entry outside source root: {member.name}",
                )

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
    require(
        "nether-pathfinder" in notice and "license/permission" in notice,
        "nested dependency license gap is unrecorded",
    )

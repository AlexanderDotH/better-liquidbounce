from __future__ import annotations

import hashlib
import json
import pathlib
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


def dependency_is_exact(value: object) -> bool:
    accepted = {VERSION, f"={VERSION}"}
    if isinstance(value, str):
        return value in accepted
    if isinstance(value, list):
        return len(value) == 1 and value[0] in accepted
    return False

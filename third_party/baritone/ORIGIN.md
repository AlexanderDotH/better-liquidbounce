# Baritone vendor origin

This directory pins an unmodified Baritone API-Fabric build for LiquidBounce.
It is intentionally kept as a separate Fabric mod JAR: it is not flattened,
relocated, or patched.

## Source identity

- Upstream: <https://github.com/cabaletta/baritone>
- Commit: `2991d9218050707df9c8daca5efd371091a92d36`
- Commit date: `2026-08-11T14:34:25-07:00`
- Upstream `git describe --always --tags --first-parent`:
  `v1.15.0-10-g2991d921`
- Minecraft: `26.2`
- Fabric Loader: `0.19.3`
- Java toolchain: Java 25
- Nether Pathfinder dependency: `dev.babbaj:nether-pathfinder:1.6`

The adjacent `baritone-1.15.0-10-g2991d921-sources.tar.gz` is a deterministic
`git archive` of that exact commit. `SHA256SUMS` authenticates both source and
binary files within this repository.

## Binary build

The vendored binary was built from the root of a detached upstream checkout on
OpenJDK `25.0.4.1` with Baritone's Gradle `8.14.4` wrapper. The CI-equivalent
command used for this exact file was:

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon build \
  -Pmod_version="$(git describe --always --tags --first-parent | cut -c2-)"
```

A focused rebuild of the same API-Fabric artifact can use `:fabric:build` from
the same checkout root:

```bash
./gradlew --no-daemon :fabric:build \
  -Pmod_version="$(git describe --always --tags --first-parent | cut -c2-)"
```

Expected output:

```text
fabric/build/libs/baritone-api-fabric-1.15.0-10-g2991d921.jar
SHA-256: ab779fd74cb995b89b0979e71adb0a1a839ff2d9a1b59d0813dab7a71759509f
```

The deterministic source archive was created with:

```bash
git archive --format=tar \
  --prefix=baritone-1.15.0-10-g2991d921/ \
  --output=baritone-1.15.0-10-g2991d921-sources.tar \
  2991d9218050707df9c8daca5efd371091a92d36
gzip -n -9 baritone-1.15.0-10-g2991d921-sources.tar
```

Run `scripts/verify-baritone-vendor.sh` from any directory to verify this
vendor bundle. Pass a built LiquidBounce release JAR as its sole argument to
also verify the final nested-mod packaging.

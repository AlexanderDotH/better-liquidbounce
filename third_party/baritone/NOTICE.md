# Baritone third-party notice and source availability

LiquidBounce uses an **unmodified** Baritone API-Fabric JAR as an independently
described nested Fabric mod.

- Component: Baritone `1.15.0-10-g2991d921`
- Copyright: the Baritone contributors
- License: GNU Lesser General Public License v3.0 (`LGPL-3.0`)
- Upstream source: <https://github.com/cabaletta/baritone/commit/2991d9218050707df9c8daca5efd371091a92d36>
- Vendored binary: `baritone-api-fabric-1.15.0-10-g2991d921.jar`
- Corresponding source: `baritone-1.15.0-10-g2991d921-sources.tar.gz`

The complete upstream LGPL-3.0 license text is in the adjacent `LICENSE` file.
The corresponding source archive and the GPL-licensed LiquidBounce application
source/build files are conveyed together so recipients can inspect, rebuild,
replace, and relink the library. A replacement Baritone build can be placed at
the pinned vendor path and LiquidBounce rebuilt; the recorded checksums and
packaging tests must then be deliberately updated to the reviewed replacement.

The Baritone JAR is not relocated or merged into LiquidBounce. Its own
`fabric.mod.json`, `mixins.baritone.json`, API/provider classes, and nested
dependency remain intact.

## Nested Nether Pathfinder dependency

The unmodified upstream Baritone JAR itself contains
`META-INF/jars/nether-pathfinder-1.6.jar` from
`dev.babbaj:nether-pathfinder:1.6`. The exact nested JAR SHA-256 is
`2ab97a3ef0d828eb8fc53adcbf78e92c645409eab10a8cff2646d52f64b11210`;
its source tag is <https://github.com/babbaj/nether-pathfinder/tree/v1.6>.

At the time of vendoring, that repository, tag, Maven POM, and binary metadata
did not contain an explicit software license. Baritone's LGPL-3.0 notice does
not by itself establish redistribution rights for this separately authored
dependency. Release redistribution therefore remains gated on an explicit
upstream license/permission clarification or replacement/removal of this
dependency. This notice records the gap rather than inferring permission.

THE SOFTWARE IS PROVIDED WITHOUT WARRANTY; see the applicable license text for
the complete terms.

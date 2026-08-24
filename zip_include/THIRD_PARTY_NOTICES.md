# SeedCracker third-party notices

The SeedCracker module is entirely local at runtime. It does not bundle or launch
SeedCrackerX or a Nether Bedrock Cracker executable.

## SeedFinding libraries and LattiCG

The release JAR embeds the following Java dependencies through the project's
Jar-in-Jar packaging mechanism. Their classes are relocated below
`net.ccbluex.liquidbounce.seedcracker.seedfinding` before embedding, solely to
avoid ViaFabricPlus' separately bundled `com.seedfinding` classes taking
precedence at runtime. The embedded lock file retains these exact source
coordinates; no code from ViaFabricPlus is modified or reused.

- `com.seedfinding:mc_math:851e9d0577dfdca50154e98f1d334bd31c641326`
- `com.seedfinding:mc_seed:55f6242001f7eb4226df4ed0d023f1838a54a99d`
- `com.seedfinding:mc_core:eee662999e9f3fe037476b6940dbd6d5e23cdbb6`
- `com.seedfinding:mc_noise:dbab3996ea3abff5dd420db53c31d5498afd2fe5`
- `com.seedfinding:mc_biome:17af8cb1110fdc983b7cb2b887d1fb2060e23ee3`
- `com.seedfinding:mc_terrain:a03e440ec5b282e399382f2cc5ad0db91b438d2e`
- `com.seedfinding:mc_feature:755d3611ac1c499c28289ccca5b738af6a5859b7`
- `com.seedfinding:mc_reversal:75aa6ce47a9f53a1aa212765e9830e08f6c86299`
- `com.seedfinding:latticg:1.07`

Their published Maven metadata identifies these artifacts as MIT licensed.
SeedFinding sources: <https://github.com/SeedFinding>. LattiCG source:
<https://github.com/mjtb49/LattiCG>.

MIT License

Copyright (c) the respective SeedFinding and LattiCG contributors.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Compatibility references

SeedCrackerX by 19MisterX98 is an MIT-licensed compatibility reference for
the Java 26.2 dependency family and supported structure methods. It is not a
runtime dependency of this module.

- <https://github.com/19MisterX98/SeedcrackerX>
- <https://github.com/19MisterX98/SeedcrackerX/blob/master/LICENSE>

The pure-Kotlin Nether bedrock implementation was checked against the public
behavior and documented Java-LCG constants of Nether_Bedrock_Cracker by
19MisterX98. No Rust binary, native library, or upstream source file is
embedded in this release.

- <https://github.com/19MisterX98/Nether_Bedrock_Cracker>
- <https://github.com/19MisterX98/Nether_Bedrock_Cracker/blob/gui/LICENSE>

## Baritone 26.2

The release embeds the unmodified Baritone API-Fabric artifact built from
commit `2991d9218050707df9c8daca5efd371091a92d36` as an intact nested Fabric
mod. Its API remains in the `baritone.api` namespace; its implementation,
provider metadata and mixins are neither flattened nor relocated.

- Artifact: `baritone-api-fabric-1.15.0-10-g2991d921.jar`
- SHA-256: `ab779fd74cb995b89b0979e71adb0a1a839ff2d9a1b59d0813dab7a71759509f`
- Source: <https://github.com/cabaletta/baritone/tree/2991d9218050707df9c8daca5efd371091a92d36>
- License: GNU Lesser General Public License 3.0 or later

The matching deterministic source archive is shipped under `sources/` in the
LiquidBounce distribution. The release JAR also includes Baritone's license,
origin and source-offer documents below `META-INF/licenses/baritone/` and
`META-INF/notices/baritone/`.

### Unresolved nested dependency license

Baritone's official Fabric artifact itself nests `dev.babbaj:nether-pathfinder:1.6`.
Its upstream repository and published POM do not currently declare a license.
This notice records that unresolved provenance instead of treating Baritone's
LGPL as a license grant for the separate dependency. Public redistribution
remains gated on an explicit license/permission clarification or replacement
of that dependency.

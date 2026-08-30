# Contributing to LiquidBounce

Keep changes reviewable, behavior-preserving, and inside one responsibility.
Feature work and structural cleanup belong in separate pull requests. Upstream
synchronization also remains separate from refactoring waves.

## Required environment

- Java 25, as pinned in `gradle/libs.versions.toml`
- Node 24.18.1, as pinned in `.node-version`
- npm 12.0.2, as pinned in `.npm-version`

Run Node commands through `fnm exec --using=24.18.1`. Point `JAVA_HOME` to a
Java 25 installation.

## Before editing

1. Read `build/reports/source-hygiene/source-quality.md` when it exists.
2. Identify the stable rule ID, responsibility, and suggested target package for
   the files you will touch.
3. Add characterization tests for every public, config, packet, event, Mixin,
   Script API, REST, WebSocket, or UI contract that the move can affect.
4. Keep the change to one feature or one neutral shared core.

Move methods unchanged before renaming or simplifying them. Reconnect callers
through a feature contract, stable module facade, explicit Mixin bridge, or
neutral shared core. Do not introduce a forbidden dependency to make a move
compile.

## Hygiene contract

- Production and UI files stay at or below 200 effective lines; tests stay at or
  below 300.
- Category roots contain only `Module*.kt` facades.
- Do not add structural suppressions, allowlists, package cycles, or forbidden
  dependency edges.
- Do not add or raise a ratchet baseline entry. A touched legacy violation must
  decrease or disappear.
- A recurring new anti-pattern requires a rule ID, executable gate test,
  actionable feedback, and Coding Standards entry.
- Keep first-party scripts and tools within the same limits as production code.

The complete rule catalog and repair guidance live in
[`.github/CODING_STANDARDS.md`](.github/CODING_STANDARDS.md).

## Verification

Run the repository contract before requesting review:

```sh
fnm exec --using=24.18.1 -- env JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  ./gradlew --no-daemon qualityGate
```

The release job also runs `build verifyReleaseArtifact`. For Minecraft 26.x it
must inspect non-obfuscated Loom's `jar` output and reject development and sources JARs.
Never copy the resulting JAR to PrismLauncher as part of a refactor PR, and do
not infer gameplay behavior from automated checks.

Attach or link the Markdown, JSON, SARIF, and Detekt reports when the gate
fails. Explain each remaining failure rather than hiding it with a suppression.

## Review and history

- Use separate commits for characterization tests, mechanical moves,
  extraction/wiring, and gate updates.
- Do not combine method renaming with method movement.
- Resolve all review threads. CODEOWNERS requests review automatically, but the
  single-owner fork does not require a human approval that nobody else can give.
- Rebase before merge. Direct pushes, force pushes, branch deletion, merge
  commits, and squash merges are not part of the `nextgen` contract.

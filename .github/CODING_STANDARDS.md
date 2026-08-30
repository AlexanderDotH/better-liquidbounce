# Project LiquidBounce Coding Standards

We invite everyone to participate in the development of LiquidBounce by submitting pull requests and opening issues in
this repository. However, we have to enforce certain standards to keep our code readable, consistent and easier to
maintain.

We kindly ask you to use [Kotlin](https://kotlinlang.org/) instead of Java for new code, if possible. In the long term,
it is our goal to largely migrate LiquidBounce to Kotlin.

If you are adding Java code, make sure it uses the correct nullability marks
and does not use legacy features from older versions of Java.
The following are common reasons for using Java:

- The framework is designed for Java (such as `mixin`)
- In some cases, Java code can achieve better readability or performance than Kotlin (if necessary, please describe in the PR)

Contributors: https://github.com/CCBlueX/LiquidBounce/graphs/contributors

## General

This section lists the official conventions of the languages Kotlin and Java. This project tries to follow them as
closely as possible, and we expect outside developers to do the same when working on the client.

### Repository standard

* Run Gradle task `qualityGate` before requesting review. It includes source hygiene, architecture, Detekt, tests,
  theme verification, compilation, and release-artifact checks.
* All new Java code must use [JSpecify](https://jspecify.dev/) annotations to mark nullability, which can be detected by Kotlin compiler and your IDE.

**Additional, non-standard conventions will be listed below and must also be followed.**

### Kotlin

* Follow Kotlin's
  official [code conventions](https://kotlinlang.org/docs/reference/coding-conventions.html#coding-conventions).
* Have a look at Kotlin's official [documentation](https://kotlinlang.org/docs/reference/).

### Java

* Have a look at Oracle's [Java Code PDF document](https://www.oracle.com/technetwork/java/codeconventions-150003.pdf).
* Read the Wikipedia article on [Java's Syntax](https://en.wikipedia.org/wiki/Java_syntax).
* Look at Oracle's [Java Tutorial](https://docs.oracle.com/javase/tutorial/java/).

# Files

### Generation

To document the ownership of a file, we include the following text in all code files *(.kt and .java)* at the beginning
of the file:

```kotlin
/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
```

If you are using IntelliJ IDEA, this header will be automatically added when you create a new file ([Config](/.idea/copyright/LiquidBounce.xml)).

### Tags

You are allowed to use the `@author <author-name>` tag, but try to limit its usage as much as possible.

You are recommended to use the following tags for bypasses:

`@anticheat <anticheat-name>`
`@anticheatVersion <anticheat-version>`
`@testedOn <anticheat-test-server>`
`@note <note-text>` - used for a special comments on a bypass

You aren't allowed to use any other tags.

# Packages

### Naming

Our naming of packages follows the following format:

* `country.company-name.product-name`

*Example:*

* `net.ccbluex.liquidbounce`

If your code is self-contained and not designed exclusively for LiquidBounce, we may allow you to include it in a
separate package outside `net.ccbluex.liquidbounce`. Please note that we have to decide on a case by case basis.

*Example:*
`net.vitox` instead of `net.ccbluex`

Links:

* [Java Package](https://en.wikipedia.org/wiki/Java_package "Wikipedia article").

# Repository hygiene gate

The source-quality gate applies to first-party Kotlin, Java, KTS, Svelte, TypeScript, JavaScript, MJS, shell scripts,
and tools. Generated output, dependency lockfiles, binary files, resources, and reviewed third-party code are excluded.
The standard license header and blank lines do not count toward effective lines. Imports and explanatory comments do.

Run the same contract used by CI with Java 25, Node 24.18.1, and npm 12.0.2:

```sh
fnm exec --using=24.18.1 -- npm install --global npm@12.0.2
fnm exec --using=24.18.1 -- env JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew qualityGate
```

Machine-readable feedback is written to `build/reports/source-hygiene/source-quality.json` and
`source-quality.sarif`; the human report is `source-quality.md`. Every finding includes a stable rule ID, location,
measured and allowed values, rationale, and a concrete extraction or target-package suggestion.

CI runs the same `quality-gate` and `release-build` jobs for pushes and pull requests. Reports remain downloadable as
workflow artifacts when a fork or Dependabot token cannot publish SARIF to GitHub code scanning.

The versioned `.github/rulesets/nextgen.json` payload is the required branch policy. A repository administrator must
import it once under **Settings > Rules > Rulesets**, activate it without bypass actors, and enable repository-level
rebase merging. This external activation cannot be inferred from a green local gate. See `.github/rulesets/README.md`
for the exact application and verification steps.

## Refactoring without behavior changes

Before moving production code, characterize the contracts the code exposes. Preserve module names and order,
settings and migrations, event priority, packet order and identity, cleanup behavior, Mixin and Script API entrypoints,
REST/WebSocket shapes, UI routes, persisted keys, and CEF input behavior.

Use separate commits for characterization tests, mechanical moves, and extraction/wiring. Move a method unchanged
before renaming or simplifying it. A module facade owns settings, event registration, and delegation; runtime state,
planning, policy, integration, rendering, and research belong in focused collaborators.

Complex features use only the roles they need:

```text
<feature>/contract  <feature>/model       <feature>/config
<feature>/policy    <feature>/planner     <feature>/session
<feature>/runtime   <feature>/integration <feature>/render <feature>/research
```

Dependencies inside a feature point from the module facade toward outer adapters, runtime/session, policy/planner,
and finally contract/model. Cross-feature access uses a contract, a stable module facade, or a neutral shared core.

## Stable source-quality rules

<a id="lb-hyg-001"></a>
### LB-HYG-001: effective-file-lines

Production and UI files may contain at most 200 effective lines; test files may contain at most 300. Extract by one
responsibility while keeping the original public contract at its established entrypoint.

<a id="lb-hyg-002"></a>
### LB-HYG-002: structural-suppression

Do not suppress `LargeClass`, `TooManyFunctions`, `LongMethod`, `CognitiveComplexMethod`, or `NestedBlockDepth`.
Production methods are limited to 40 lines, complexity 12, and nesting depth 3; test methods are limited to 60 lines.
Resolve the reported responsibility instead of hiding it.

<a id="lb-hyg-003"></a>
### LB-HYG-003: package-path-alignment

The declared package must match the source path. Move the declaration and all imports together, then compile every
consumer, including Java Mixins.

<a id="lb-hyg-004"></a>
### LB-HYG-004: category-root-modules-only

A module-category root contains only `Module*.kt` facades. Place helpers below a feature package named for their
responsibility, for example `combat/macekill/session` or `player/reach/planner`.

<a id="lb-hyg-005"></a>
### LB-HYG-005: semantic-prefix-cluster

Five or more sibling files sharing a two-token semantic prefix indicate a missing feature boundary. Move the cluster
under its own feature/responsibility package. Homogeneous strategy collections under `modes`, `exploits`, and
`triggers` are valid.

<a id="lb-arch-001"></a>
### LB-ARCH-001: forbidden-dependency

Dependencies point downward through this responsibility graph:

```text
bootstrap/injection -> integration/script -> features -> api/render-core
                    -> event/config -> common/utils-core
```

`utils-core` does not import features, events, config, rendering, or Mixins. Event and config do not form a cycle.
Render core does not know concrete modules. Features do not import injection or concrete integration implementations.
Injection calls stable facades or explicit bridges.

<a id="lb-arch-002"></a>
### LB-ARCH-002: package-cycle

Package cycles are forbidden. Move the shared contract inward or invert the outer dependency through a narrow port.

<a id="lb-ratchet-001"></a>
### LB-RATCHET-001: new-or-worsened-debt

During migration, new violations are forbidden. A touched violating file must reduce the reported metric or become
fully compliant. Never trade one structural violation for another.

<a id="lb-ratchet-002"></a>
### LB-RATCHET-002: ratchet-baseline-increase

The temporary debt baseline may only shrink. Do not add entries, increase measurements, loosen limits, create an
allowlist, or add an inline suppression. Delete the baseline when the final legacy violation is removed.

<a id="lb-policy-001"></a>
### LB-POLICY-001: pinned-build-toolchains

Keep Java, Node, and npm pinned only in `gradle/libs.versions.toml`, `.node-version`, and `.npm-version`. CI and local
acceptance must consume those files and fail with the exact repair command when the active process uses another version.

<a id="lb-policy-002"></a>
### LB-POLICY-002: ci-acceptance-parity

Push and pull-request events run the same `quality-gate` and `release-build` jobs. `quality-gate` invokes
`qualityGate`; `release-build` invokes `build verifyReleaseArtifact`. Never add `-x test`, `-x detekt`,
or an equivalent exclusion. Preserve Markdown, JSON, SARIF, and Detekt report publication, and fail CI before artifact
upload when any required file is absent instead of silently ignoring the missing feedback. The
artifact step runs even after gate failure. For pushes and same-repository
pull requests, missing source-quality or Detekt SARIF must also fail its code-scanning upload. Fork and Dependabot pull
requests retain the complete report artifact but skip code-scanning upload because their tokens cannot safely publish it.

<a id="lb-policy-003"></a>
### LB-POLICY-003: nextgen-protection-contract

Keep `.github/rulesets/nextgen.json` active, without bypass actors, and synchronized with the exact CI job names. It
must block deletion and force pushes, require linear rebase-only pull requests with resolved threads, and require
up-to-date green `quality-gate` and `release-build` checks. The single-owner fork uses zero mandatory approvals and no
last-push approval, because no second collaborator can provide them. Local validation does not replace the one-time
external GitHub import.

<a id="lb-policy-004"></a>
### LB-POLICY-004: executable-ci-entrypoint

Shell entrypoints invoked directly by CI must be tracked as executable. In particular,
`scripts/verify-baritone-vendor.sh` remains mode `100755`; moving logic into focused helpers must not break its entrypoint.

<a id="lb-policy-005"></a>
### LB-POLICY-005: wired-gradle-responsibility

Every first-party `gradle/*.gradle.kts` responsibility file must be applied exactly once by `build.gradle.kts`. The root
`qualityGate` must also depend on `testBuildLogic`, which executes the independent `buildSrc` test suite. Do not leave
obsolete intermediate scripts or duplicate dependency declarations after splitting build logic. Update the policy test
whenever a deliberate responsibility slice is added, renamed, or removed.

<a id="lb-policy-006"></a>
### LB-POLICY-006: complete-tool-module-packaging

When a runnable tool entrypoint delegates to focused helper modules, every deployment or generation workflow must copy
the complete module tree. `ts-defgen.js` and `ts-defgen/*.js` are one runtime unit; copying only the entrypoint produces
a clean local source tree but a broken generated-definition job.

<a id="lb-policy-007"></a>
### LB-POLICY-007: contributor-review-contract

Keep `CONTRIBUTING.md`, `.github/CODEOWNERS`, and `.github/pull_request_template.md` together. The contribution guide
defines the behavior-preserving workflow, CODEOWNERS requests accountable review, and the pull-request checklist
requires characterization, gate, and ratchet evidence. On the single-owner fork, neither human nor code-owner approval
is mandatory: the sole owner cannot approve a pull request they authored. The PR, resolved-thread, required-check, and
history rules still enforce a reviewable audit path. Keep every stable rule anchor in this document and the actionable
feedback loop in `AGENTS.md`; the repository-policy tests reject documentation or agent-instruction drift.

<a id="lb-policy-008"></a>
### LB-POLICY-008: loom-release-artifact

Minecraft 26.x is non-obfuscated and uses Loom's `net.fabricmc.fabric-loom` plugin. In this mode `jar` is the
publishable mod artifact and Loom intentionally does not register `remapJar`. Bind `verifyReleaseArtifact` lazily to
`tasks.named<Jar>("jar").archiveFile`, depend on that task provider, and explicitly reject `-dev.jar` and
`-sources.jar`. Obfuscated Minecraft versions instead use `net.fabricmc.fabric-loom-remap` and `remapJar`; do not mix
the two contracts. The `release-build` CI job must run verification and select the same classifier-safe artifact.
Archive integrity proves packaging only; it does not prove live gameplay.

## Agent feedback loop

1. Read `source-quality.md` before editing and identify the rule, responsibility, and suggested target package.
2. Add or run characterization tests for the affected public contract.
3. Move one responsibility without changing its logic, then reconnect it through an allowed contract.
4. Run `qualityGate` after each responsibility and confirm the ratchet decreased.
5. If a recurring anti-pattern is not represented, add a stable rule ID, executable gate test, actionable message,
   and this documentation in the same change. A prose-only convention is not enforceable.

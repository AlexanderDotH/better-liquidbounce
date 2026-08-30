## Learned User Preferences

- After building, deploy the release JAR (`liquidbounce-0.*.jar`, not dev/sources) to `/home/alex/.local/share/PrismLauncher/instances/Performium 21.1/minecraft/mods/` for in-game testing.
- Never copy `-sources.jar` or other non-mod build artifacts into the Performium mods folder.
- When implementing from a plan file, do not edit the plan file itself; execute against it as specified.
- Prefer plain ClickGUI text fields over searchable player dropdown UI for entering player names.
- For SpearKill A* routes: keep the final approach lateral (not a top-down last step), stop within spear reach, and force instant path-follow rotations that override normal look while moving.

## Learned Workspace Facts

- LiquidBounce is a Minecraft client mod: Kotlin/Java backend plus Svelte ClickGUI theme in `src-theme/` (bundled as `liquidbounce.zip` in the JAR).
- Builds require Java 25: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew build`.
- Fun-category modules live in the Kotlin package `modules.`fun`` (backticks required because `fun` is reserved).
- Java mixins cannot import the Kotlin `fun` package; expose a `@JvmStatic` Kotlin bridge in a normal package (e.g. `PlayerModelDelayHook`, `PlayerModelNametagHook`, `PlayerModelParticleHook`) for Java mixin hooks.
- Amnesia dummy nametags: when Nametags is on, suppress vanilla name+below-name on the real player and render at the visual dummy via `PlayerModelNametagHook`; draw `belowNameDisplay()` scoreText (health/hearts) with vanilla `mc.font`; avoid double-offsetting (delayed render state already positions the dummy).
- ClickGUI text inputs must use the `cefTextInput` Svelte action (WebSocket keyboard events + `GET /api/v1/client/clipboard`; release focus on `virtualScreen` close and gate keystrokes while ClickGUI is open) because JCEF does not reliably handle HTML `<input>` typing or paste.
- ClickGUI shift+hover extended descriptions for module modes/toggles need `description`, `extendedDescription`, and `key` in `ValueGroupSerializer` interop JSON.
- ClickGUI shift detection uses global `shiftHeld` state in `shiftDescription.ts` (keydown/keyup plus window blur) for CEF reliability.
- Player picker backend uses `ValueType.PLAYER` with the `world_players` REST registry (RemotePlayers, excluding self and AntiBot bots); ClickGUI renders `PLAYER` settings as plain `TextSetting`.
- Performium in-game LiquidBounce configs live under `/home/alex/.local/share/PrismLauncher/instances/Performium 21.1/minecraft/LiquidBounce/` (`modules.json`, `backups/`); `.localconfig load` overwrites live module settings.
- `ModuleSpearKill` Packet movement supports nested A* (`SpearKillAStarRoute` / path render); Wait ticks belong under Packet mode (shared by both Packet and A*).

## Repository Hygiene Contract

- Read `build/reports/source-hygiene/source-quality.md` before changing a reported file. Follow the stable rule and
  suggested responsibility/package in `.github/CODING_STANDARDS.md`.
- Keep production and UI files at or below 200 effective lines and test files at or below 300 effective lines.
- Use Node 24.18.1 with npm 12.0.2, then run
  `fnm exec --using=24.18.1 -- env JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew qualityGate` after
  each extracted responsibility. This executes the gate's own contract tests before the full Java-25 acceptance.
- Add characterization tests before moving behavior. First move methods unchanged, preserve public/config/wire/Mixin/
  Script/UI contracts, then rename or simplify in a separate step.
- Category roots contain only `Module*.kt` facades. Put config, policy, planning, session, runtime, integration,
  rendering, and research code below the owning feature package.
- Never add or enlarge the debt baseline, an allowlist, structural suppression, forbidden dependency, or package cycle.
- New recurring anti-patterns require a stable rule ID, a gate test, actionable feedback, and Coding Standards text.
  Do not record unenforced prose-only exceptions.
- Keep `.github/rulesets/nextgen.json` aligned with the `quality-gate` and `release-build` CI job names. Local checks do
  not activate GitHub protection; a repository administrator must import and verify the ruleset externally.
- Every `gradle/*.gradle.kts` responsibility must be applied exactly once from `build.gradle.kts`. Remove superseded
  build-script drafts instead of leaving unwired or duplicate dependency declarations behind.
- Treat a tool entrypoint and its helper directory as one deployment unit. If `ts-defgen.js` delegates into
  `ts-defgen/`, update and verify every workflow copy step in the same change.
- Minecraft 26.x uses non-obfuscated Fabric Loom, where `jar` is the publishable mod artifact and `remapJar` is absent.
  Verify that exact task output and never validate, publish, or deploy `-dev.jar` or `-sources.jar` as the release mod.

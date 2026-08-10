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

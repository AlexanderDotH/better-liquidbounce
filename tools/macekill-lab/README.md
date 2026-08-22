# MaceKill ClipReach Paper lab

This directory is a source-only, non-distributed lab scaffold for an explicitly authorized local
Paper server. It does not prove that ClipReach works, and it must not be shipped in the LiquidBounce
JAR. The pinned profile remains `UNVALIDATED` until client and server evidence satisfies every
acceptance item below.

## Pinned runtime

- Minecraft: `26.2` (protocol `776`)
- Paper: stable build `112`, published 2026-08-11
- Paper file: `paper-26.2-112.jar`
- Paper URL: `https://fill-data.papermc.io/v1/objects/bd3a58cf96874e5ea6643f5f6fe9b4f5bf9e34b795fa078c2f0ee8b98b2f907e/paper-26.2-112.jar`
- Paper SHA-256: `bd3a58cf96874e5ea6643f5f6fe9b4f5bf9e34b795fa078c2f0ee8b98b2f907e`
- Java: `25`
- Plugin set: only the locally built `MaceKillLabObserver` JAR recorded in `profile.json`

The Paper build metadata comes from the official Fill v3 endpoint. A future update is a new profile,
not an in-place replacement of build 112.

## Build only the observer

From the repository root:

```sh
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew -p tools/macekill-lab/observer-plugin clean build
bash tools/macekill-lab/verify-profile.sh
```

This resolves the pinned Paper API to compile the plugin. It does not download or run the Paper
server. The built observer stays under the ignored `observer-plugin/build/` directory.

## Manual lab procedure

1. Separately download the pinned Paper JAR only after receiving explicit authorization. Verify its
   SHA-256 against `profile.json` before first launch.
2. Create a disposable server directory, accept the EULA manually, and install only the observer JAR
   whose hash matches the profile. Record hashes of any additional plugin before use; otherwise the
   profile no longer applies.
3. Join with two controlled accounts. Run `/macekilllab arm <attacker> <target>` to create a temporary
   obsidian test cell and raise the target's health. Run `/macekilllab mark <client-session-id>` before
   each `.maceclip probe ...` so the server and client JSONL streams can be correlated.
4. Exercise visible, occluded, enclosed, elevated, moving, and target-death cases. Use
   `.maceclip status` and `.maceclip abort`; abort must finish the inverse recovery path.
5. Run `/macekilllab cleanup` before shutdown. The plugin restores the cell blocks and target state,
   but the server must still be disposable and backed up.

Server evidence is written to `plugins/MaceKillLabObserver/evidence/*.jsonl`. It records authoritative
positions every tick plus movement, server teleports, damage, death, disconnect, arm/mark, and cleanup
events. Client correction packets remain client-side evidence and must be correlated by session ID and
timestamps; Bukkit cannot directly observe every correction packet sent by the network layer.

## Promotion gate

Do not change the profile to `VALIDATED` unless repeated runs prove all of the following:

- exactly one attack and lethal mace damage;
- exact authoritative return to the recorded origin;
- unchanged local client position;
- no queued or cancelled owned movement packet;
- every correction is recorded and followed by bounded recovery;
- no later self-damage, stuck state, or leaked movement/slot ownership;
- the Paper JAR, Java/protocol, observer JAR, complete plugin set, client build, and evidence bundle all
  have recorded SHA-256 values.

“No correction observed” is evidence only; it is never acceptance proof.

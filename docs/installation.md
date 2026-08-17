# Installation

**Pegasus — Created by Anirban &lt;3**

Paper and Fabric are separate platforms with separate jars. Do not mix them.

| Platform | Artifact | Install to | Required on clients |
| --- | --- | --- | --- |
| Paper 1.21.1 | `Pegasus-Paper-1.0.0.jar` | `plugins/` | No |
| Fabric 1.21.1 | `Pegasus-Fabric-1.0.0.jar` | `mods/` | **Yes** |

---

## Requirements

- **Minecraft 1.21.1** (Java Edition)
- **Java 21** or newer at runtime
- Paper: a Paper 1.21.1 server
- Fabric: **Fabric Loader ≥ 0.16.0** and **Fabric API** for 1.21.1

---

## Building from source

Requires a JDK 21 toolchain; Gradle fetches everything else.

```bash
git clone <repository-url>
cd pegasus-java

./gradlew :common:test          # shared logic tests
./gradlew :paper:jar            # -> paper/build/libs/Pegasus-Paper-1.0.0.jar
./gradlew :fabric:remapJar      # -> fabric/build/libs/Pegasus-Fabric-1.0.0.jar
./gradlew buildAll              # both artifacts
./gradlew verifyAll             # tests + both builds
```

The first Fabric build downloads and remaps Minecraft, so it takes noticeably longer than later runs.

To regenerate the original textures (needs Python 3 and Pillow):

```bash
pip install Pillow
python3 assets/generate_textures.py
```

---

## Paper setup

1. Stop the server.
2. Copy `Pegasus-Paper-1.0.0.jar` into `plugins/`.
3. Start the server. You should see:

   ```
   Pegasus 1.21 — Created by Anirban <3
   [OK] Configuration loaded and validated
   [OK] Ownership storage ready (entity persistent data)
   [OK] Pegasus entity system registered
   [OK] Unicorn breeding system registered
   [OK] Spawn eggs registered (2 variants)
   [OK] Commands registered (/pegasus)
   [OK] Natural spawn system enabled (every 60s, above Y 175)
   [..] Resource pack optional: vanilla horse visuals are used unless the Pegasus pack is served
   ```

4. Edit `plugins/Pegasus/config.yml`, then run `/pegasus reload`.
5. Confirm with `/pegasus info`.

Set `allowed-worlds` to match your actual world folder names — the default is `world`, so natural
spawning does nothing on a server whose overworld folder is named something else.

**Note on visuals:** on Paper a Pegasus renders as a vanilla horse. This is a platform limitation,
not a bug. See [paper-resource-pack.md](paper-resource-pack.md).

---

## Fabric setup

The mod is required on **both** the server and every connecting client, because it registers entity
types and client renderers.

1. Install Fabric Loader 0.16.0+ for 1.21.1 on the server and on each client.
2. Download **Fabric API** for 1.21.1 and place it in `mods/` on both sides.
3. Place `Pegasus-Fabric-1.0.0.jar` in `mods/` on both sides.
4. Start the server. Expect the same banner as above, ending with:

   ```
   [OK] Natural spawn system enabled (every 60s, above Y 175)
   [..] Client rendering is provided by this mod; no resource pack required on Fabric
   ```

5. Edit `config/pegasus.json`, then run `/pegasus reload`.

A client without the mod cannot join, because the server declares entity types the client does not
know. Distribute the same jar to players. More detail: [fabric-setup.md](fabric-setup.md).

---

## Verifying the install

```
/pegasus info                    Should print the banner and current settings
/pegasus summon                  Spawns a Pegasus (op / permission required)
/pegasus giveegg <you> blue_eye  Gives a blue-eyed spawn egg
```

Then, as a normal player:

1. Right-click the Pegasus with a **Golden Apple** until taming succeeds — you become the owner.
2. Right-click with a **Saddle** to equip it.
3. Mount, press **jump** to take off, steer with your look direction, **sneak** to descend.
4. Ask another player to try mounting it; they should be refused.

To test breeding, summon two Unicorns (Fabric: `/summon pegasus:unicorn`), give each a **Nether
Star**, then feed both a **Golden Carrot**.

---

## Uninstalling

Remove the jar and restart.

Ownership data lives in each entity's own persistent data, not in a separate file, so removing the
plugin/mod leaves ordinary horses behind on Paper. On Fabric the custom entities cannot load without
the mod and Minecraft will drop them — **back up your world before uninstalling on Fabric.**

---

**Pegasus 1.21 — Created by Anirban &lt;3**

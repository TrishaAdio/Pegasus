# Fabric client and server setup

**Pegasus — Created by Anirban &lt;3**

The Fabric build registers real entity types and client renderers, so it is required on **both**
sides. This is what makes the animated winged model possible.

---

## Environment

`fabric.mod.json` declares `"environment": "*"` with two entrypoints:

| Entrypoint | Class | Runs on |
| --- | --- | --- |
| `main` | `dev.anirban.pegasus.fabric.PegasusMod` | Server and client |
| `client` | `dev.anirban.pegasus.fabric.client.PegasusClient` | Client only |

Dependencies: `fabricloader >= 0.16.0`, `fabric-api`, `minecraft ~1.21.1`, `java >= 21`.

---

## Server

1. Fabric Loader 0.16.0+ for 1.21.1.
2. `mods/fabric-api-*.jar`
3. `mods/Pegasus-Fabric-1.0.0.jar`
4. Start once to generate `config/pegasus.json`.

Only the `main` entrypoint runs; rendering classes are never loaded.

## Client

Identical: Fabric Loader, Fabric API, and the same Pegasus jar in `mods/`.

**Every player needs the mod.** A vanilla client cannot join a server running it — the server sends
`pegasus:pegasus` and `pegasus:unicorn` entity types the client cannot resolve. There is no
server-only mode; that is inherent to custom entities, and it is the trade for real custom rendering.

---

## What the client registers

`PegasusClient` registers in a deliberate order:

```java
EntityModelLayerRegistry.registerModelLayer(PEGASUS, PegasusEntityModel::getTexturedModelData);
EntityModelLayerRegistry.registerModelLayer(UNICORN, UnicornEntityModel::getTexturedModelData);
EntityRendererRegistry.register(PegasusRegistry.PEGASUS, PegasusEntityRenderer::new);
EntityRendererRegistry.register(PegasusRegistry.UNICORN, UnicornEntityRenderer::new);
```

**Model layers first, renderers second.** The geometry is guaranteed to exist before anything can be
drawn, which is what prevents a missing-model exception or a one-frame invisible entity on the very
first spawn.

Texture identifiers are resolved **once** into an immutable `EnumMap` in a static initialiser, not
per frame. Combined with `getOrDefault` falling back to the classic texture, a variant value can never
resolve to a missing file mid-render, so there is no white/black texture flash after spawn.

---

## Animation

Animation state is computed **server-side** by the shared `AnimationResolver` and synced to clients
through a tracked data field, so every observer sees the same state as the rider — no client-side
guessing, no desync.

`PegasusEntityModel.setAngles` then eases each angle toward its target with a smoothing function
rather than assigning it directly. Together with the resolver's minimum dwell times and speed
hysteresis, that is what prevents snapping between states and wing flicker at threshold speeds.

The wing is two segments (main + tip) and the tip lags the main wing, so a beat reads as a flexible
membrane instead of a rigid plank.

States: idle, walk, run, wing flap, takeoff, flying, landing, eat/tame, hurt, death.

---

## Content registered

| Registry | Id |
| --- | --- |
| Entity type | `pegasus:pegasus` |
| Entity type | `pegasus:unicorn` |
| Item | `pegasus:classic_pegasus_spawn_egg` |
| Item | `pegasus:blue_eye_pegasus_spawn_egg` |
| Item | `pegasus:unicorn_spawn_egg` |

Eggs are vanilla `SpawnEggItem`s, so vanilla handles placement and behaviour; there is no custom item
data that could be malformed at spawn time. They appear in the creative **Spawn Eggs** tab when
`spawn-eggs.creative-tab` is `true`.

Vanilla commands work too:

```
/summon pegasus:pegasus
/summon pegasus:unicorn
/give @s pegasus:blue_eye_pegasus_spawn_egg
```

---

## Development

```bash
./gradlew :fabric:remapJar      # build the mod
./gradlew :fabric:runClient     # launch a dev client
./gradlew :fabric:runServer     # launch a dev server
```

`runClient` and `runServer` are provided by Loom. They were not exercised in this environment
(no display/interactive session available) — see the final report's limitations section.

Mappings are Yarn `1.21.1+build.3`. To move to another 1.21.x patch, change `minecraft_version`,
`yarn_mappings` and `fabric_api_version` in `gradle.properties` and recompile; mapping names do shift
between versions, so expect to fix a few symbols.

---

**Pegasus 1.21 — Created by Anirban &lt;3**

# Pegasus

**Created by Anirban &lt;3**

Tameable winged horses for Minecraft **Java Edition 1.21.1**, with permanent first-tamer ownership,
Unicorn breeding, Hay Bale sky spawning, saddle-gated flight and configurable spawn eggs.

Two separate builds are produced — Paper and Fabric are different platforms and do **not** share a jar.

| Platform | Artifact | Side |
| --- | --- | --- |
| Paper 1.21.1 | `paper/build/libs/Pegasus-Paper-1.0.0.jar` | Server only |
| Fabric 1.21.1 | `fabric/build/libs/Pegasus-Fabric-1.0.0.jar` | Client **and** server |

---

## Provenance and licensing

This is a **clean-room Java Edition implementation**. It was written from a written description of
observed gameplay behaviour. No code, model, texture, sound, animation, pack or asset from any
Bedrock add-on was copied, extracted, decompiled, reused or redistributed.

All artwork in this repository is original and generated procedurally by
[`assets/generate_textures.py`](assets/generate_textures.py), which draws every pixel from scratch —
no external image is read, traced or sampled. The project is MIT licensed (see [LICENSE](LICENSE)).

---

## Build

Requires a **JDK 21** toolchain. Gradle downloads everything else.

```bash
./gradlew :common:test          # shared logic unit tests
./gradlew :paper:jar            # Paper plugin jar
./gradlew :fabric:remapJar      # Fabric mod jar
./gradlew buildAll              # both platform artifacts
./gradlew verifyAll             # tests + both platform builds
```

Archives are configured for reproducible output (`preserveFileTimestamps = false`,
`reproducibleFileOrder = true`, fixed file permissions).

---

## Installation

**Paper** — drop `Pegasus-Paper-1.0.0.jar` into `plugins/`, start the server, then edit
`plugins/Pegasus/config.yml` and run `/pegasus reload`.

**Fabric** — install Fabric Loader ≥ 0.16.0 and **Fabric API** for 1.21.1, then place
`Pegasus-Fabric-1.0.0.jar` in `mods/` on **both** the server and every client. Config is written to
`config/pegasus.json` on first run.

Full details: [docs/installation.md](docs/installation.md).

---

## Behaviour reproduced from the reference description

These mechanics were requested as observed behaviour and are implemented as described.

### Hay Bale spawning above Y=175
Pegasus spawn naturally on platforms built from Hay Bales, high in the sky. The scanner requires the
platform to sit strictly **above Y=175** by default, to contain at least **128 Hay Bales**
(two stacks), and to be at least **90 % Hay Bale** across the sampled area. Attempts run every
**60 s** with a **10 %** chance, and are skipped when a Pegasus is already nearby.

> Every one of those numbers is a configurable default, **not** a measured fact from the reference.
> They were chosen as reasonable Java Edition values. See
> [docs/configuration.md](docs/configuration.md).

### Unicorn breeding into Pegasus
Two Unicorns can produce a Pegasus foal:

| Nether Stars given | Pegasus foal chance |
| --- | --- |
| Both parents | **100 %** |
| One parent | **50 %** |
| Neither parent | **0 %** (ordinary Unicorn foal) |

A **Nether Star** primes a Unicorn; a **Golden Carrot** then starts the actual breeding. Priming
expires after **5 minutes** and is **consumed** on breeding, so one star cannot be reused.

**Unicorn is implemented in this project** — it is not vanilla, and no external mod is required or
assumed. On Fabric it is a fully registered `pegasus:unicorn` entity with its own original model
(horn, no wings) and texture. On Paper it is a marked vanilla horse.

### Blue-eyed variant
Pegasus foals born from Unicorns use the **blue-eye** variant, which has its own original texture
and its own spawn egg.

### Taming, saddle-gated riding and flight
A Pegasus must be tamed before it can be ridden, and a **saddle** is required before mounting and
flying. Once saddled, the owner can take off and fly. Wing flapping is driven by a shared animation
state machine (see the platform note below).

---

## Original Java Edition design decisions

Everything in this section was **not** specified by the reference and is this project's own design.
It is called out separately so nothing here is mistaken for observed behaviour.

### First-tamer permanent ownership
The reference confirms taming but says nothing about ownership. This project adds:

- The **first player whose tame attempt succeeds** becomes the permanent owner.
- Owner **UUID** and **last-known name** are stored, and the name is refreshed when the owner
  renames their account.
- Ownership persists across server restarts, chunk unload/reload, entity unload/reload and world
  saves, because it lives in the entity's own persistent data (Paper `PersistentDataContainer`,
  Fabric entity NBT) which Minecraft saves alongside the entity.
- Concurrent tame attempts are **race-safe**: exactly one player wins.
- By default only the owner may ride, control, rename, breed/manage or use owner-only interactions.
- Administrators can override and can inspect, transfer, clear or recover ownership.

### Flight controls
The reference does not confirm controls. These are this project's choices:

| Input | Effect |
| --- | --- |
| **Jump** | Take off |
| **Look direction** | Steer; look up to climb, look down to dive |
| **Sneak** | Descend and land |
| **Sprint** | Fly faster (`flight.sprint-multiplier`) |

Steering is derived from the rider's look direction rather than key state. Paper 1.21.1 does not
expose per-tick client input (`Player#getCurrentInput()` arrived in a later version), so this keeps
both platforms behaving identically and needs no client mod on Paper.

### Tame item
**Golden Apple**, chosen as a deliberately costly but obtainable item. The reference does not
identify a tame item. Configurable via `taming.items`.

### Spawn eggs
Three eggs: classic Pegasus, blue-eyed Pegasus, and Unicorn. On Fabric they are real registered
`SpawnEggItem`s that appear in the creative Spawn Eggs tab. On Paper they are horse spawn eggs
carrying validated persistent variant data. Egg data is validated on use — hand-edited or
third-party data falls back to the classic variant rather than failing to spawn.

### Other additions
Configurable health, speeds, chances, cooldowns, worlds, messages and debug mode; colourised
startup output; admin commands; automated tests for the shared rules.

---

## Commands

```
/pegasus                          Show the credit banner
/pegasus info                     Plugin/mod status and key settings
/pegasus owner                    Inspect the owner of the Pegasus you are looking at
/pegasus transfer <player>        Transfer ownership
/pegasus clearowner               Clear ownership, making it tameable again
/pegasus giveegg <player> [variant]   Give a spawn egg
/pegasus summon [variant]         Summon a Pegasus
/pegasus reload                   Reload configuration
```

Variants: `classic`, `blue_eye`. Full permission nodes:
[docs/commands-and-permissions.md](docs/commands-and-permissions.md).

---

## Platform differences you should know about

This is the most important honest caveat in the project.

### Fabric renders a real animated Pegasus
Fabric registers genuine entity types, so it ships an original two-segment winged model with all ten
animation states — idle, walk, run, wing flap, takeoff, flying, landing, eat/tame, hurt, death.
Transitions are eased and speed thresholds use hysteresis, so states blend instead of snapping.
**No resource pack is required on Fabric.**

### Paper does not render wings
Paper's public API **cannot** register a new entity type or attach custom geometry to a vanilla
entity. A Pegasus on Paper is a vanilla horse carrying persistent data, so **it looks like a horse.**

A resource pack cannot fix this: retexturing the horse model would change *every* horse on the
server, not just Pegasus. Rather than ship a pack that breaks ordinary horses, the Paper module
instead makes the shared animation state **audible and visible without a pack** — wing-beat sounds
and feather particles on takeoff and during flight, and a landing thud — via `PegasusEffects`.

Gameplay (ownership, taming, breeding, spawning, flight, eggs, commands, persistence) is at full
parity between the two platforms. Only the custom model is Fabric-only. See
[docs/paper-resource-pack.md](docs/paper-resource-pack.md) for the full explanation and the options
if you want true custom visuals on Paper.

---

## Documentation

- [docs/installation.md](docs/installation.md)
- [docs/configuration.md](docs/configuration.md)
- [docs/commands-and-permissions.md](docs/commands-and-permissions.md)
- [docs/paper-resource-pack.md](docs/paper-resource-pack.md)
- [docs/fabric-setup.md](docs/fabric-setup.md)
- [docs/migration.md](docs/migration.md)
- [docs/troubleshooting.md](docs/troubleshooting.md)
- [CHANGELOG.md](CHANGELOG.md)

---

## Architecture

```
common/    Platform-neutral rules, no server API imports. Unit tested.
           OwnershipRecord/OwnershipService · BreedingRules/BreedingService
           SpawnPlatformRules · SpawnEggData · CooldownTracker
           config/{ConfigSource,MapConfigSource,ConfigValidator}
           animation/{AnimationState,AnimationResolver}
           Messages · Ansi · StartupReport · Branding

paper/     Paper 1.21.1 plugin. Vanilla horses + PersistentDataContainer.
fabric/    Fabric 1.21.1 mod. Registered entity types + client renderers.
assets/    Original texture generator.
docs/      Documentation.
```

Both platforms consume the *same* validated configuration model and the *same* ownership, breeding
and spawn-validation rules, so identical settings produce identical behaviour.

---

**Pegasus 1.21 — Created by Anirban &lt;3**

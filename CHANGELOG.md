# Changelog

**Pegasus — Created by Anirban &lt;3**

All notable changes to this project are documented here.

---

## 1.0.0 — Initial release

First release. Clean-room Java Edition implementation for Paper 1.21.1 and Fabric 1.21.1.

Because this is the first release there is no "ported" code in the usual sense: nothing was copied
from any prior codebase. The sections below separate mechanics that reproduce the reference
description from mechanics this project designed, and record the defects found and fixed while
building against the real APIs.

---

### Reference behaviour reproduced

Implemented from a written description of observed gameplay. No add-on code or assets were used.

- **Hay Bale sky spawning.** Pegasus spawn naturally on Hay Bale platforms above Y=175 (default),
  requiring at least 128 Hay Bales (two stacks) at 90 % purity across the sampled area, attempted
  every 60 s at a 10 % chance. All values configurable.
- **Unicorn breeding into Pegasus.** Two Unicorns can produce a Pegasus foal.
- **Nether Star chance ladder.** Both parents primed → 100 %; one parent primed → 50 %; neither → 0 %.
- **Golden Carrot trigger.** Priming with a Nether Star comes first; a Golden Carrot then starts the
  actual breeding.
- **Blue-eyed variant.** Foals born from Unicorns use the blue-eye variant, with its own texture and
  spawn egg.
- **Taming by interaction.** Right-click an untamed Pegasus to attempt taming.
- **Saddle-gated riding.** A Pegasus must be tamed, and then saddled, before it can be ridden.
- **Flight with wing flapping.** Once saddled, the owner can take off and fly; wing beats are driven
  by the shared animation state machine.

> Numeric defaults above are reasonable Java Edition values chosen by this project. They are **not**
> measurements taken from the reference material, and every one is configurable.

---

### New features (original Java Edition design)

Not specified by the reference. This project's own design decisions.

#### Ownership
- **First-tamer permanent ownership.** The first player whose tame attempt succeeds becomes the
  permanent owner.
- **Race-safe claiming.** Concurrent tame attempts resolve to exactly one owner, enforced atomically
  via `ConcurrentHashMap.computeIfAbsent`.
- **Persistence across everything.** Owner UUID and last-known name survive server restarts, chunk
  unload/reload, entity unload/reload and world saves, by living in the entity's own persistent data
  (Paper `PersistentDataContainer`, Fabric NBT) rather than a side file that could drift out of sync.
- **Versioned data** (`dataVersion = 1`) with legacy key aliases, so a future release can migrate
  safely.
- **Owner-only actions by default** — riding, controlling, renaming, breeding and management.
- **Name refresh.** The stored display name updates when an owner renames their account.
- **Administrator override** plus commands to inspect, transfer, clear and recover ownership.
- **Safe handling of offline, banned, deleted and renamed owners.**

#### Flight
- **Original control scheme:** jump to take off, look direction to steer (look up to climb, down to
  dive), sneak to descend and land, sprint to fly faster.
- Server-authoritative velocity, so all observers stay in sync in multiplayer.
- Configurable horizontal/vertical speed, sprint multiplier, fall-damage protection and takeoff
  cooldown.
- Hover floor so a level Pegasus glides rather than dropping.
- Terrain-clip recovery and fall-damage protection for both Pegasus and rider.

#### Animation
- Shared ten-state machine: idle, walk, run, wing flap, takeoff, flying, landing, eat/tame, hurt,
  death.
- **Anti-snap design:** per-state minimum dwell times plus separate enter/exit speed thresholds
  (hysteresis), so states cannot flip-flop on a per-tick basis.
- Fabric ships an original two-segment winged model where the wing tip lags the main wing; angles are
  eased toward targets rather than assigned.
- Animation state is computed server-side and synced via tracked data, so observers and the rider see
  the same state.

#### Content and configuration
- **Unicorn entity implemented in-project** — no external mod required or assumed. Original model
  (horn, no wings) and texture.
- **Three spawn eggs:** classic Pegasus, blue-eyed Pegasus, Unicorn. Creative-tab integration on
  Fabric; validated persistent variant data on Paper.
- **Configurable** health, speeds, tame items and chance, cooldowns, breeding items and window,
  spawn rules, worlds, egg permissions, messages and debug mode.
- **Fail-safe config validation:** out-of-range values are clamped, unparseable values fall back to
  defaults, and everything is reported as a warning. A broken config never prevents startup.
- **Colourised startup banner** honouring `NO_COLOR` and dumb terminals, with plain-text status
  markers so colour is never the only signal.
- **Commands:** `info`, `owner`, `transfer`, `clearowner`, `giveegg`, `summon`, `reload`, with tab
  completion and documented permission nodes.
- **Original procedurally generated assets** via `assets/generate_textures.py`.

---

### Bugs found and fixed during development

Found by compiling and testing against the real APIs rather than assuming signatures.

#### Wrong API assumptions, caught by verifying against the actual jars
1. **`Attribute.MAX_HEALTH` does not exist in Paper 1.21.1.** The enum constants are prefixed:
   `GENERIC_MAX_HEALTH`, `GENERIC_MOVEMENT_SPEED`. Fixed after inspecting the real `paper-api` jar.
2. **`CreatureSpawnEvent.getReason()` does not exist.** The method is `getSpawnReason()`.
3. **`Player#getCurrentInput()` is not available in Paper 1.21.1.** It arrived in a later version.
   The flight control scheme was designed around look-direction steering instead of assuming a
   per-tick input API that would have failed at runtime.
4. **`Nameable.setCustomName(String)` is deprecated on Paper.** Switched to the Adventure
   `customName(Component)` API.
5. **`FabricItemSettings` no longer exists** in Fabric API 0.102.1. Replaced with
   `new Item.Settings()`.
6. **`AbstractHorseEntity.playAngrySound()` is `public`, not `protected`,** so the intended override
   was an illegal reduction in visibility. Removed.

#### Build system defects
7. **Fabric Loom 1.7.4 is incompatible with Gradle 8.14.5** — it calls `Problems.forNamespace`,
   which has been removed. Upgraded to Loom 1.13.6.
8. **Loom cannot run under `RepositoriesMode.FAIL_ON_PROJECT_REPOS`.** It must add its own project
   repositories. Restructured `settings.gradle` to declare repositories per project instead.

#### Correctness and robustness issues designed out
9. **Ownership race condition.** A naive read-then-write check lets two simultaneous tame attempts
   both succeed. Replaced with an atomic `computeIfAbsent` claim; covered by a concurrent test.
10. **Unbounded state growth (memory leak).** Per-entity maps for breeding priming, cooldowns, flight
    and effects would grow for the lifetime of the server. All are now pruned on expiry, on
    death/dismount, and by a periodic housekeeping pass against live entity ids.
11. **Nether Star reuse.** Priming is consumed on breeding, so one star cannot produce repeated
    Pegasus foals.
12. **Corrupt ownership locking a Pegasus forever.** A malformed owner UUID previously would have
    made an entity permanently unusable. Invalid records are now discarded and the keys erased, so it
    becomes tameable again, with a warning logged.
13. **Divide-by-zero in platform validation.** An empty block scan is explicitly rejected rather than
    computing a ratio against zero.
14. **NaN velocity corrupting entity position.** Flight vectors are checked with `Double.isFinite`
    before assignment; a non-finite value is dropped instead of permanently corrupting the entity.
15. **Health above maximum.** Attributes are applied so current health is clamped to the new maximum,
    never leaving the entity in an invalid state.
16. **Off-hand double-firing.** Interaction handlers ignore the off-hand copy of the event, so taming
    and priming cannot consume two items from one right-click.
17. **Chunk loading from scheduled tasks.** Both spawn scanners check `isChunkLoaded` and never force
    loading or generation, so a background scan cannot stall the server or generate terrain.
18. **Unvalidated spawn-egg data.** Item data is treated as untrusted: an unknown variant falls back
    to classic rather than throwing during spawn.
19. **Animation snapping and wing flicker.** Addressed with minimum dwell times, speed hysteresis and
    eased angle transitions.
20. **Texture flash on first spawn.** Renderer texture identifiers are resolved once into an
    immutable map with a safe fallback, and model layers are registered before renderers.
21. **Console spam.** Debug logging is lazy (`Supplier<String>`), in-flight wing effects are
    throttled, and normal startup output is bounded to one line per subsystem.
22. **Egg-to-entity attribution.** Egg variant is bridged from interaction to spawn through a
    short-lived (2 s), position-keyed map that is actively expired, so a cancelled spawn cannot leak
    or mis-tag a later entity.

---

### Known limitations

- **Paper does not render a custom Pegasus model.** Paper's public API cannot register an entity type
  or attach custom geometry to a vanilla entity, and a resource pack would change *every* horse on the
  server. Paper therefore renders a vanilla horse and expresses animation state through sound and
  particles instead. Gameplay is at full parity; only the model is Fabric-only. See
  `docs/paper-resource-pack.md`.
- **Fabric requires the mod on all clients.** Inherent to custom entity types; there is no
  server-only mode.
- **Paper and Fabric data are not interchangeable.** See `docs/migration.md`.
- **Fabric is version-sensitive.** Yarn mapping names shift between 1.21.x patches.
- **No custom sounds are shipped.** Effects reuse vanilla sound events. Original `.ogg` assets are a
  candidate for a future release.
- **`breeding.cooldown-seconds` is accepted and validated but not yet enforced** as a per-pair gate;
  vanilla love-mode cooldown currently governs breeding frequency.
- **`entity.baby-scale` and `spawn-eggs.creative-tab`** are validated but only partially wired
  (`creative-tab` applies on Fabric only; `baby-scale` relies on vanilla baby scaling).
- **Not runtime-tested on a live server.** See the final report for exactly what was and was not
  verified.

---

**Pegasus 1.21 — Created by Anirban &lt;3**

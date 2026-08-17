# Paper visuals and the resource-pack question

**Pegasus — Created by Anirban &lt;3**

**Short answer: no resource pack is shipped, and none is recommended. On Paper a Pegasus looks like
a vanilla horse.** This page explains exactly why, what you get instead, and what your options are
if you want true custom visuals.

---

## Why Paper cannot render a custom Pegasus

Paper's public API has no way to register a new entity type. Every entity a plugin spawns must be one
of Minecraft's existing types. A Pegasus on Paper is therefore a vanilla `Horse` carrying persistent
marker data.

That choice is deliberate and has real benefits — vanilla taming, saddling, mounting, inventory,
pathfinding and client synchronisation all work correctly and are far better tested than anything a
plugin could reimplement with NMS. The cost is that the client renders it with the vanilla horse
model.

### A resource pack does not solve this

The obvious idea is "ship a pack that retextures the horse." It does not work, because a resource
pack applies to an **entity type**, not to an individual entity. Replacing
`assets/minecraft/models/entity/horse` or the horse texture changes **every horse on the server** —
ordinary horses, other plugins' horses, horses in players' stables. You would trade "Pegasus has no
wings" for "all horses have wings," which is worse.

Minecraft also gives no per-entity model selector for living entities. `CustomModelData` works on
**items**, not mobs. Horse variant textures are chosen by the horse's own colour/marking data, which
cannot point at an arbitrary custom file.

So there is no honest way to ship a pack that gives wings to Pegasus only.

---

## What the Paper module does instead

The shared animation state machine still runs on Paper. `FlightController` resolves the same ten
states as Fabric (idle, walk, run, wing flap, takeoff, flying, landing, eat, hurt, death), and
`PegasusEffects` turns state **transitions** into feedback that needs no pack:

| State | Effect |
| --- | --- |
| Takeoff | Wing-beat sound, cloud burst at the hooves |
| Flying / wing flap | Repeating wing beat, throttled to ~620 ms, with drifting particles either side of the body |
| Landing | Landing thud, dust puff |
| Hurt | Horse hurt sound |
| Death | Horse death sound |

Effects fire only on transitions plus a throttled in-flight interval, so a long flight does not flood
clients with packets.

You get the *feel* of a flying winged horse — audible wing beats, visible feather drift — without
breaking every other horse on the server.

---

## Options if you require custom visuals on Paper

These are **not implemented** here. They are listed so you can make an informed decision.

### 1. Use the Fabric build
The Fabric module registers real entity types and ships an original animated winged model with all
ten states. If custom visuals matter most, Fabric is the correct platform for this feature. This is
the recommended path.

### 2. Display-entity overlay (moderate effort, no pack conflict for other horses)
Since 1.19.4, Paper can spawn `ItemDisplay` entities and mount them on a carrier. You would make the
horse invisible and mount an `ItemDisplay` whose item uses a custom model from a pack, then animate
it by updating its transformation each tick.

This is the standard modern technique and it **does** avoid affecting other horses, because the
custom model lives on an *item*, where `CustomModelData` is valid.

Tradeoffs to budget for: you must serve a resource pack; you take over hitbox/nameplate/shadow
behaviour; interpolated transforms need care to stay smooth for all viewers; and you are maintaining
a second entity per Pegasus. It is a substantial feature, which is why it is not in this release.

### 3. Global horse replacement (not recommended)
Ship a pack that replaces the horse model outright and accept that all horses become winged. Only
sensible on a server where that is the intended aesthetic.

---

## Serving a resource pack, if you add one

For completeness, if you pursue option 2:

```properties
# server.properties
resource-pack=https://example.com/pegasus-pack.zip
resource-pack-sha1=<sha1 of the zip>
resource-pack-required=false
```

Compute the hash with `sha1sum pegasus-pack.zip`. Keep `resource-pack-required=false` so players
without the pack can still play — the plugin's gameplay does not depend on it.

Pack format for 1.21.1 is `34`:

```json
{ "pack": { "pack_format": 34, "description": "Pegasus — Created by Anirban <3" } }
```

### Graceful fallback
Whatever you build, keep it optional. The Paper module never requires a pack: ownership, taming,
breeding, spawning, flight, eggs and commands all work with vanilla assets, and a player who
declines the pack sees a normal horse with correct behaviour rather than a broken entity.

---

## Fabric, for contrast

No pack is needed. Textures ship inside the mod jar at
`assets/pegasus/textures/entity/pegasus/{classic,blue_eye}.png` and
`assets/pegasus/textures/entity/unicorn/unicorn.png`, and are loaded by the mod's own renderers.
Because the mod is required on clients anyway, there is nothing extra to distribute.

See [fabric-setup.md](fabric-setup.md).

---

**Pegasus 1.21 — Created by Anirban &lt;3**

# Troubleshooting

**Pegasus — Created by Anirban &lt;3**

Turn on `debug: true` (Paper `config.yml`) or `"debug": true` (Fabric `pegasus.json`), then
`/pegasus reload`. Debug mode reports platform scans, tame claims, egg spawns and foal chances.
Turn it back off afterwards.

---

## Pegasus never spawn naturally

Work through these in order:

1. **`allowed-worlds` does not match your world.** The most common cause. Default is `world`. Paper
   uses world folder names; Fabric uses dimension ids (`minecraft:overworld`, bare `overworld` also
   accepted). Check `/pegasus info`.
2. **You are not high enough.** The platform must be **strictly above** Y=175 by default, and the
   scan only searches ±12 blocks vertically around a player. Stand on or near the platform.
3. **Platform too small.** 128 Hay Bales minimum, and at least 90 % of the sampled square must be
   Hay Bale. A 12×12 solid platform (144 blocks) passes comfortably; a ring or a scattered pattern
   does not.
4. **Chance and interval.** Defaults are 10 % every 60 s, so expect roughly one spawn per 10 minutes
   of standing there. Raise `spawn-chance-percent` to 100 while testing.
5. **`max-nearby-pegasus`.** Defaults to 1 within 48 blocks. One already there blocks more.
6. **No headroom.** Two air blocks are needed above the platform surface.
7. **Invalid `required-block`.** A typo logs a warning at startup and disables spawning.

With `debug: true` you get an explicit reason per attempt, for example:

```
[..] platform rejected at world 120,180,-64: not-enough-required-blocks (96/169 blocks)
[..] valid platform at world 120,180,-64 (144/169 blocks)
```

---

## Taming does nothing

- You must hold a **Golden Apple** (`taming.items`). Other items are ignored silently.
- Default success chance is **35 %** per attempt — keep trying.
- There is a **3 second** cooldown per player between attempts.
- If it is already owned you get "This Pegasus already belongs to …".
- Paper: you need `pegasus.tame` (default: everyone).

## I cannot ride my own Pegasus

- **It needs a saddle.** Right-click with a Saddle first. This is the single most common cause.
- Confirm you own it with `/pegasus owner`.
- Paper: you need `pegasus.ride`.
- Set `taming.require-saddle-to-ride: false` if you do not want the saddle gate.

## Someone else's Pegasus refuses me

Working as designed. Only the owner may ride, rename, breed or manage it. An admin
(`pegasus.admin`, or op level 2 on Fabric) can override, or transfer it with `/pegasus transfer`.

---

## Flight problems

**It will not take off.** Press **jump** while mounted. There is a 750 ms cooldown between takeoffs.
The Pegasus must be saddled and you must be the owner.

**It sinks slowly instead of flying level.** That is intentional — a level Pegasus glides with a
gentle hover floor. Look slightly upward to hold or gain altitude.

**It descends when I do not want it to.** Sneak triggers descent. Release sneak.

**I got stuck in a block.** The flight controller detects a solid block at the Pegasus position and
nudges it up one block. If it persists, dismount and remount. Report the coordinates if reproducible.

**I took fall damage.** `flight.prevent-fall-damage` defaults to `true` and covers both the Pegasus
and its rider while flying. If you dismount mid-air well after the Pegasus left flight state, normal
fall rules apply.

---

## Breeding never produces a Pegasus

The order matters:

1. Give **each** Unicorn a **Nether Star** (right-click). You should see "The Unicorn absorbs the
   Nether Star's light."
2. **Then** feed both a **Golden Carrot** to start breeding.

Common mistakes:

- **Golden Carrot first.** Priming must come first; a carrot alone gives an ordinary foal.
- **Priming expired.** It lasts 5 minutes by default.
- **Priming already consumed.** It is consumed on breeding by design, so a single star cannot be
  reused. Prime again.
- **Only one parent primed.** That is a 50 % chance, so failures are expected.
- **Not both Unicorns.** Both parents must be Unicorns.
- Paper: you need `pegasus.breed`.

Priming is deliberately **not** saved across a restart.

---

## Ownership issues

**Owner is banned, deleted or gone.** Ownership persists on purpose. An admin runs
`/pegasus clearowner` (making it tameable again) or `/pegasus transfer <player>`.

**Owner changed their name.** UUID matching still works; the stored name updates on the next
interaction.

**"Discarded invalid Pegasus ownership data".** Corrupt owner data was found and cleared, and the
Pegasus is tameable again. This is the intended safe behaviour — the alternative is a Pegasus locked
to an owner who cannot exist.

**Two players tamed at once.** Exactly one wins. This is enforced atomically and covered by tests.

---

## Fabric-specific

**Clients cannot join.** Every player needs the mod plus Fabric API. A vanilla client cannot resolve
`pegasus:pegasus`. There is no server-only mode.

**Entity is invisible or untextured.** Confirm the client has the mod (not just the server) and that
Fabric API is present. Textures ship inside the jar; no resource pack is involved.

**Crash on startup mentioning mappings or a missing symbol.** You are on a different 1.21.x patch
than 1.21.1. Update `minecraft_version`, `yarn_mappings` and `fabric_api_version` in
`gradle.properties` and rebuild.

## Paper-specific

**My Pegasus looks like an ordinary horse.** Expected. Paper cannot render a custom entity model.
See [paper-resource-pack.md](paper-resource-pack.md) for the full reasoning and your options.

**Another plugin's horses are being treated as Pegasus.** They should not be — identification uses a
namespaced persistent-data key. If it happens, that plugin is copying our persistent data.

**Config edits do nothing.** Run `/pegasus reload` and read the warnings. Out-of-range values are
clamped and reported rather than applied.

---

## Startup warnings

Yellow `[WARN]` lines are informational; the server still starts.

| Warning | Meaning |
| --- | --- |
| `… is outside x..y; clamped to z` | Value out of range; clamped. |
| `… is not a number; using default` | Unparseable value; default used. |
| `taming.items entry '…' is not a valid item` | Unknown item name; that entry never matches. |
| `natural-spawning.required-block '…' is not a valid block` | Natural spawning is disabled. |
| `messages.… is not a known message key` | Typo in a message key; ignored. |

---

## Reporting a bug

Include: platform and exact version, Pegasus version, `/pegasus info` output, your config file, the
startup banner, relevant log lines with `debug: true`, and reproduction steps.

---

**Pegasus 1.21 — Created by Anirban &lt;3**

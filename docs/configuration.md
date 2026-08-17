# Configuration

**Pegasus — Created by Anirban &lt;3**

| Platform | File | Format |
| --- | --- | --- |
| Paper | `plugins/Pegasus/config.yml` | YAML |
| Fabric | `config/pegasus.json` | JSON |

Both files are read into the **same** shared validator, so identical settings produce identical
behaviour on both platforms. Reload at runtime with `/pegasus reload`.

---

## Validation philosophy

**A broken config never stops the server.** Every value is optional. Anything missing, unparseable
or out of range is replaced with the documented default or clamped into range, and reported as a
yellow `[WARN]` line at startup. Only a structurally impossible configuration would be a failure.

Concretely:

| Input | Result |
| --- | --- |
| `taming.chance-percent: 5000` | Clamped to `100`, warning printed |
| `taming.chance-percent: -20` | Clamped to `0`, warning printed |
| `entity.max-health: "very healthy"` | Falls back to `30.0`, warning printed |
| `flight.horizontal-speed: NaN` | Falls back to `0.62`, warning printed |
| `taming.items: []` | Falls back to `[GOLDEN_APPLE]` — never leaves a Pegasus untameable |
| `messages.not-a-real-key: "..."` | Reported as unknown and ignored |
| `natural-spawning.required-block: NOT_A_BLOCK` | Natural spawning is skipped, warning printed |

This behaviour is covered by unit tests in `ConfigValidatorTest`.

---

## Reference

Ranges are enforced. Values outside them are clamped, not rejected.

### `debug`
`false` — print detailed diagnostics: platform scan results, tame claims, egg spawns, foal chances.
Leave off in production; normal output is intentionally quiet.

### `entity`
| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `max-health` | `30.0` | 1–1024 | Half-hearts. 30 = 15 hearts. |
| `movement-speed` | `0.2825` | 0.01–2.0 | Ground speed attribute. Vanilla horses sit near 0.2. |
| `baby-scale` | `0.5` | 0.1–1.0 | Relative foal size. |

### `taming`
| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `items` | `[GOLDEN_APPLE]` | non-empty | Items that count as a tame attempt. **Original design choice.** |
| `chance-percent` | `35` | 0–100 | Success chance per attempt. |
| `cooldown-seconds` | `3` | 0–3600 | Minimum delay between attempts per player. |
| `require-saddle-to-ride` | `true` | — | Require a saddle before mounting. |

On Fabric, item names accept `golden_apple`, `minecraft:golden_apple` or `GOLDEN_APPLE`.

### `flight`
| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `horizontal-speed` | `0.62` | 0.05–3.0 | Forward speed, blocks per tick. |
| `vertical-speed` | `0.42` | 0.05–3.0 | Climb and dive rate, blocks per tick. |
| `sprint-multiplier` | `1.5` | 1.0–4.0 | Speed multiplier while sprinting. |
| `prevent-fall-damage` | `true` | — | Cancel fall damage for a flying Pegasus **and its rider**. |
| `takeoff-cooldown-millis` | `750` | 0–60000 | Minimum delay between takeoffs. |

Values above roughly `1.5` horizontal speed are fast enough to outrun chunk loading on some servers.

### `natural-spawning`
| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `enabled` | `true` | — | Master switch. |
| `minimum-y-level` | `175` | -64–319 | Platform must be **strictly above** this Y. |
| `required-block` | `HAY_BLOCK` | any block | Block the platform is built from. |
| `minimum-platform-blocks` | `128` | 1–4096 | Minimum required blocks. 128 = two stacks. |
| `required-block-ratio` | `0.9` | 0.1–1.0 | Fraction of the sampled area that must match. |
| `check-interval-seconds` | `60` | 5–3600 | Seconds between attempts. Lower costs more CPU. |
| `spawn-chance-percent` | `10` | 0–100 | Chance a valid platform produces a Pegasus. |
| `max-nearby-pegasus` | `1` | 0–64 | Skip if this many are already nearby. |
| `nearby-radius` | `48` | 4–256 | Radius for the nearby check. |
| `allowed-worlds` | `[world]` | list | Worlds where spawning may occur. **Empty list = every world.** |

> These defaults are **reasonable Java Edition values chosen by this project**, not measurements
> from the reference material.

World names differ by platform. Paper uses folder names (`world`, `world_nether`). Fabric uses
dimension ids (`minecraft:overworld`), and also accepts the bare path (`overworld`).

**Cost control.** The scanner only inspects already-loaded chunks and never forces chunk loading or
generation. It samples a bounded square around each player and stops at the first valid platform per
player per pass, so cost does not grow with world or build size.

### `breeding`
| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `preparation-item` | `NETHER_STAR` | any item | Primes a Unicorn. |
| `trigger-item` | `GOLDEN_CARROT` | any item | Starts the breeding. |
| `preparation-window-seconds` | `300` | 1–86400 | How long priming lasts. |
| `both-prepared-chance-percent` | `100` | 0–100 | Pegasus chance when **both** parents primed. |
| `one-prepared-chance-percent` | `50` | 0–100 | Pegasus chance when **one** parent primed. |
| `cooldown-seconds` | `300` | 0–86400 | Reserved per-pair cooldown. |

Priming is **transient and not persisted**. It expires, and it is consumed on breeding so one Nether
Star cannot be reused. Persisting it would let players bank preparations across restarts.

### `spawn-eggs`
| Key | Default | Meaning |
| --- | --- | --- |
| `require-permission` | `false` | Require `pegasus.egg.<variant>` to use an egg (Paper). |
| `creative-tab` | `true` | Show eggs in the creative Spawn Eggs tab (Fabric). |
| `display-name` | `{variant} Spawn Egg` | `{variant}` is replaced with the display name (Paper). |
| `lore` | `[Pegasus — Created by Anirban <3]` | Item lore lines (Paper). |

### `messages`
Override any user-facing string. Unknown keys are reported and ignored. Available placeholders:
`{owner}`, `{uuid}`, `{player}`, `{variant}`.

Keys: `tame-success`, `tame-failed`, `already-owned`, `not-owner`, `needs-saddle`, `owner-info`,
`owner-none`, `transfer-done`, `owner-cleared`, `no-permission`, `reloaded`, `egg-given`,
`unicorn-prepared`, `unicorn-not-prepared`, `breeding-success`, `breeding-failed`.

---

## Reload behaviour

`/pegasus reload` re-reads the file, re-validates it, prints any warnings and pushes new values into
the live services. The natural-spawn task is restarted so a changed interval takes effect
immediately. Existing entities keep their persisted ownership and variant; changed attribute values
apply to newly spawned Pegasus.

---

**Pegasus 1.21 — Created by Anirban &lt;3**

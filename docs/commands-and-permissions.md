# Commands and permissions

**Pegasus — Created by Anirban &lt;3**

Command syntax is identical on both platforms. Permissions differ because Fabric has no permission
plugin API: Paper uses named nodes, Fabric uses vanilla operator levels.

---

## Commands

Alias on Paper: `/peg`.

| Command | Description | Requires looking at a Pegasus |
| --- | --- | --- |
| `/pegasus` | Show the credit banner and subcommand list | No |
| `/pegasus info` | Status, spawn settings, tame chance, tracked flights | No |
| `/pegasus owner` | Show owner name and UUID | **Yes** |
| `/pegasus transfer <player>` | Transfer ownership to another player | **Yes** |
| `/pegasus clearowner` | Remove ownership so it can be tamed again | **Yes** |
| `/pegasus giveegg <player> [variant]` | Give a spawn egg | No |
| `/pegasus summon [variant]` | Summon a Pegasus at your position | No |
| `/pegasus reload` | Re-read and re-validate configuration | No |

`[variant]` accepts `classic` or `blue_eye`. It is optional and defaults to `classic`.
Both `blue_eye` and `blue-eye` are accepted. An unknown variant is rejected with the valid list
rather than silently substituted.

"Requires looking at a Pegasus" means a ray-trace up to **12 blocks** from your eyes must hit one.
If it does not, the command tells you so instead of acting on the wrong entity.

---

## Paper permissions

| Node | Default | Grants |
| --- | --- | --- |
| `pegasus.*` | op | Everything below |
| `pegasus.admin` | op | **Override all ownership checks**; implies every subcommand |
| `pegasus.tame` | all | Attempt to tame |
| `pegasus.ride` | all | Ride a Pegasus you own |
| `pegasus.breed` | all | Prime and breed Unicorns |
| `pegasus.egg.*` | op | Every spawn egg variant |
| `pegasus.egg.classic` | op | Classic spawn egg |
| `pegasus.egg.blue_eye` | op | Blue-eyed spawn egg |
| `pegasus.command.*` | op | Every subcommand |
| `pegasus.command.info` | all | `/pegasus info` |
| `pegasus.command.owner` | all | `/pegasus owner` |
| `pegasus.command.transfer` | all | `/pegasus transfer` (still owner-gated unless admin) |
| `pegasus.command.clearowner` | op | `/pegasus clearowner` |
| `pegasus.command.giveegg` | op | `/pegasus giveegg` |
| `pegasus.command.summon` | op | `/pegasus summon` |
| `pegasus.command.reload` | op | `/pegasus reload` |

Two separate gates apply, and both must pass:

1. **Command permission** — may you run the subcommand at all.
2. **Ownership** — may you act on *this* Pegasus.

So a player with `pegasus.command.transfer` can transfer their **own** Pegasus but not someone
else's. `pegasus.admin` satisfies the ownership gate for any Pegasus.

`pegasus.egg.<variant>` is only enforced when `spawn-eggs.require-permission: true`.

---

## Fabric permissions

Fabric has no permission API, so operator level is used:

| Action | Required level |
| --- | --- |
| `/pegasus`, `/pegasus info`, `/pegasus owner` | 0 (everyone) |
| `/pegasus transfer` | 0, but owner-gated; level 2 overrides ownership |
| `/pegasus clearowner`, `giveegg`, `summon`, `reload` | 2 |
| Ownership override on interaction | 2 |

Grant with `/op <player>` or set `op-permission-level` in `server.properties`.
For finer control, use a Fabric permissions mod; the level checks are plain
`ServerCommandSource#hasPermissionLevel` calls.

---

## Edge cases

| Situation | Behaviour |
| --- | --- |
| Owner is **offline** | Ownership is unchanged and still enforced. Non-owners are refused. |
| Owner is **banned or deleted** | Ownership persists. An admin uses `/pegasus clearowner` or `/pegasus transfer` to recover it. |
| Owner **renamed** their account | UUID match still works; the stored display name updates on next interaction. |
| Pegasus **dies** | Cached ownership, flight and effect state are released. |
| Ownership data is **malformed** | Discarded on load and the Pegasus becomes tameable again, rather than being locked to an owner who cannot exist. A warning is logged. |
| **Two players tame simultaneously** | Exactly one becomes owner; the other is told who owns it. |
| Transfer target is **offline** | Paper accepts offline players who have joined before. Fabric requires the target to be online. |

---

**Pegasus 1.21 — Created by Anirban &lt;3**

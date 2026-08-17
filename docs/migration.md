# Migration notes

**Pegasus — Created by Anirban &lt;3**

---

## Migrating from an earlier Pegasus release

**There is no earlier Java Edition release of this project.** Version 1.0.0 is the first, so there is
no data to migrate from.

If you are arriving from a *Bedrock* add-on with similar mechanics, none of its data is readable here.
This is a clean-room Java Edition implementation with a different data model on a different edition;
worlds, entities and packs do not transfer. Start fresh.

---

## Forward compatibility, built in now

Ownership data is versioned from day one so a future release can migrate it safely.

Stored per entity:

| Field | Paper key (PDC) | Fabric key (NBT) |
| --- | --- | --- |
| Owner UUID | `pegasus:owner_uuid` | `PegasusOwnerUuid` |
| Owner name | `pegasus:owner_name` | `PegasusOwnerName` |
| Data version | `pegasus:data_version` | `PegasusDataVersion` |
| Variant | `pegasus:variant` | `PegasusVariant` |

Current `dataVersion` is **1**.

`OwnershipRecord.deserialize` already tolerates:

- **A missing version field** — treated as version 1 and upgraded in place on the next write.
- **Legacy key spellings** — `ownerUuid`, `owner`, `ownerName` and `lastKnownOwner` are read as
  aliases of the current names.
- **A malformed or absent UUID** — the record is rejected and the Pegasus becomes tameable again,
  rather than being permanently locked to an owner that cannot be resolved.
- **A missing or blank owner name** — normalised to `Unknown`, and refreshed the next time the real
  owner interacts.

This is covered by `OwnershipServiceTest.persistenceRoundTripAndMalformedLegacyDataAreSafe`.

Because ownership lives in the entity's own persistent data rather than a side file, there is no
separate database that can drift out of sync with the world, and no migration step needed for chunk
or world format changes.

---

## Moving between platforms

**Paper and Fabric data are not interchangeable.**

| From | To | What happens |
| --- | --- | --- |
| Paper | Fabric | Paper Pegasus are vanilla horses with extra data. Fabric expects `pegasus:pegasus` entities. They stay ordinary horses; ownership is not carried over. |
| Fabric | Paper | Fabric Pegasus are custom entity types Paper cannot load. **They will be removed from the world.** |

**Back up your world before switching platforms.** There is no supported converter. If you need one,
the honest approach is a purpose-written datafixer, which is out of scope here.

Configuration *does* port cleanly in spirit — both platforms use the same keys and the same validator.
You will need to translate the file format (YAML ↔ JSON) and adjust two things:

- `natural-spawning.allowed-worlds`: Paper uses folder names (`world`), Fabric uses dimension ids
  (`minecraft:overworld`).
- Item and block names: Paper uses `HAY_BLOCK`/`GOLDEN_APPLE`; Fabric accepts those as well as
  `minecraft:hay_block`/`golden_apple`.

---

## Changing Minecraft version

This release targets **1.21.1** specifically.

**Paper** is generally tolerant across 1.21.x because `api-version: '1.21'` and the plugin uses only
public API — no NMS. Moving to another 1.21.x patch will usually just work; bump
`paper_api_version` and rebuild to be certain.

**Fabric** is version-sensitive because Yarn mapping names shift between versions. To retarget, edit
`gradle.properties`:

```properties
minecraft_version=1.21.1
yarn_mappings=1.21.1+build.3
fabric_api_version=0.102.1+1.21.1
```

Then recompile and fix any symbols the compiler flags. Known moving parts across the 1.21 line
include `Item.Settings` gaining a required registry key in later versions, and attribute constants
losing the `GENERIC_` prefix. Expect a small, mechanical set of fixes rather than a rewrite —
all platform-specific code is confined to the `paper/` and `fabric/` modules, and `common/` imports
no game API at all.

---

**Pegasus 1.21 — Created by Anirban &lt;3**

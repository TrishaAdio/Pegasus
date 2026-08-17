/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.paper;

import dev.anirban.pegasus.common.OwnershipRecord;
import dev.anirban.pegasus.common.OwnershipService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Bridges the shared {@link OwnershipService} to durable Paper storage.
 *
 * <p>The entity's own {@link PersistentDataContainer} is the source of truth. Because Minecraft
 * saves that container with the entity, ownership survives chunk unload, entity reload, world save,
 * restart and cross-world teleports without a separate database that could drift out of sync.
 * The in-memory service is a cache plus the atomic gate for concurrent tame attempts.
 */
public final class PaperOwnershipStore {
    private final OwnershipService service = new OwnershipService();
    private final PegasusKeys keys;

    public PaperOwnershipStore(PegasusKeys keys) {
        this.keys = keys;
    }

    /**
     * Attempts to claim an unowned Pegasus.
     *
     * <p>Runs on the server thread (Bukkit events are single-threaded), and the underlying
     * {@code computeIfAbsent} makes the decision atomic regardless. Persisted immediately so a
     * crash right after taming cannot lose the claim.
     *
     * @return true when this player became the permanent owner
     */
    public boolean tryClaim(Entity entity, Player player) {
        // Re-read persisted data first: the entity may have loaded after the cache was built.
        hydrate(entity);
        OwnershipService.ClaimResult result =
                service.claimFirstTamer(entity.getUniqueId(), player.getUniqueId(), player.getName());
        if (result.claimedByCaller()) {
            persist(entity, result.owner());
        }
        return result.claimedByCaller();
    }

    public Optional<OwnershipRecord> owner(Entity entity) {
        hydrate(entity);
        return service.ownerOf(entity.getUniqueId());
    }

    public boolean canManage(Entity entity, UUID actor, boolean administratorOverride) {
        hydrate(entity);
        return service.canManage(entity.getUniqueId(), actor, administratorOverride);
    }

    public boolean transfer(Entity entity, UUID actor, boolean administratorOverride,
                            UUID recipient, String recipientName) {
        hydrate(entity);
        boolean changed = service.transfer(entity.getUniqueId(), actor, administratorOverride, recipient, recipientName);
        if (changed) {
            service.ownerOf(entity.getUniqueId()).ifPresent(record -> persist(entity, record));
        }
        return changed;
    }

    public boolean clear(Entity entity, UUID actor, boolean administratorOverride) {
        hydrate(entity);
        boolean changed = service.clear(entity.getUniqueId(), actor, administratorOverride);
        if (changed) {
            erase(entity);
        }
        return changed;
    }

    /** Keeps the stored display name current when a player changes their username. */
    public void refreshName(Entity entity, Player player) {
        owner(entity).ifPresent(record -> {
            if (record.ownerId().equals(player.getUniqueId()) && !record.lastKnownName().equals(player.getName())) {
                OwnershipRecord updated = OwnershipRecord.of(player.getUniqueId(), player.getName());
                service.restore(entity.getUniqueId(), updated);
                persist(entity, updated);
            }
        });
    }

    /** Drops cached state when a Pegasus dies so the map cannot grow across the server's lifetime. */
    public void forget(UUID entityId) {
        service.removeForDeath(entityId);
    }

    /**
     * Loads persisted ownership into the cache.
     *
     * <p>Malformed or partially written data is discarded rather than propagated, and a legacy
     * record missing its version is upgraded in place on next write.
     */
    public void hydrate(Entity entity) {
        UUID id = entity.getUniqueId();
        if (service.ownerOf(id).isPresent()) {
            return;
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String storedUuid = data.get(keys.ownerUuid(), PersistentDataType.STRING);
        if (storedUuid == null || storedUuid.isBlank()) {
            return;
        }
        Map<String, String> raw = new HashMap<>();
        raw.put("owner-uuid", storedUuid);
        raw.put("owner-name", data.get(keys.ownerName(), PersistentDataType.STRING));
        Integer version = data.get(keys.dataVersion(), PersistentDataType.INTEGER);
        if (version != null) {
            raw.put("version", Integer.toString(version));
        }
        OwnershipRecord.deserialize(raw).ifPresentOrElse(
                record -> service.restore(id, record),
                // Corrupt UUID: remove the bad keys so the Pegasus becomes tameable again
                // instead of being permanently locked to an owner who cannot exist.
                () -> erase(entity));
    }

    private void persist(Entity entity, OwnershipRecord record) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(keys.ownerUuid(), PersistentDataType.STRING, record.ownerId().toString());
        data.set(keys.ownerName(), PersistentDataType.STRING, record.lastKnownName());
        data.set(keys.dataVersion(), PersistentDataType.INTEGER, OwnershipRecord.CURRENT_VERSION);
    }

    private void erase(Entity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.remove(keys.ownerUuid());
        data.remove(keys.ownerName());
    }
}

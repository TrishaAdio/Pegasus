/* Pegasus Java Edition — Created by Anirban <3 */
package dev.anirban.pegasus.common;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Atomic ownership authority. Platform event handlers must call this on the server thread;
 * computeIfAbsent additionally guarantees correctness if two tame events arrive together.
 */
public final class OwnershipService {
    private final ConcurrentHashMap<UUID, OwnershipRecord> owners = new ConcurrentHashMap<>();

    public ClaimResult claimFirstTamer(UUID pegasusId, UUID playerId, String playerName) {
        OwnershipRecord candidate = OwnershipRecord.of(playerId, playerName);
        OwnershipRecord resolved = owners.computeIfAbsent(requireId(pegasusId), ignored -> candidate);
        return new ClaimResult(resolved, resolved.ownerId().equals(playerId));
    }

    public Optional<OwnershipRecord> ownerOf(UUID pegasusId) {
        return Optional.ofNullable(owners.get(requireId(pegasusId)));
    }

    public boolean canManage(UUID pegasusId, UUID actorId, boolean administratorOverride) {
        if (administratorOverride) return true;
        OwnershipRecord owner = owners.get(requireId(pegasusId));
        return owner != null && owner.ownerId().equals(actorId);
    }

    public boolean transfer(UUID pegasusId, UUID actorId, boolean administratorOverride,
                            UUID recipientId, String recipientName) {
        if (!canManage(pegasusId, actorId, administratorOverride)) return false;
        owners.put(requireId(pegasusId), OwnershipRecord.of(recipientId, recipientName));
        return true;
    }

    public boolean clear(UUID pegasusId, UUID actorId, boolean administratorOverride) {
        if (!canManage(pegasusId, actorId, administratorOverride)) return false;
        return owners.remove(requireId(pegasusId)) != null;
    }

    public void restore(UUID pegasusId, OwnershipRecord record) {
        owners.put(requireId(pegasusId), record);
    }

    public Optional<OwnershipRecord> removeForDeath(UUID pegasusId) {
        return Optional.ofNullable(owners.remove(requireId(pegasusId)));
    }

    private static UUID requireId(UUID value) {
        if (value == null) throw new IllegalArgumentException("Entity UUID cannot be null");
        return value;
    }

    public record ClaimResult(OwnershipRecord owner, boolean claimedByCaller) { }
}

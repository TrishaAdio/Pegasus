package dev.anirban.pegasus.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class OwnershipServiceTest {
    @Test void firstSuccessfulTamerPermanentlyClaimsOwnership() {
        OwnershipService service = new OwnershipService();
        UUID pegasus = UUID.randomUUID(); UUID alice = UUID.randomUUID(); UUID bob = UUID.randomUUID();
        assertTrue(service.claimFirstTamer(pegasus, alice, "Alice").claimedByCaller());
        assertFalse(service.claimFirstTamer(pegasus, bob, "Bob").claimedByCaller());
        assertEquals(alice, service.ownerOf(pegasus).orElseThrow().ownerId());
    }

    @Test void competingTameAttemptsHaveExactlyOneWinner() throws Exception {
        OwnershipService service = new OwnershipService(); UUID pegasus = UUID.randomUUID();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<OwnershipService.ClaimResult> a = pool.submit(() -> service.claimFirstTamer(pegasus, first, "A"));
            Future<OwnershipService.ClaimResult> b = pool.submit(() -> service.claimFirstTamer(pegasus, second, "B"));
            assertEquals(1, (a.get().claimedByCaller() ? 1 : 0) + (b.get().claimedByCaller() ? 1 : 0));
        }
    }

    @Test void onlyOwnerOrAdministratorCanManageAndTransfer() {
        OwnershipService service = new OwnershipService(); UUID pegasus = UUID.randomUUID();
        UUID owner = UUID.randomUUID(), stranger = UUID.randomUUID(), recipient = UUID.randomUUID();
        service.claimFirstTamer(pegasus, owner, "Owner");
        assertFalse(service.canManage(pegasus, stranger, false));
        assertTrue(service.canManage(pegasus, stranger, true));
        assertFalse(service.transfer(pegasus, stranger, false, recipient, "Recipient"));
        assertTrue(service.transfer(pegasus, stranger, true, recipient, "Recipient"));
        assertEquals(recipient, service.ownerOf(pegasus).orElseThrow().ownerId());
    }

    @Test void persistenceRoundTripAndMalformedLegacyDataAreSafe() {
        OwnershipRecord original = OwnershipRecord.of(UUID.randomUUID(), "Alice");
        assertEquals(original.ownerId(), OwnershipRecord.deserialize(original.serialize()).orElseThrow().ownerId());
        assertEquals("Alice", OwnershipRecord.deserialize(Map.of("ownerUuid", original.ownerId().toString(), "ownerName", "Alice"))
                .orElseThrow().lastKnownName());
        assertTrue(OwnershipRecord.deserialize(Map.of("owner-uuid", "not-a-uuid")).isEmpty());
        assertTrue(OwnershipRecord.deserialize(null).isEmpty());
    }
}

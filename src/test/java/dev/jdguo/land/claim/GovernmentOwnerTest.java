package dev.jdguo.land.claim;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GovernmentOwnerTest {
    @Test void onlyLiveAdministratorCanManageServerParcelsAndPrivateRightsAreUnchanged() {
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        assertTrue(GovernmentOwner.mayManage(player, GovernmentOwner.ID, true));
        assertFalse(GovernmentOwner.mayManage(player, GovernmentOwner.ID, false));
        assertFalse(GovernmentOwner.mayManage(GovernmentOwner.ID, GovernmentOwner.ID, false));
        assertTrue(GovernmentOwner.mayManage(player, player, false));
        assertFalse(GovernmentOwner.mayManage(player, other, true));
        assertFalse(GovernmentOwner.mayManage(player, null, true));
        assertEquals("cbd4b6ae-6633-3ba2-982f-700dc5d4fdb8", GovernmentOwner.ID.toString());
        assertEquals("服务器用地", GovernmentOwner.NAME);
    }
}

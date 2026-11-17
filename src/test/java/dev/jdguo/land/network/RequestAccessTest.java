package dev.jdguo.land.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestAccessTest {
    @Test void ordinaryPlayerNeverGainsManagementFromLiveConnection() {
        Object player = new Object(), handler = new Object();
        assertTrue(RequestAccess.current(player, player, handler, handler));
        assertFalse(RequestAccess.mayManage(true, false));
    }
    @Test void permissionIsReevaluatedAfterRevocation() {
        assertTrue(RequestAccess.mayManage(true, true));
        assertFalse(RequestAccess.mayManage(true, false));
    }
    @Test void stalePlayerOrHandlerCannotManageAfterReconnect() {
        Object player = new Object(), handler = new Object();
        assertFalse(RequestAccess.current(null, player, handler, handler));
        assertFalse(RequestAccess.current(new Object(), player, handler, handler));
        assertFalse(RequestAccess.current(player, player, new Object(), handler));
        assertFalse(RequestAccess.mayManage(false, true));
    }
    @Test void absentIdentityIsNotALiveConnection() {
        assertFalse(RequestAccess.current(null, null, null, null));
    }
}

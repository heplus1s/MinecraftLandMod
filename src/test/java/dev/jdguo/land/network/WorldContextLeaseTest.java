package dev.jdguo.land.network;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorldContextLeaseTest {
    @Test void rejectsReconnectAndWorldSwitchEvenWithSameDimensionName() {
        var lease = new WorldContextLease(); var connection = new Object(); var world = new Object();
        lease.bind(connection, world, "token", "minecraft:overworld", 100);
        assertTrue(lease.valid(connection, world, "token", 101));
        assertFalse(lease.valid(new Object(), world, "token", 101));
        assertFalse(lease.valid(connection, new Object(), "token", 101));
        assertFalse(lease.valid(connection, world, "old-token", 101));
        assertFalse(lease.valid(connection, world, "token", 10_000_000_100L));
        lease.clear(); assertFalse(lease.valid(connection, world, "token", 101));
    }
    @Test void delayedResponsesCannotBindNewConnectionOrWorld() {
        Object connection = new Object(), world = new Object();
        assertTrue(WorldContextLease.acceptsResponse("new", "new", connection, connection, world, world));
        assertFalse(WorldContextLease.acceptsResponse("new", "old", connection, connection, world, world));
        assertFalse(WorldContextLease.acceptsResponse("new", "new", connection, new Object(), world, world));
        assertFalse(WorldContextLease.acceptsResponse("new", "new", connection, connection, world, new Object()));
        assertFalse(WorldContextLease.acceptsResponse(null, "new", connection, connection, world, world));
        assertFalse(WorldContextLease.acceptsResponse("new", "new", connection, connection, world, null));
    }
    @Test void slowResponseDoesNotRotatePendingNonceOnEveryHeartbeat() {
        assertTrue(WorldContextLease.mayStartRequest(false, 0, 0));
        assertFalse(WorldContextLease.mayStartRequest(true, 2_000_000_000L, 8_000_000_000L));
        assertFalse(WorldContextLease.mayStartRequest(true, 6_000_000_000L, 8_000_000_000L));
        assertTrue(WorldContextLease.mayStartRequest(true, 8_000_000_000L, 8_000_000_000L));
    }
    @Test void neverGuessesNames() {
        var lease = new WorldContextLease();
        lease.bind(new Object(), new Object(), "token", "example:custom_world", 100);
        assertEquals("example:custom_world", lease.dimension());
    }
}

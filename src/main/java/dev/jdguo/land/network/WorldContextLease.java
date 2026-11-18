package dev.jdguo.land.network;

/** Session/world identity, not a dimension-name guess. Shared by client and server tests. */
public final class WorldContextLease {
    private Object connection, world;
    private String token, dimension;
    private long expires;
    public static boolean mayStartRequest(boolean pending, long now, long deadline) {
        return !pending || now >= deadline;
    }
    public static boolean acceptsResponse(String expectedNonce, String receivedNonce,
                                          Object expectedConnection, Object currentConnection,
                                          Object expectedWorld, Object currentWorld) {
        return expectedNonce != null && expectedNonce.equals(receivedNonce)
                && currentConnection != null && expectedConnection == currentConnection
                && currentWorld != null && expectedWorld == currentWorld;
    }
    public void bind(Object connection, Object world, String token, String dimension, long now) {
        this.connection = connection; this.world = world; this.token = token; this.dimension = dimension;
        this.expires = now + 10_000_000_000L;
    }
    public boolean valid(Object connection, Object world, String token, long now) {
        return connection != null && world != null && this.connection == connection && this.world == world
                && this.token != null && this.token.equals(token) && now < expires;
    }
    public String dimension() { return dimension; }
    public String token() { return token; }
    public void clear() { connection = world = null; token = dimension = null; expires = 0; }
}

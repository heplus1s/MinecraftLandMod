package dev.jdguo.land.network;

import java.util.ArrayDeque;

/** Connection-scoped rolling budget; changing the client nonce cannot reset it. */
public final class OwnerColorSessionBudget {
    public static final int LIMIT = 10;
    public static final long WINDOW_NANOS = 10_000_000_000L;
    private Object handler;
    private final ArrayDeque<Long> accepted = new ArrayDeque<>();
    public boolean allow(Object connection, long now) {
        if (connection == null) return false;
        if (handler != connection) { handler = connection; accepted.clear(); }
        while (!accepted.isEmpty() && now - accepted.peekFirst() >= WINDOW_NANOS) accepted.removeFirst();
        if (accepted.size() >= LIMIT) return false;
        accepted.addLast(now); return true;
    }
}

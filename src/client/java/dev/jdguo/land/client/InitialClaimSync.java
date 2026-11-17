package dev.jdguo.land.client;

/** Session-local retry gate: no request before channel registration, no repeat after a snapshot. */
public final class InitialClaimSync {
    private boolean received;
    private boolean requested;
    private int ticks;
    public void reset() { received = false; requested = false; ticks = 0; }
    public void received() { if (requested) received = true; }
    public boolean tick(boolean connected, boolean supported) {
        if (!connected || received) return false;
        if (++ticks < 20) return false;
        ticks = 0;
        if (supported) requested = true;
        return supported;
    }
}

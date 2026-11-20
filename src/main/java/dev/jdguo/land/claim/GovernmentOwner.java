package dev.jdguo.land.claim;

import java.util.UUID;

/** Reserved non-player ownership identity. */
public final class GovernmentOwner {
    public static final UUID ID = UUID.fromString("cbd4b6ae-6633-3ba2-982f-700dc5d4fdb8");
    public static final String NAME = "服务器用地";
    private GovernmentOwner() {}
    public static boolean is(UUID owner) { return ID.equals(owner); }
    public static boolean mayManage(UUID actor, UUID owner, boolean administrator) {
        return owner != null && (is(owner) ? administrator : owner.equals(actor));
    }
}

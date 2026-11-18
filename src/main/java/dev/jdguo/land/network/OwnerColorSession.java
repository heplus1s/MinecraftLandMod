package dev.jdguo.land.network;
import java.util.UUID;
/** Cosmetic subscription is bound to a live handler, current world identity and client epoch. */
public record OwnerColorSession(Object handler, Object world, UUID session) {
    public boolean matches(Object handler, Object world, UUID session) {
        return handler != null && world != null && session != null
                && this.handler == handler && this.world == world && session.equals(this.session);
    }
}

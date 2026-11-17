package dev.jdguo.land.network;

/** Evaluate the live connection and current OP status, never a client-supplied role. */
public final class RequestAccess {
    private RequestAccess() { }
    public static boolean current(Object onlinePlayer, Object requestingPlayer, Object currentHandler, Object requestingHandler) {
        return requestingPlayer != null && requestingHandler != null
                && onlinePlayer == requestingPlayer && currentHandler == requestingHandler;
    }
    public static boolean mayManage(boolean currentConnection, boolean operatorNow) {
        return currentConnection && operatorNow;
    }
}

package dev.jdguo.land.ui;

/** Human-readable failures; never changes server validation or silently moves a selection. */
public final class LandTradeFeedback {
    private LandTradeFeedback() {}
    public static String reason(String reason, String dimension) {
        if ("rejected:dimension".equals(reason)) return unknownDimension(dimension);
        if ("管理员尚未启用无主地块出售".equals(reason))
            return "无主地块出售尚未开启。请管理员在领地管理中开启并保存默认地价。";
        return reason;
    }
    public static String unknownDimension(String dimension) {
        return "无法确认选区维度「" + dimension + "」。为避免交易错误地块，已停止本次操作；不会自动改为其他维度。";
    }
}

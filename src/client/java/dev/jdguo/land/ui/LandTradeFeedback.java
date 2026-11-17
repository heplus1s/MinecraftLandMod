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
        return "服务器不存在地图维度「" + dimension + "」。请切回当前服务器地图维度后重新选区，或使用 K 网格；不会自动改为其他维度。";
    }
}

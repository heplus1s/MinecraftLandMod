package dev.jdguo.land.ui;

import net.minecraft.client.MinecraftClient;

public final class ClientLandDimensions {
    private ClientLandDimensions() {}
    public static String rejectionMessage(MinecraftClient client, String requested) {
        var network = client.getNetworkHandler();
        String current = client.world == null ? "未连接" : client.world.getRegistryKey().getValue().toString();
        var advertised = network == null ? java.util.List.<String>of()
                : network.getWorldKeys().stream().map(key -> key.getValue().toString()).sorted().toList();
        String known = String.join(", ", advertised.stream().limit(8).toList());
        if (advertised.size() > 8) known += " …（共 " + advertised.size() + " 个）";
        return LandTradeFeedback.unknownDimension(requested)
                + " 客户端世界：" + current + "；已确认服务端世界："
                + (ClientWorldContext.ready(client) ? ClientWorldContext.currentDimension(client) : "未确认")
                + "；登录时维度：" + (known.isEmpty() ? "未收到" : known)
                + "。这是客户端预检查，尚未向服务端提交交易。";
    }
    public static boolean mayRequest(MinecraftClient client, String dimension) {
        var network = client.getNetworkHandler();
        if (network == null || client.world == null) return false;
        if (ClientWorldContext.ready(client) && dimension.equals(ClientWorldContext.currentDimension(client))) return true;
        return LandDimensionPolicy.mayRequest(dimension,
                client.world.getRegistryKey().getValue().toString(),
                network.getWorldKeys().stream().map(key -> key.getValue().toString()).toList());
    }
}

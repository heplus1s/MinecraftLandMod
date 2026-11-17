package dev.jdguo.land.network;

import dev.jdguo.land.world.ClaimSavedData;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

/** Optional UI protocol. The server resolves ownership again for every request. */
public final class ParcelRename {
    public static final Identifier CHANNEL = new Identifier("landmod", "parcel_rename");
    private ParcelRename() { }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL, (server, player, handler, buf, sender) -> {
            final java.util.UUID request;
            final java.util.UUID parcel;
            final String name;
            try {
                request = buf.readUuid();
                parcel = buf.readUuid();
                name = buf.readString(128);
                if (buf.isReadable()) return;
            } catch (RuntimeException invalid) { return; }
            server.execute(() -> {
                if (!dev.jdguo.land.network.RequestAccess.current(server.getPlayerManager().getPlayer(player.getUuid()), player, player.networkHandler, handler)) return;
                var result = ClaimSavedData.get(server).renameParcel(player, parcel, name);
                String message = switch (result.denial()) {
                    case NONE -> name.trim().isEmpty() ? "已清除宗地名称" : "宗地名称已保存";
                    case NOT_OWNER -> "只有宗地主人可以改名";
                    case NAME_INVALID -> "名称最多32个字符，不能包含控制字符";
                    case PARCEL_NOT_FOUND -> "宗地已不存在，请重新选择";
                    default -> "当前不能修改这块宗地的名称";
                };
                if (ServerPlayNetworking.canSend(player, CHANNEL)) {
                    var reply = PacketByteBufs.create();
                    reply.writeUuid(request);
                    reply.writeBoolean(result.succeeded());
                    reply.writeString(message);
                    ServerPlayNetworking.send(player, CHANNEL, reply);
                }
            });
        });
    }
}

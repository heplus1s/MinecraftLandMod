package dev.jdguo.land.network;

import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.claim.GovernmentOwner;
import dev.jdguo.land.world.ClaimSavedData;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;
import java.util.UUID;

/** Optional selected-chunk permission protocol; it never trusts client ownership. */
public final class ClaimPermissions {
    public static final Identifier CHANNEL = new Identifier("landmod", "claim_permissions");
    private ClaimPermissions() { }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL, (server, player, handler, buf, sender) -> {
            final UUID request;
            final ClaimKey key;
            final int action;
            final String memberName;
            try {
                request = buf.readUuid();
                key = new ClaimKey(buf.readString(256), buf.readInt(), buf.readInt());
                action = buf.readUnsignedByte(); // 0..2 ordinary; 3..5 government-only read, trust, revoke
                memberName = buf.readString(16);
                if (buf.isReadable() || action > 5) return;
            } catch (RuntimeException invalid) { return; }
            server.execute(() -> {
                if (!dev.jdguo.land.network.RequestAccess.current(server.getPlayerManager().getPlayer(player.getUuid()), player, player.networkHandler, handler)) return;
                var data = ClaimSavedData.get(server);
                var claim = data.claims().get(key.dimension(), key.chunkX(), key.chunkZ());
                boolean government = action >= 3;
                int operation = government ? action - 3 : action;
                boolean owner = claim != null && (government
                        ? GovernmentOwner.is(claim.owner()) && player.hasPermissionLevel(2)
                        : !GovernmentOwner.is(claim.owner()) && player.getUuid().equals(claim.owner()));
                String status = owner ? "成员可建造、拆除及使用容器；仅本区块生效"
                        : government ? "仅管理员可查看和修改服务器用地区块成员" : "只有区块所有者可以查看和修改成员";
                if (owner && operation != 0) {
                    // Add only known online players; revocation also works for offline stored members.
                    var online = server.getPlayerManager().getPlayer(memberName);
                    UUID id = online == null ? null : online.getUuid();
                    String actualName = online == null ? memberName : online.getGameProfile().getName();
                    if (operation == 2) {
                        for (var member : claim.members().values()) {
                            if (member.name().equalsIgnoreCase(memberName)) { id = member.id(); actualName = member.name(); break; }
                        }
                    }
                    if (id == null) status = operation == 1 ? "添加成员时请让该玩家在线" : "未找到这个成员";
                    else {
                        var result = data.setMember(player, key, id, actualName, operation == 1);
                        status = result.succeeded() ? "权限已保存" : "未修改：" + result.denial().name();
                    }
                }
                if (!ServerPlayNetworking.canSend(player, CHANNEL)) return;
                var reply = PacketByteBufs.create();
                reply.writeUuid(request);
                reply.writeBoolean(owner);
                reply.writeString(status);
                String members = owner ? claim.members().values().stream().map(m -> m.name()).collect(java.util.stream.Collectors.joining("、")) : "";
                reply.writeString(members.length() > 8000 ? members.substring(0, 8000) + "…" : members);
                ServerPlayNetworking.send(player, CHANNEL, reply);
            });
        });
    }
}

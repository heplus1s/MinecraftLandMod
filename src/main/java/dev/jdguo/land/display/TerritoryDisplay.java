package dev.jdguo.land.display;

import dev.jdguo.land.world.ClaimSavedData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.text.Text;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Vanilla protocol only: no client mod, chunk tickets or custom HUD required. */
public final class TerritoryDisplay {
    private static final Map<UUID, ServerBossBar> BARS = new HashMap<>();
    private TerritoryDisplay() { }
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var claims = ClaimSavedData.get(server).claims();
            for (var player : server.getPlayerManager().getPlayerList()) {
                var claim = claims.get(player.getWorld().getRegistryKey().getValue().toString(),
                        player.getBlockX() >> 4, player.getBlockZ() >> 4);
                if (claim == null) {
                    remove(player.getUuid());
                    continue;
                }
                String name = claim.name().isBlank() ? "未命名领地" : claim.name();
                var onlineOwner = server.getPlayerManager().getPlayer(claim.owner());
                String owner = onlineOwner == null ? claim.ownerName() : onlineOwner.getGameProfile().getName();
                if (owner.isBlank()) owner = "未知所有者";
                Text title = Text.literal(name + " · 所有者：" + owner);
                var bar = BARS.computeIfAbsent(player.getUuid(), id -> {
                    var created = new ServerBossBar(title, BossBar.Color.BLUE, BossBar.Style.PROGRESS);
                    created.addPlayer(player);
                    return created;
                });
                // Re-resolve each tick so rename, sale, dimension and boundary changes are immediate.
                if (!bar.getName().equals(title)) bar.setName(title);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BARS.values().forEach(ServerBossBar::clearPlayers);
            BARS.clear();
        });
    }
    private static void remove(UUID id) {
        var bar = BARS.remove(id);
        if (bar != null) bar.clearPlayers();
    }
}

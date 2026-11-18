package dev.jdguo.land.client;

import dev.jdguo.land.LandMod;
import dev.jdguo.land.client.xaero.XaeroRefresh;
import dev.jdguo.land.network.ClaimSnapshot;
import dev.jdguo.land.network.ClaimSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class LandClient implements ClientModInitializer {
    private static final InitialClaimSync initial = new InitialClaimSync();
    @Override
    public void onInitializeClient() {
        ClaimDisplayPreferences.load(); ClientOwnerColors.register();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { ClientClaims.clear(); ClientOwnerColors.reset(); initial.reset(); });
        ClientPlayNetworking.registerGlobalReceiver(ClaimSync.CHANNEL, (client, handler, buf, responseSender) -> {
            ClaimSnapshot snapshot;
            try {
                snapshot = ClaimSnapshot.read(buf);
            } catch (RuntimeException exception) {
                LandMod.LOGGER.warn("Ignored a land claim sync packet", exception);
                return;
            }
            client.execute(() -> {
                if (client.getNetworkHandler() != handler || client.player == null) return;
                initial.received();
                ClientClaims.replace(snapshot);
                XaeroRefresh.mark();
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientOwnerColors.tick(client);
            XaeroRefresh.tick();
            boolean connected = client.player != null && client.getNetworkHandler() != null;
            if (initial.tick(connected, connected && ClientPlayNetworking.canSend(ClaimSync.CHANNEL))) {
                var request = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                request.writeVarInt(1);
                ClientPlayNetworking.send(ClaimSync.CHANNEL, request);
            }
        });
    }
}

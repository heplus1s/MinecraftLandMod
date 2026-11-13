package dev.jdguo.land.client;

import dev.jdguo.land.LandMod;
import dev.jdguo.land.client.xaero.XaeroRefresh;
import dev.jdguo.land.network.ClaimSnapshot;
import dev.jdguo.land.network.ClaimSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class LandClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ClaimSync.CHANNEL, (client, handler, buf, responseSender) -> {
            ClaimSnapshot snapshot;
            try {
                snapshot = ClaimSnapshot.read(buf);
            } catch (RuntimeException exception) {
                LandMod.LOGGER.warn("Ignored a land claim sync packet", exception);
                return;
            }
            client.execute(() -> {
                ClientClaims.replace(snapshot);
                XaeroRefresh.mark();
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> XaeroRefresh.tick());
    }
}

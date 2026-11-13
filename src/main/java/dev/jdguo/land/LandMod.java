package dev.jdguo.land;

import dev.jdguo.land.command.ChunkMarks;
import dev.jdguo.land.command.LandCommands;
import dev.jdguo.land.config.LandConfig;
import dev.jdguo.land.network.ClaimSync;
import dev.jdguo.land.protect.ClaimProtection;
import dev.jdguo.land.trade.LandTrade;
import dev.jdguo.land.world.ClaimSavedData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LandMod implements ModInitializer {
    public static final String MOD_ID = "landmod";
    public static final Logger LOGGER = LogManager.getLogger("landmod");

    @Override
    public void onInitialize() {
        LandConfig.load();
        LandCommands.register();
        ClaimProtection.register();
        ClaimSync.register();
        LandTrade.register();
        ChunkMarks.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ClaimSavedData data = ClaimSavedData.get(server);
            if (data.claims().consumeRewrite()) {
                data.markDirty();
            }
            LOGGER.info("Loaded {} land claims in {} parcels", data.claims().size(), data.claims().parcelCount());
        });
    }
}

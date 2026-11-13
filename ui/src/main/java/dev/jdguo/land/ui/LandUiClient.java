package dev.jdguo.land.ui;

import dev.jdguo.land.network.SaleSnapshot;
import dev.jdguo.land.trade.LandTrade;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Optional land client. It does not change protection, and it is not part of a bank screen.
 */
public final class LandUiClient implements ClientModInitializer {
    private static KeyBinding open;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(LandTrade.CHANNEL, (client, handler, buf, responseSender) -> {
        });
        ClientPlayNetworking.registerGlobalReceiver(LandTrade.SALES, (client, handler, buf, responseSender) -> {
            SaleSnapshot snapshot;
            try {
                snapshot = SaleSnapshot.read(buf);
            } catch (RuntimeException exception) {
                return;
            }
            client.execute(() -> ClientSales.replace(snapshot));
        });
        open = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.landmod-ui.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.landmod-ui"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.wasPressed()) {
                client.setScreen(new LandBoardScreen(client.currentScreen));
            }
        });
    }
}

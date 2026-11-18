package dev.jdguo.land.client;

import dev.jdguo.land.color.OwnerColorPalette;
import dev.jdguo.land.network.ColorSync;
import dev.jdguo.land.client.xaero.XaeroRefresh;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import java.util.*;

/** Optional color side-channel. Old claim packets and server authorization are unchanged. */
public final class ClientOwnerColors {
    private static final Map<UUID,Integer> COLORS = new HashMap<>();
    private static final Map<UUID,Integer> OWNER_REVISION = new HashMap<>();
    private static Object connection, world;
    private static int revision, ticks;
    private static boolean received;
    private ClientOwnerColors() {}
    public static int rgb(UUID owner) { return COLORS.getOrDefault(owner, OwnerColorPalette.color(owner)); }
    public static int ownerRevision(UUID owner) { return OWNER_REVISION.getOrDefault(owner,0); }
    public static int revision() { return revision; }
    public static boolean supported() { return ClientPlayNetworking.canSend(ColorSync.CHANNEL); }
    public static void reset() { COLORS.clear(); OWNER_REVISION.clear(); received=false; ticks=0; revision++; XaeroRefresh.mark(); }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ColorSync.CHANNEL, (client,handler,buf,sender) -> {
            final boolean reset;
            final Map<UUID,Integer> update = new HashMap<>();
            try {
                if (buf.readVarInt()!=1) return;
                reset=buf.readBoolean(); int count=buf.readVarInt();
                if(count<0 || count>256) return;
                for(int i=0;i<count;i++) {
                    UUID id=buf.readUuid(); int rgb=buf.readInt();
                    if(rgb!=-1 && !OwnerColorPalette.validRgb(rgb)) return;
                    update.put(id,rgb);
                }
                if(buf.isReadable()) return;
            } catch(RuntimeException invalid) { return; }
            Object responseWorld=client.world;
            client.execute(() -> {
                if(client.getNetworkHandler()!=handler || client.world!=responseWorld || client.player==null) return;
                if(reset) COLORS.clear();
                revision++;
                update.forEach((id,rgb)->{if(rgb==-1)COLORS.remove(id);else COLORS.put(id,rgb);OWNER_REVISION.put(id,revision);});
                received=true; XaeroRefresh.mark();
            });
        });
    }
    public static void tick(MinecraftClient client) {
        if(connection!=client.getNetworkHandler() || world!=client.world) {
            reset(); connection=client.getNetworkHandler(); world=client.world;
        }
        if(client.player!=null && !received && ticks++%40==0 && supported()) send(0,0);
    }
    public static boolean send(int action,int rgb) {
        if(!supported() || action<0 || action>2 || (action==1&&!OwnerColorPalette.validRgb(rgb)))return false;
        var b=PacketByteBufs.create();b.writeVarInt(action);if(action==1)b.writeInt(rgb);
        ClientPlayNetworking.send(ColorSync.CHANNEL,b);return true;
    }
}

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
    private static final OwnerColorPages PAGES = new OwnerColorPages();
    private static UUID session=UUID.randomUUID();
    private static long pendingSince;
    private static final Map<UUID,Integer> OWNER_REVISION = new HashMap<>();
    private static Object connection, world;
    private static int revision, ticks;

    private ClientOwnerColors() {}
    public static int rgb(UUID owner) { return PAGES.colors().getOrDefault(owner, OwnerColorPalette.color(owner)); }
    public static int ownerRevision(UUID owner) { return OWNER_REVISION.getOrDefault(owner,0); }
    public static int revision() { return revision; }
    public static boolean ready() { return PAGES.ready(); }
    public static boolean supported() { return ClientPlayNetworking.canSend(ColorSync.CHANNEL); }
    public static void reset() { PAGES.reset(); session=UUID.randomUUID(); pendingSince=0; OWNER_REVISION.clear(); ticks=0; revision++; XaeroRefresh.mark(); }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ColorSync.CHANNEL, (client,handler,buf,sender) -> {
            final boolean full;
            final UUID responseSession,transfer;
            final int pageIndex,pageCount;
            final Map<UUID,Integer> update = new HashMap<>();
            try {
                if (buf.readVarInt()!=2) return;
                responseSession=buf.readUuid();full=buf.readBoolean();transfer=buf.readUuid();
                pageIndex=buf.readVarInt();pageCount=buf.readVarInt();int count=buf.readVarInt();
                if(count<0 || count>256 || pageCount<1 || pageCount>4096 || pageIndex<0 || pageIndex>=pageCount) return;
                for(int i=0;i<count;i++) {
                    UUID id=buf.readUuid();int rgb=buf.readInt();
                    if(rgb!=-1 && !OwnerColorPalette.validRgb(rgb)) return;
                    update.put(id,rgb);
                }
                if(buf.isReadable()) return;
            } catch(RuntimeException invalid) { return; }
            Object responseWorld=client.world;
            client.execute(() -> {
                if(client.getNetworkHandler()!=handler || client.world!=responseWorld || client.player==null || !session.equals(responseSession)) return;
                try {
                    if(full&&pageIndex==0)pendingSince=System.nanoTime();
                    boolean committed=PAGES.accept(full,transfer,pageIndex,pageCount,update);
                    if(!committed)return;
                    pendingSince=0;revision++;
                    if(full)PAGES.colors().keySet().forEach(id->OWNER_REVISION.put(id,revision));
                    else update.keySet().forEach(id->OWNER_REVISION.put(id,revision));
                    XaeroRefresh.mark();
                }catch(RuntimeException invalid){PAGES.reset();pendingSince=0;}

            });
        });
    }
    public static void tick(MinecraftClient client) {
        if(connection!=client.getNetworkHandler() || world!=client.world) {
            reset(); connection=client.getNetworkHandler(); world=client.world;
        }
        if(client.player!=null && ((!PAGES.ready()&&!PAGES.pending()) || (PAGES.pending()&&System.nanoTime()-pendingSince>5_000_000_000L)) && ticks++%40==0 && supported()) send(0,0);
    }
    public static boolean send(int action,int rgb) {
        if(!supported() || action<0 || action>2 || (action==1&&!OwnerColorPalette.validRgb(rgb)))return false;
        var b=PacketByteBufs.create();b.writeVarInt(action);if(action==1)b.writeInt(rgb);b.writeUuid(session);
        ClientPlayNetworking.send(ColorSync.CHANNEL,b);return true;
    }
}

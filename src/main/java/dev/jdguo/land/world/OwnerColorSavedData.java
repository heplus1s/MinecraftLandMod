package dev.jdguo.land.world;

import dev.jdguo.land.color.OwnerColorPalette;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import java.util.*;

/** Independent optional cosmetic data. Never changes claims or ownership. */
public final class OwnerColorSavedData extends PersistentState {
    public static final String ID = "landmod_owner_colors";
    public static final int MAX_OVERRIDES = 16384;
    private final Map<UUID, Integer> overrides = new LinkedHashMap<>();
    private final Map<UUID, Integer> automatic = new LinkedHashMap<>();
    private long revision;
    private Object scannedClaims;
    private int scannedRevision;
    public static OwnerColorSavedData get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(
                OwnerColorSavedData::fromNbt, OwnerColorSavedData::new, ID);
    }
    public static OwnerColorSavedData fromNbt(NbtCompound nbt) {
        var data = new OwnerColorSavedData();
        readColors(nbt.getCompound("Automatic"), data.automatic);
        readColors(nbt.getCompound("Overrides"), data.overrides);
        return data;
    }
    private static void readColors(NbtCompound entries, Map<UUID, Integer> target) {
        for (String key : entries.getKeys()) {
            try {
                UUID owner = UUID.fromString(key);
                if (entries.contains(key, NbtElement.INT_TYPE) && OwnerColorPalette.validRgb(entries.getInt(key)))
                    target.put(owner, entries.getInt(key));
            } catch (IllegalArgumentException ignored) { }
        }
    }
    public Map<UUID, Integer> overrides() { return Collections.unmodifiableMap(new LinkedHashMap<>(overrides)); }
    public Map<UUID, Integer> automatic() { return Collections.unmodifiableMap(new LinkedHashMap<>(automatic)); }
    public Map<UUID, Integer> effective() { var all = new LinkedHashMap<>(automatic); all.putAll(overrides); return Collections.unmodifiableMap(all); }
    public long revision() { return revision; }
    public void ensureClaims(MinecraftServer server) {
        var claims = ClaimSavedData.get(server).claims();
        if (scannedClaims == claims && scannedRevision == claims.revision()) return;
        ensure(claims.toSnapshot().claims().stream().map(c -> new dev.jdguo.land.color.OwnerColorAllocator.Cell(c.dimension(), c.chunkX(), c.chunkZ(), c.owner())).toList());
        scannedClaims = claims; scannedRevision = claims.revision();
    }
    public void ensure(Collection<dev.jdguo.land.color.OwnerColorAllocator.Cell> claims) {
        var added = dev.jdguo.land.color.OwnerColorAllocator.allocate(claims, automatic, overrides);
        if (!added.isEmpty()) { automatic.putAll(added); revision++; markDirty(); }
    }
    private void ensureOwner(UUID owner) {
        if (!automatic.containsKey(owner)) { automatic.put(owner, OwnerColorPalette.color(owner)); revision++; markDirty(); }
    }
    public int color(UUID owner) { return overrides.getOrDefault(owner, automatic.getOrDefault(owner, OwnerColorPalette.color(owner))); }
    public boolean set(UUID owner, int rgb) {
        if (owner == null || !OwnerColorPalette.validRgb(rgb)) throw new IllegalArgumentException("owner/color");
        if (!overrides.containsKey(owner) && overrides.size() >= MAX_OVERRIDES) throw new IllegalStateException("color capacity");
        ensureOwner(owner);
        if (Objects.equals(overrides.put(owner, rgb), rgb)) return false;
        revision++; markDirty(); return true;
    }
    public boolean reset(UUID owner) {
        if (owner == null) return false;
        ensureOwner(owner);
        if (overrides.remove(owner) == null) return false;
        revision++; markDirty(); return true;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        var entries = new NbtCompound();
        overrides.forEach((owner, rgb) -> entries.putInt(owner.toString(), rgb));
        var allocated = new NbtCompound(); automatic.forEach((owner, rgb) -> allocated.putInt(owner.toString(), rgb));
        nbt.putInt("Version", 2); nbt.put("Automatic", allocated); nbt.put("Overrides", entries); return nbt;
    }
}

package dev.jdguo.land.claim;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of one estate. The chunks in it are stored as {@link Claim}s that
 * point at this id. A parcel is never a sub-chunk cuboid.
 */
public final class Parcel {
    private final UUID id;
    private String name;
    private final NbtCompound flags;
    private final NbtCompound extra;

    public Parcel(UUID id, String name, NbtCompound flags, NbtCompound extra) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = name == null ? "" : name;
        this.flags = flags == null ? new NbtCompound() : flags;
        this.extra = extra == null ? new NbtCompound() : extra;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public NbtCompound flags() {
        return flags;
    }

    NbtCompound storedExtra() {
        return extra;
    }

    void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public void putFlag(String key, NbtElement value) {
        flags.put(key, value.copy());
    }
}

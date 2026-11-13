package dev.jdguo.land.claim;

import net.minecraft.nbt.NbtCompound;

import java.util.Objects;
import java.util.UUID;

/**
 * A trusted player on one claimed chunk. {@code extra} keeps unknown NBT so a
 * later version can store more member data without this version deleting it.
 */
public final class Member {
    private final UUID id;
    private String name;
    private final NbtCompound extra;

    public Member(UUID id, String name, NbtCompound extra) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = name == null ? "" : name;
        this.extra = extra == null ? new NbtCompound() : extra;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public NbtCompound extra() {
        return extra;
    }
}

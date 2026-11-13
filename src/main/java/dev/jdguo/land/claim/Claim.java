package dev.jdguo.land.claim;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One claimed chunk. Members belong to this chunk only.
 * {@code name} is optional display text. {@code flags} and {@code extra} are
 * reserved storage: flags for later shop or debt markers, extra for any NBT
 * key this version does not know so a newer save can be loaded and written
 * again without dropping those keys.
 */
public final class Claim {
    private final String dimension;
    private final int chunkX;
    private final int chunkZ;
    private UUID owner;
    private String ownerName;
    private String name;
    private final LinkedHashMap<UUID, Member> members;
    private final NbtCompound flags;
    private final NbtCompound extra;

    public Claim(String dimension, int chunkX, int chunkZ, UUID owner, String ownerName, String name,
                 LinkedHashMap<UUID, Member> members, NbtCompound flags, NbtCompound extra) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.owner = Objects.requireNonNull(owner, "owner");
        this.ownerName = ownerName == null ? "" : ownerName;
        this.name = name == null ? "" : name;
        this.members = members == null ? new LinkedHashMap<>() : members;
        this.flags = flags == null ? new NbtCompound() : flags;
        this.extra = extra == null ? new NbtCompound() : extra;
    }

    public ClaimKey key() {
        return new ClaimKey(dimension, chunkX, chunkZ);
    }

    public String dimension() {
        return dimension;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
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

    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    public Map<UUID, Member> members() {
        return Collections.unmodifiableMap(members);
    }

    public void putFlag(String key, NbtElement value) {
        flags.put(key, value.copy());
    }

    void setOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    void setOwnerName(String ownerName) {
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    void setName(String name) {
        this.name = name == null ? "" : name;
    }

    void clearMembers() {
        members.clear();
    }

    void addMember(Member member) {
        members.put(member.id(), member);
    }

    Member removeMember(UUID playerId) {
        return members.remove(playerId);
    }

    boolean renameMember(UUID playerId, String playerName) {
        Member member = members.get(playerId);
        if (member == null || member.name().equals(playerName)) {
            return false;
        }
        member.setName(playerName);
        return true;
    }
}

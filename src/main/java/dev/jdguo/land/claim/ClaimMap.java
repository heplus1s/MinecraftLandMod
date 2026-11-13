package dev.jdguo.land.claim;

import dev.jdguo.land.integration.ClaimGates;
import dev.jdguo.land.network.ClaimSnapshot;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory claims for one world save. Chunk positions are independent:
 * the same x/z in another dimension is a different claim.
 * A non-positive claim limit means no limit.
 */
public final class ClaimMap {
    public static final int FORMAT = 1;

    private static final Set<String> ROOT_KEYS = Set.of("Format", "Claims");
    private static final Set<String> CLAIM_KEYS = Set.of(
            "Dim", "X", "Z", "Owner", "OwnerName", "Name", "Members", "Flags");
    private static final Set<String> MEMBER_KEYS = Set.of("Id", "Name");

    private static final Comparator<Claim> ORDER = Comparator
            .comparing(Claim::dimension)
            .thenComparingInt(Claim::chunkX)
            .thenComparingInt(Claim::chunkZ);

    private final Map<ClaimKey, Claim> claims = new HashMap<>();
    private NbtCompound extraRoot = new NbtCompound();
    private int storedFormat = FORMAT;
    private int revision = 1;

    public int size() {
        return claims.size();
    }

    public int revision() {
        return revision;
    }

    public @Nullable Claim get(String dimension, int chunkX, int chunkZ) {
        return claims.get(new ClaimKey(dimension, chunkX, chunkZ));
    }

    public Collection<Claim> all() {
        return List.copyOf(claims.values());
    }

    public List<Claim> ownedBy(UUID owner) {
        List<Claim> owned = new ArrayList<>();
        for (Claim claim : claims.values()) {
            if (claim.owner().equals(owner)) {
                owned.add(claim);
            }
        }
        owned.sort(ORDER);
        return owned;
    }

    public int countOwned(UUID owner) {
        int count = 0;
        for (Claim claim : claims.values()) {
            if (claim.owner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Operators bypass protection. A null actor (a mob explosion, for example)
     * cannot change a claimed chunk. Unclaimed chunks are open to everyone.
     */
    public boolean allowsChange(String dimension, int blockX, int blockZ, @Nullable UUID actor, boolean operator) {
        if (operator) {
            return true;
        }
        Claim claim = get(dimension, blockX >> 4, blockZ >> 4);
        if (claim == null) {
            return true;
        }
        if (actor == null) {
            return false;
        }
        return actor.equals(claim.owner()) || claim.isMember(actor);
    }

    public boolean updateName(UUID playerId, String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (Claim claim : claims.values()) {
            if (claim.owner().equals(playerId) && !claim.ownerName().equals(playerName)) {
                claim.setOwnerName(playerName);
                changed = true;
            }
            if (claim.renameMember(playerId, playerName)) {
                changed = true;
            }
        }
        if (changed) {
            revision++;
        }
        return changed;
    }

    public Result claim(String dimension, int chunkX, int chunkZ, UUID owner, String ownerName, int maxClaims) {
        Claim existing = get(dimension, chunkX, chunkZ);
        if (existing != null) {
            if (existing.owner().equals(owner)) {
                return Result.fail(Denial.ALREADY_OWNED, existing, existing.ownerName());
            }
            return Result.fail(Denial.OWNED_BY_OTHER, existing, existing.ownerName());
        }
        if (maxClaims > 0 && countOwned(owner) >= maxClaims) {
            return Result.fail(Denial.AT_LIMIT, null, "");
        }
        if (!ClaimGates.allowClaim(owner, dimension, chunkX, chunkZ)) {
            return Result.fail(Denial.GATE, null, "");
        }
        Claim claim = new Claim(dimension, chunkX, chunkZ, owner, ownerName, "",
                new LinkedHashMap<>(), new NbtCompound(), new NbtCompound());
        claims.put(claim.key(), claim);
        revision++;
        return Result.ok(claim);
    }

    public Result abandon(String dimension, int chunkX, int chunkZ, UUID actor) {
        Claim claim = get(dimension, chunkX, chunkZ);
        if (claim == null) {
            return Result.fail(Denial.NOT_CLAIMED, null, "");
        }
        if (!claim.owner().equals(actor)) {
            return Result.fail(Denial.NOT_OWNER, claim, claim.ownerName());
        }
        if (!ClaimGates.allowAbandon(actor, claim)) {
            return Result.fail(Denial.GATE, claim, "");
        }
        claims.remove(claim.key());
        revision++;
        return Result.ok(claim);
    }

    public Result trust(String dimension, int chunkX, int chunkZ, UUID actor, UUID memberId, String memberName) {
        Claim claim = get(dimension, chunkX, chunkZ);
        if (claim == null) {
            return Result.fail(Denial.NOT_CLAIMED, null, "");
        }
        if (!claim.owner().equals(actor)) {
            return Result.fail(Denial.NOT_OWNER, claim, claim.ownerName());
        }
        if (actor.equals(memberId)) {
            return Result.fail(Denial.SELF, claim, "");
        }
        if (claim.isMember(memberId)) {
            return Result.fail(Denial.ALREADY_MEMBER, claim, memberName);
        }
        if (!ClaimGates.allowTrust(actor, claim, memberId)) {
            return Result.fail(Denial.GATE, claim, memberName);
        }
        claim.addMember(new Member(memberId, memberName, new NbtCompound()));
        revision++;
        return Result.ok(claim);
    }

    public Result untrust(String dimension, int chunkX, int chunkZ, UUID actor, UUID memberId, String memberName) {
        Claim claim = get(dimension, chunkX, chunkZ);
        if (claim == null) {
            return Result.fail(Denial.NOT_CLAIMED, null, "");
        }
        if (!claim.owner().equals(actor)) {
            return Result.fail(Denial.NOT_OWNER, claim, claim.ownerName());
        }
        if (!claim.isMember(memberId)) {
            return Result.fail(Denial.NOT_MEMBER, claim, memberName);
        }
        if (!ClaimGates.allowUntrust(actor, claim, memberId)) {
            return Result.fail(Denial.GATE, claim, memberName);
        }
        claim.removeMember(memberId);
        revision++;
        return Result.ok(claim);
    }

    public Result transfer(String dimension, int chunkX, int chunkZ, UUID actor, UUID newOwner, String newOwnerName, int maxClaims) {
        Claim claim = get(dimension, chunkX, chunkZ);
        if (claim == null) {
            return Result.fail(Denial.NOT_CLAIMED, null, "");
        }
        if (!claim.owner().equals(actor)) {
            return Result.fail(Denial.NOT_OWNER, claim, claim.ownerName());
        }
        if (actor.equals(newOwner)) {
            return Result.fail(Denial.SELF, claim, newOwnerName);
        }
        if (maxClaims > 0 && countOwned(newOwner) >= maxClaims) {
            return Result.fail(Denial.AT_LIMIT, claim, newOwnerName);
        }
        if (!ClaimGates.allowTransfer(actor, claim, newOwner)) {
            return Result.fail(Denial.GATE, claim, newOwnerName);
        }
        claim.setOwner(newOwner, newOwnerName);
        claim.clearMembers();
        revision++;
        return Result.ok(claim);
    }

    public ClaimSnapshot toSnapshot() {
        List<Claim> ordered = new ArrayList<>(claims.values());
        ordered.sort(ORDER);
        List<ClaimSnapshot.PlacedClaim> placed = new ArrayList<>(ordered.size());
        for (Claim claim : ordered) {
            placed.add(new ClaimSnapshot.PlacedClaim(
                    claim.dimension(),
                    claim.chunkX(),
                    claim.chunkZ(),
                    claim.owner(),
                    claim.ownerName(),
                    claim.name()));
        }
        return new ClaimSnapshot(revision, List.copyOf(placed));
    }

    public NbtCompound toNbt() {
        NbtCompound root = extraRoot.copy();
        root.putInt("Format", storedFormat);
        NbtList list = new NbtList();
        List<Claim> ordered = new ArrayList<>(claims.values());
        ordered.sort(ORDER);
        for (Claim claim : ordered) {
            list.add(writeClaim(claim));
        }
        root.put("Claims", list);
        return root;
    }

    public static ClaimMap fromNbt(NbtCompound nbt) {
        ClaimMap map = new ClaimMap();
        if (nbt == null) {
            return map;
        }
        map.extraRoot = unknownKeys(nbt, ROOT_KEYS);
        if (nbt.contains("Format", NbtElement.INT_TYPE)) {
            map.storedFormat = Math.max(FORMAT, nbt.getInt("Format"));
        }
        NbtList list = nbt.getList("Claims", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            Claim claim = readClaim(list.getCompound(i));
            if (claim != null) {
                map.claims.putIfAbsent(claim.key(), claim);
            }
        }
        return map;
    }

    private static NbtCompound writeClaim(Claim claim) {
        NbtCompound tag = claim.storedExtra().copy();
        tag.putString("Dim", claim.dimension());
        tag.putInt("X", claim.chunkX());
        tag.putInt("Z", claim.chunkZ());
        tag.putUuid("Owner", claim.owner());
        tag.putString("OwnerName", claim.ownerName());
        tag.putString("Name", claim.name());
        NbtList members = new NbtList();
        for (Member member : claim.members().values()) {
            NbtCompound memberTag = member.extra().copy();
            memberTag.putUuid("Id", member.id());
            memberTag.putString("Name", member.name());
            members.add(memberTag);
        }
        tag.put("Members", members);
        tag.put("Flags", claim.flags().copy());
        return tag;
    }

    private static @Nullable Claim readClaim(NbtCompound tag) {
        if (!tag.contains("Dim", NbtElement.STRING_TYPE)
                || !tag.contains("X", NbtElement.INT_TYPE)
                || !tag.contains("Z", NbtElement.INT_TYPE)
                || !tag.containsUuid("Owner")) {
            return null;
        }
        UUID owner = tag.getUuid("Owner");
        LinkedHashMap<UUID, Member> members = new LinkedHashMap<>();
        NbtList memberList = tag.getList("Members", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < memberList.size(); i++) {
            NbtCompound memberTag = memberList.getCompound(i);
            if (!memberTag.containsUuid("Id")) {
                continue;
            }
            UUID id = memberTag.getUuid("Id");
            if (id.equals(owner) || members.containsKey(id)) {
                continue;
            }
            members.put(id, new Member(id, memberTag.getString("Name"), unknownKeys(memberTag, MEMBER_KEYS)));
        }
        NbtCompound flags = tag.contains("Flags", NbtElement.COMPOUND_TYPE)
                ? tag.getCompound("Flags").copy()
                : new NbtCompound();
        return new Claim(
                tag.getString("Dim"),
                tag.getInt("X"),
                tag.getInt("Z"),
                owner,
                tag.getString("OwnerName"),
                tag.getString("Name"),
                members,
                flags,
                unknownKeys(tag, CLAIM_KEYS));
    }

    private static NbtCompound unknownKeys(NbtCompound source, Set<String> known) {
        NbtCompound extra = new NbtCompound();
        for (String key : source.getKeys()) {
            if (!known.contains(key)) {
                NbtElement value = source.get(key);
                if (value != null) {
                    extra.put(key, value.copy());
                }
            }
        }
        return extra;
    }

    public enum Denial {
        NONE,
        ALREADY_OWNED,
        OWNED_BY_OTHER,
        AT_LIMIT,
        NOT_CLAIMED,
        NOT_OWNER,
        SELF,
        ALREADY_MEMBER,
        NOT_MEMBER,
        GATE
    }

    public record Result(Denial denial, @Nullable Claim claim, String subject) {
        public boolean succeeded() {
            return denial == Denial.NONE;
        }

        static Result ok(Claim claim) {
            return new Result(Denial.NONE, claim, "");
        }

        static Result fail(Denial denial, @Nullable Claim claim, String subject) {
            return new Result(denial, claim, subject == null ? "" : subject);
        }
    }
}

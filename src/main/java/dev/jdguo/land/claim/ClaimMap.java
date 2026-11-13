package dev.jdguo.land.claim;

import dev.jdguo.land.LandMod;
import dev.jdguo.land.integration.ClaimGates;
import dev.jdguo.land.integration.LandEvents;
import dev.jdguo.land.network.ClaimSnapshot;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory claims for one world save. A claim is one 16×16 chunk.
 * A parcel is a named id shared by one or more claims in the same dimension.
 * The same x/z in another dimension is a different claim and cannot join the parcel.
 * A non-positive claim limit means no limit. The limit counts chunks, not parcels.
 */
public final class ClaimMap {
    public static final int FORMAT = 2;

    private static final Set<String> ROOT_KEYS = Set.of("Format", "Claims", "Parcels", "Sales");
    private static final Set<String> CLAIM_KEYS = Set.of(
            "Dim", "X", "Z", "Owner", "OwnerName", "Name", "Members", "Flags", "Parcel");
    private static final Set<String> MEMBER_KEYS = Set.of("Id", "Name");
    private static final Set<String> PARCEL_KEYS = Set.of("Id", "Name", "Flags");

    private static final Comparator<Claim> ORDER = Comparator
            .comparing(Claim::dimension)
            .thenComparingInt(Claim::chunkX)
            .thenComparingInt(Claim::chunkZ);

    private final Map<ClaimKey, Claim> claims = new HashMap<>();
    private final Map<UUID, Parcel> parcels = new HashMap<>();
    private final SaleBook sales = new SaleBook();
    private NbtCompound extraRoot = new NbtCompound();
    private int storedFormat = FORMAT;
    private int revision = 1;
    private boolean rewriteRequired;

    public int size() {
        return claims.size();
    }

    public int parcelCount() {
        Set<UUID> ids = new HashSet<>();
        for (Claim claim : claims.values()) {
            ids.add(claim.parcelId());
        }
        return ids.size();
    }

    public int revision() {
        return revision;
    }

    /**
     * True after a load that had to invent parcel ids or repair a parcel that
     * no longer had one owner in one dimension. The save should be written back.
     */
    public boolean needsRewrite() {
        return rewriteRequired;
    }

    public boolean consumeRewrite() {
        boolean value = rewriteRequired;
        rewriteRequired = false;
        return value;
    }

    public @Nullable Claim get(String dimension, int chunkX, int chunkZ) {
        return claims.get(new ClaimKey(dimension, chunkX, chunkZ));
    }

    public Collection<Claim> all() {
        return List.copyOf(claims.values());
    }

    public List<Claim> inParcel(UUID parcelId) {
        List<Claim> list = new ArrayList<>();
        if (parcelId == null) {
            return list;
        }
        for (Claim claim : claims.values()) {
            if (parcelId.equals(claim.parcelId())) {
                list.add(claim);
            }
        }
        list.sort(ORDER);
        return list;
    }

    public @Nullable ParcelInfo info(UUID parcelId) {
        List<Claim> list = inParcel(parcelId);
        if (list.isEmpty()) {
            return null;
        }
        Parcel parcel = parcels.get(parcelId);
        String name = parcel == null ? list.get(0).name() : parcel.name();
        NbtCompound flags = parcel == null ? new NbtCompound() : parcel.flags();
        List<ClaimKey> keys = new ArrayList<>(list.size());
        for (Claim claim : list) {
            keys.add(claim.key());
        }
        Claim first = list.get(0);
        return new ParcelInfo(
                parcelId,
                name,
                first.owner(),
                first.ownerName(),
                first.dimension(),
                list.size(),
                list.size() * ParcelInfo.BLOCKS_PER_CHUNK,
                keys,
                flags);
    }

    public int parcelChunkCount(UUID parcelId) {
        ParcelInfo info = info(parcelId);
        return info == null ? 0 : info.chunkCount();
    }

    public long parcelBlockCount(UUID parcelId) {
        ParcelInfo info = info(parcelId);
        return info == null ? 0L : info.blockCount();
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

    public List<ParcelInfo> parcelsOwnedBy(UUID owner) {
        List<ParcelInfo> owned = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Claim claim : claims.values()) {
            if (claim.owner().equals(owner) && seen.add(claim.parcelId())) {
                ParcelInfo info = info(claim.parcelId());
                if (info != null && info.owner().equals(owner)) {
                    owned.add(info);
                }
            }
        }
        owned.sort(Comparator
                .comparing(ParcelInfo::dimension)
                .thenComparingInt(info -> info.chunks().get(0).chunkX())
                .thenComparingInt(info -> info.chunks().get(0).chunkZ())
                .thenComparing(info -> info.id().toString()));
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

    public int countParcelsOwned(UUID owner) {
        return parcelsOwnedBy(owner).size();
    }

    /**
     * Parcels the owner already has on the four edge-adjacent chunks.
     * Used when a new claim can join one of them.
     */
    public List<ParcelInfo> adjacentOwnedParcels(UUID owner, String dimension, int chunkX, int chunkZ) {
        List<ParcelInfo> found = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        int[][] deltas = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] delta : deltas) {
            Claim claim = get(dimension, chunkX + delta[0], chunkZ + delta[1]);
            if (claim == null || !claim.owner().equals(owner) || !seen.add(claim.parcelId())) {
                continue;
            }
            ParcelInfo info = info(claim.parcelId());
            if (info != null) {
                found.add(info);
            }
        }
        return found;
    }

    public NbtCompound copyParcelFlags(UUID parcelId) {
        Parcel parcel = parcels.get(parcelId);
        if (parcel == null) {
            return new NbtCompound();
        }
        return parcel.flags().copy();
    }

    public Result sellParcel(UUID seller, String sellerName, UUID parcelId) {
        return sales.sellParcel(this, seller, sellerName, parcelId);
    }

    public Result sellChunks(UUID seller, String sellerName, List<ClaimKey> keys) {
        return sales.sellChunks(this, seller, sellerName, keys);
    }

    public Result cancelSale(UUID seller, UUID parcelId) {
        return sales.cancel(this, seller, parcelId);
    }

    public Result buyParcel(UUID buyer, String buyerName, UUID parcelId, int maxClaims) {
        return sales.buyParcel(this, buyer, buyerName, parcelId, maxClaims);
    }

    public Result buyChunks(UUID buyer, String buyerName, List<ClaimKey> keys, int maxClaims) {
        return sales.buyChunks(this, buyer, buyerName, keys, maxClaims);
    }

    public @Nullable SaleInfo saleOf(UUID parcelId) {
        return sales.find(parcelId);
    }

    public List<SaleInfo> sales() {
        sales.prune(this);
        return sales.list();
    }

    public int saleRevision() {
        return sales.revision();
    }

    public boolean putParcelFlag(UUID parcelId, String key, NbtElement value) {
        if (info(parcelId) == null || key == null || value == null) {
            return false;
        }
        parcels.get(parcelId).putFlag(key, value);
        revision++;
        return true;
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
                return fail(Denial.ALREADY_OWNED, existing, existing.ownerName());
            }
            return fail(Denial.OWNED_BY_OTHER, existing, existing.ownerName());
        }
        if (maxClaims > 0 && countOwned(owner) >= maxClaims) {
            return fail(Denial.AT_LIMIT, null, "");
        }
        if (!ClaimGates.allowClaim(owner, dimension, chunkX, chunkZ)) {
            return fail(Denial.GATE, null, "");
        }
        Parcel parcel = createParcel("");
        Claim claim = place(dimension, chunkX, chunkZ, parcel, owner, ownerName);
        revision++;
        return ok(claim);
    }

    public Result claimJoin(String dimension, int chunkX, int chunkZ, UUID owner, String ownerName, int maxClaims, UUID parcelId) {
        Claim existing = get(dimension, chunkX, chunkZ);
        if (existing != null) {
            if (existing.owner().equals(owner)) {
                return fail(Denial.ALREADY_OWNED, existing, existing.ownerName());
            }
            return fail(Denial.OWNED_BY_OTHER, existing, existing.ownerName());
        }
        ParcelInfo parcel = info(parcelId);
        if (parcel == null) {
            return fail(Denial.PARCEL_NOT_FOUND, null, "");
        }
        if (!parcel.owner().equals(owner)) {
            return fail(Denial.NOT_OWNER, claims.get(parcel.chunks().get(0)), parcel.ownerName());
        }
        if (!parcel.dimension().equals(dimension)) {
            return fail(Denial.DIFFERENT_DIMENSION, null, "");
        }
        if (!adjacentTo(parcel, chunkX, chunkZ)) {
            return fail(Denial.NOT_ADJACENT, null, "");
        }
        if (maxClaims > 0 && countOwned(owner) >= maxClaims) {
            return fail(Denial.AT_LIMIT, null, "");
        }
        if (!ClaimGates.allowClaim(owner, dimension, chunkX, chunkZ)
                || !ClaimGates.allowJoin(owner, parcel, dimension, chunkX, chunkZ)) {
            return fail(Denial.GATE, null, "");
        }
        Claim claim = place(dimension, chunkX, chunkZ, parcels.get(parcelId), owner, ownerName);
        revision++;
        return ok(claim);
    }

    public Result abandon(String dimension, int chunkX, int chunkZ, UUID actor) {
        Claim claim = get(dimension, chunkX, chunkZ);
        if (claim == null) {
            return fail(Denial.NOT_CLAIMED, null, "");
        }
        if (!claim.owner().equals(actor)) {
            return fail(Denial.NOT_OWNER, claim, claim.ownerName());
        }
        if (!ClaimGates.allowAbandon(actor, claim)) {
            return fail(Denial.GATE, claim, "");
        }
        UUID parcelId = claim.parcelId();
        claims.remove(claim.key());
        if (inParcel(parcelId).isEmpty()) {
            parcels.remove(parcelId);
        }
        revision++;
        return ok(claim);
    }

    public Result trust(String dimension, int chunkX, int chunkZ, UUID actor, UUID memberId, String memberName) {
        Claim claim = get(dimension, chunkX, chunkZ);
        if (claim == null) {
            return fail(Denial.NOT_CLAIMED, null, "");
        }
        if (!claim.owner().equals(actor)) {
            return fail(Denial.NOT_OWNER, claim, claim.ownerName());
        }
        if (actor.equals(memberId)) {
            return fail(Denial.SELF, claim, "");
        }
        if (claim.isMember(memberId)) {
            return fail(Denial.ALREADY_MEMBER, claim, memberName);
        }
        if (!ClaimGates.allowTrust(actor, claim, memberId)) {
            return fail(Denial.GATE, claim, memberName);
        }
        claim.addMember(new Member(memberId, memberName, new NbtCompound()));
        revision++;
        return ok(claim);
    }

    public Result untrust(String dimension, int chunkX, int chunkZ, UUID actor, UUID memberId, String memberName) {
        Claim claim = get(dimension, chunkX, chunkZ);
        if (claim == null) {
            return fail(Denial.NOT_CLAIMED, null, "");
        }
        if (!claim.owner().equals(actor)) {
            return fail(Denial.NOT_OWNER, claim, claim.ownerName());
        }
        if (!claim.isMember(memberId)) {
            return fail(Denial.NOT_MEMBER, claim, memberName);
        }
        if (!ClaimGates.allowUntrust(actor, claim, memberId)) {
            return fail(Denial.GATE, claim, memberName);
        }
        claim.removeMember(memberId);
        revision++;
        return ok(claim);
    }

    public Result transfer(String dimension, int chunkX, int chunkZ, UUID actor, UUID newOwner, String newOwnerName, int maxClaims) {
        return transfer(dimension, chunkX, chunkZ, actor, newOwner, newOwnerName, maxClaims, "");
    }

    public Result transfer(String dimension, int chunkX, int chunkZ, UUID actor, UUID newOwner, String newOwnerName, int maxClaims, String reference) {
        return transferChunks(actor, List.of(new ClaimKey(dimension, chunkX, chunkZ)), newOwner, newOwnerName, maxClaims, reference);
    }

    /**
     * Move these chunks to {@code newOwner}. They become one parcel.
     * When the selection is the whole parcel, the parcel id stays.
     * A smaller selection is carved out first, then given away.
     */
    public Result transferChunks(UUID actor, List<ClaimKey> keys, UUID newOwner, String newOwnerName, int maxClaims, String reference) {
        String ref = ref(reference);
        String ownerName = newOwnerName == null ? "" : newOwnerName;
        List<Claim> moving = resolve(keys);
        if (moving == null || moving.isEmpty()) {
            return fail(Denial.NOT_CLAIMED, null, "");
        }
        UUID parcelId = moving.get(0).parcelId();
        for (Claim claim : moving) {
            if (!claim.owner().equals(actor)) {
                return fail(Denial.NOT_OWNER, claim, claim.ownerName());
            }
            if (!claim.parcelId().equals(parcelId)) {
                return fail(Denial.MIXED_PARCEL, claim, "");
            }
        }
        if (actor.equals(newOwner)) {
            return fail(Denial.SELF, moving.get(0), ownerName);
        }
        if (moving.size() == inParcel(parcelId).size()) {
            return transferParcel(actor, parcelId, newOwner, ownerName, maxClaims, ref);
        }
        if (maxClaims > 0 && (long) countOwned(newOwner) + moving.size() > maxClaims) {
            return fail(Denial.AT_LIMIT, moving.get(0), ownerName);
        }
        ParcelInfo source = info(parcelId);
        if (!ClaimGates.allowSplit(actor, source, List.copyOf(moving))) {
            return fail(Denial.GATE, moving.get(0), ownerName);
        }
        for (Claim claim : moving) {
            if (!ClaimGates.allowTransfer(actor, claim, newOwner, ref)) {
                return fail(Denial.GATE, claim, ownerName);
            }
        }
        Parcel created = createParcel("");
        for (Claim claim : moving) {
            claim.setParcel(created.id());
            claim.setName("");
            claim.setOwner(newOwner, ownerName);
            claim.clearMembers();
        }
        revision++;
        LandEvents.afterChunkTransfer(List.copyOf(moving), parcelId, info(created.id()), actor, newOwner, ref);
        return ok(moving.get(0));
    }

    public Result transferParcel(UUID actor, UUID parcelId, UUID newOwner, String newOwnerName, int maxClaims, String reference) {
        String ref = ref(reference);
        String ownerName = newOwnerName == null ? "" : newOwnerName;
        ParcelInfo parcel = info(parcelId);
        if (parcel == null) {
            return fail(Denial.PARCEL_NOT_FOUND, null, "");
        }
        Claim sample = claims.get(parcel.chunks().get(0));
        if (!parcel.owner().equals(actor)) {
            return fail(Denial.NOT_OWNER, sample, parcel.ownerName());
        }
        if (actor.equals(newOwner)) {
            return fail(Denial.SELF, sample, ownerName);
        }
        if (maxClaims > 0 && (long) countOwned(newOwner) + parcel.chunkCount() > maxClaims) {
            return fail(Denial.AT_LIMIT, sample, ownerName);
        }
        List<Claim> all = inParcel(parcelId);
        if (!ClaimGates.allowParcelTransfer(actor, parcel, newOwner, ref)) {
            return fail(Denial.GATE, sample, ownerName);
        }
        for (Claim claim : all) {
            if (!ClaimGates.allowTransfer(actor, claim, newOwner, ref)) {
                return fail(Denial.GATE, claim, ownerName);
            }
        }
        for (Claim claim : all) {
            claim.setOwner(newOwner, ownerName);
            claim.clearMembers();
        }
        revision++;
        LandEvents.afterParcelTransfer(info(parcelId), actor, newOwner, ref);
        return ok(all.get(0));
    }

    /**
     * Give a parcel to someone else without the current owner acting.
     * For collateral seizure. {@code maxClaims} of 0 or less ignores the cap.
     * Player commands do not call this.
     */
    public Result seizeParcel(UUID parcelId, UUID newOwner, String newOwnerName, int maxClaims, String reference) {
        String ref = ref(reference);
        String ownerName = newOwnerName == null ? "" : newOwnerName;
        ParcelInfo parcel = info(parcelId);
        if (parcel == null) {
            return fail(Denial.PARCEL_NOT_FOUND, null, "");
        }
        Claim sample = claims.get(parcel.chunks().get(0));
        if (parcel.owner().equals(newOwner)) {
            return fail(Denial.SELF, sample, ownerName);
        }
        if (maxClaims > 0 && (long) countOwned(newOwner) + parcel.chunkCount() > maxClaims) {
            return fail(Denial.AT_LIMIT, sample, ownerName);
        }
        if (!ClaimGates.allowSeizeParcel(parcel, newOwner, ref)) {
            return fail(Denial.GATE, sample, ownerName);
        }
        UUID previous = parcel.owner();
        List<Claim> all = inParcel(parcelId);
        for (Claim claim : all) {
            claim.setOwner(newOwner, ownerName);
            claim.clearMembers();
        }
        revision++;
        LandEvents.afterSeizeParcel(info(parcelId), previous, newOwner, ref);
        return ok(all.get(0));
    }

    /**
     * Take one chunk. The last chunk of a parcel seizes that whole parcel.
     * Otherwise the chunk is carved into a new parcel for {@code newOwner}.
     */
    public Result seizeChunk(String dimension, int chunkX, int chunkZ, UUID newOwner, String newOwnerName, int maxClaims, String reference) {
        String ref = ref(reference);
        String ownerName = newOwnerName == null ? "" : newOwnerName;
        Claim claim = get(dimension, chunkX, chunkZ);
        if (claim == null) {
            return fail(Denial.NOT_CLAIMED, null, "");
        }
        if (claim.owner().equals(newOwner)) {
            return fail(Denial.SELF, claim, ownerName);
        }
        if (inParcel(claim.parcelId()).size() == 1) {
            return seizeParcel(claim.parcelId(), newOwner, ownerName, maxClaims, ref);
        }
        if (maxClaims > 0 && countOwned(newOwner) >= maxClaims) {
            return fail(Denial.AT_LIMIT, claim, ownerName);
        }
        ParcelInfo source = info(claim.parcelId());
        if (!ClaimGates.allowSeizeParcel(source, newOwner, ref) || !ClaimGates.allowSeizeChunk(claim, newOwner, ref)) {
            return fail(Denial.GATE, claim, ownerName);
        }
        UUID previousParcel = claim.parcelId();
        UUID previousOwner = claim.owner();
        Parcel created = createParcel("");
        claim.setParcel(created.id());
        claim.setName("");
        claim.setOwner(newOwner, ownerName);
        claim.clearMembers();
        revision++;
        LandEvents.afterSeizeChunk(claim, previousParcel, info(created.id()), previousOwner, newOwner, ref);
        return ok(claim);
    }

    public Result renameParcel(UUID actor, UUID parcelId, String rawName) {
        String name = ParcelNames.clean(rawName);
        if (name == null) {
            return fail(Denial.NAME_INVALID, null, "");
        }
        ParcelInfo current = info(parcelId);
        if (current == null) {
            return fail(Denial.PARCEL_NOT_FOUND, null, "");
        }
        Claim sample = claims.get(current.chunks().get(0));
        if (!current.owner().equals(actor)) {
            return fail(Denial.NOT_OWNER, sample, current.ownerName());
        }
        if (!ClaimGates.allowRename(actor, current, name)) {
            return fail(Denial.GATE, sample, "");
        }
        if (name.equals(current.name())) {
            return ok(sample);
        }
        Parcel parcel = parcels.get(parcelId);
        parcel.setName(name);
        for (Claim claim : inParcel(parcelId)) {
            claim.setName(name);
        }
        revision++;
        return ok(sample);
    }

    public Result merge(UUID actor, UUID targetId, UUID sourceId) {
        if (targetId == null || sourceId == null) {
            return fail(Denial.PARCEL_NOT_FOUND, null, "");
        }
        if (targetId.equals(sourceId)) {
            return fail(Denial.SAME_PARCEL, null, "");
        }
        ParcelInfo target = info(targetId);
        ParcelInfo source = info(sourceId);
        if (target == null || source == null) {
            return fail(Denial.PARCEL_NOT_FOUND, null, "");
        }
        if (!target.owner().equals(actor) || !source.owner().equals(actor)) {
            String ownerName = target.owner().equals(actor) ? source.ownerName() : target.ownerName();
            return fail(Denial.NOT_OWNER, null, ownerName);
        }
        if (!target.dimension().equals(source.dimension())) {
            return fail(Denial.DIFFERENT_DIMENSION, null, "");
        }
        List<Claim> targetClaims = inParcel(targetId);
        List<Claim> sourceClaims = List.copyOf(inParcel(sourceId));
        if (!touches(targetClaims, sourceClaims)) {
            return fail(Denial.NOT_ADJACENT, null, "");
        }
        if (!ClaimGates.allowMerge(actor, target, source)) {
            return fail(Denial.GATE, null, "");
        }
        absorb(parcels.get(targetId), parcels.get(sourceId));
        String name = parcels.get(targetId).name();
        for (Claim claim : sourceClaims) {
            claim.setParcel(targetId);
            claim.setName(name);
        }
        parcels.remove(sourceId);
        revision++;
        LandEvents.afterMerge(info(targetId), sourceId);
        return ok(sourceClaims.get(0));
    }

    /**
     * Carve these chunks into a new parcel still owned by {@code actor}.
     * At least one chunk has to stay behind. Selling the carved chunks is
     * {@link #transferChunks}; selling every chunk is {@link #transferParcel}.
     */
    public Result split(UUID actor, List<ClaimKey> keys, String rawName) {
        String name = ParcelNames.clean(rawName);
        if (name == null) {
            return fail(Denial.NAME_INVALID, null, "");
        }
        List<Claim> moving = resolve(keys);
        if (moving == null || moving.isEmpty()) {
            return fail(Denial.NOT_CLAIMED, null, "");
        }
        UUID parcelId = moving.get(0).parcelId();
        for (Claim claim : moving) {
            if (!claim.owner().equals(actor)) {
                return fail(Denial.NOT_OWNER, claim, claim.ownerName());
            }
            if (!claim.parcelId().equals(parcelId)) {
                return fail(Denial.MIXED_PARCEL, claim, "");
            }
        }
        if (moving.size() >= inParcel(parcelId).size()) {
            return fail(Denial.ENTIRE_PARCEL, moving.get(0), "");
        }
        ParcelInfo source = info(parcelId);
        if (!ClaimGates.allowSplit(actor, source, List.copyOf(moving))) {
            return fail(Denial.GATE, moving.get(0), "");
        }
        Parcel created = createParcel(name);
        for (Claim claim : moving) {
            claim.setParcel(created.id());
            claim.setName(name);
        }
        revision++;
        LandEvents.afterSplit(info(parcelId), info(created.id()));
        return ok(moving.get(0));
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
                    claim.name(),
                    claim.parcelId()));
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
        NbtList parcelList = new NbtList();
        List<Parcel> orderedParcels = new ArrayList<>(parcels.values());
        orderedParcels.sort(Comparator.comparing(parcel -> parcel.id().toString()));
        for (Parcel parcel : orderedParcels) {
            parcelList.add(writeParcel(parcel));
        }
        root.put("Parcels", parcelList);
        sales.prune(this);
        root.put("Sales", sales.toNbt());
        return root;
    }

    public static ClaimMap fromNbt(NbtCompound nbt) {
        ClaimMap map = new ClaimMap();
        if (nbt == null) {
            return map;
        }
        map.extraRoot = unknownKeys(nbt, ROOT_KEYS);
        int fileFormat = 0;
        if (nbt.contains("Format", NbtElement.INT_TYPE)) {
            fileFormat = nbt.getInt("Format");
            map.storedFormat = Math.max(FORMAT, fileFormat);
        }
        NbtList parcelList = nbt.getList("Parcels", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < parcelList.size(); i++) {
            Parcel parcel = readParcel(parcelList.getCompound(i));
            if (parcel != null) {
                map.parcels.putIfAbsent(parcel.id(), parcel);
            }
        }
        int missingParcelIds = 0;
        NbtList list = nbt.getList("Claims", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i);
            boolean hadParcel = tag.containsUuid("Parcel");
            Claim claim = readClaim(tag);
            if (claim == null) {
                continue;
            }
            if (map.claims.putIfAbsent(claim.key(), claim) == null && !hadParcel) {
                missingParcelIds++;
            }
        }
        map.normalizeParcels();
        map.sales.read(nbt.getList("Sales", NbtElement.COMPOUND_TYPE));
        if (fileFormat < FORMAT || missingParcelIds > 0) {
            map.rewriteRequired = true;
        }
        if (missingParcelIds > 0) {
            LandMod.LOGGER.info("Assigned parcel ids to {} existing claims", missingParcelIds);
        }
        return map;
    }

    private void normalizeParcels() {
        Map<UUID, List<Claim>> groups = new HashMap<>();
        for (Claim claim : claims.values()) {
            groups.computeIfAbsent(claim.parcelId(), id -> new ArrayList<>()).add(claim);
        }
        for (List<Claim> group : groups.values()) {
            group.sort(ORDER);
            Map<String, List<Claim>> parts = new LinkedHashMap<>();
            for (Claim claim : group) {
                parts.computeIfAbsent(partKey(claim), key -> new ArrayList<>()).add(claim);
            }
            if (parts.size() > 1) {
                rewriteRequired = true;
                String primary = partKey(group.get(0));
                Parcel original = ensureParcel(group.get(0).parcelId());
                for (Map.Entry<String, List<Claim>> part : parts.entrySet()) {
                    if (part.getKey().equals(primary)) {
                        continue;
                    }
                    Parcel split = createParcel(original.name());
                    for (Claim claim : part.getValue()) {
                        claim.setParcel(split.id());
                    }
                }
            } else {
                ensureParcel(group.get(0).parcelId());
            }
        }
        Map<UUID, List<Claim>> current = new HashMap<>();
        for (Claim claim : claims.values()) {
            current.computeIfAbsent(claim.parcelId(), id -> new ArrayList<>()).add(claim);
        }
        for (Map.Entry<UUID, List<Claim>> entry : current.entrySet()) {
            Parcel parcel = ensureParcel(entry.getKey());
            List<Claim> group = entry.getValue();
            group.sort(ORDER);
            if (parcel.name().isEmpty()) {
                for (Claim claim : group) {
                    if (!claim.name().isEmpty()) {
                        parcel.setName(claim.name());
                        rewriteRequired = true;
                        break;
                    }
                }
            }
            for (Claim claim : group) {
                if (!claim.name().equals(parcel.name())) {
                    claim.setName(parcel.name());
                    rewriteRequired = true;
                }
            }
        }
        parcels.entrySet().removeIf(entry ->
                inParcel(entry.getKey()).isEmpty()
                        && entry.getValue().flags().isEmpty()
                        && entry.getValue().storedExtra().isEmpty());
    }

    private static String partKey(Claim claim) {
        return claim.dimension() + "\0" + claim.owner();
    }

    private Claim place(String dimension, int chunkX, int chunkZ, Parcel parcel, UUID owner, String ownerName) {
        Claim claim = new Claim(dimension, chunkX, chunkZ, parcel.id(), owner, ownerName == null ? "" : ownerName,
                parcel.name(), new LinkedHashMap<>(), new NbtCompound(), new NbtCompound());
        claims.put(claim.key(), claim);
        return claim;
    }

    private Parcel createParcel(String name) {
        Parcel parcel = new Parcel(UUID.randomUUID(), name == null ? "" : name, new NbtCompound(), new NbtCompound());
        parcels.put(parcel.id(), parcel);
        return parcel;
    }

    private Parcel ensureParcel(UUID id) {
        Parcel parcel = parcels.get(id);
        if (parcel == null) {
            parcel = new Parcel(id, "", new NbtCompound(), new NbtCompound());
            parcels.put(id, parcel);
        }
        return parcel;
    }

    private static boolean adjacentTo(ParcelInfo parcel, int chunkX, int chunkZ) {
        for (ClaimKey key : parcel.chunks()) {
            if (ParcelGeometry.edgeAdjacent(key.chunkX(), key.chunkZ(), chunkX, chunkZ)) {
                return true;
            }
        }
        return false;
    }

    private static boolean touches(List<Claim> left, List<Claim> right) {
        Set<Long> spots = new HashSet<>();
        for (Claim claim : left) {
            spots.add(ParcelGeometry.pack(claim.chunkX(), claim.chunkZ()));
        }
        for (Claim claim : right) {
            int x = claim.chunkX();
            int z = claim.chunkZ();
            if (spots.contains(ParcelGeometry.pack(x + 1, z))
                    || spots.contains(ParcelGeometry.pack(x - 1, z))
                    || spots.contains(ParcelGeometry.pack(x, z + 1))
                    || spots.contains(ParcelGeometry.pack(x, z - 1))) {
                return true;
            }
        }
        return false;
    }

    private void absorb(Parcel target, Parcel source) {
        NbtCompound displaced = new NbtCompound();
        for (String key : source.flags().getKeys()) {
            NbtElement incoming = source.flags().get(key);
            if (incoming == null) {
                continue;
            }
            if (!target.flags().contains(key)) {
                target.flags().put(key, incoming.copy());
            } else if (!incoming.equals(target.flags().get(key))) {
                displaced.put(key, incoming.copy());
            }
        }
        boolean namedApart = !source.name().isEmpty() && !source.name().equals(target.name());
        if (!namedApart && displaced.isEmpty() && source.storedExtra().isEmpty()) {
            return;
        }
        NbtCompound record = new NbtCompound();
        record.putUuid("Id", source.id());
        if (!source.name().isEmpty()) {
            record.putString("Name", source.name());
        }
        if (!displaced.isEmpty()) {
            record.put("Flags", displaced);
        }
        if (!source.storedExtra().isEmpty()) {
            record.put("Extra", source.storedExtra().copy());
        }
        stash(target.storedExtra(), record);
    }

    private static void stash(NbtCompound extra, NbtCompound record) {
        if (extra.contains("Absorbed", NbtElement.LIST_TYPE)) {
            NbtList existing = extra.getList("Absorbed", NbtElement.COMPOUND_TYPE);
            if (existing == extra.get("Absorbed")) {
                existing.add(record);
                return;
            }
        }
        if (!extra.contains("Absorbed")) {
            NbtList created = new NbtList();
            created.add(record);
            extra.put("Absorbed", created);
            return;
        }
        extra.put("Absorbed_" + record.getUuid("Id").toString().replace("-", ""), record);
    }

    private @Nullable List<Claim> resolve(List<ClaimKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<ClaimKey> unique = new LinkedHashSet<>();
        for (ClaimKey key : keys) {
            unique.add(java.util.Objects.requireNonNull(key, "chunk"));
        }
        List<Claim> resolved = new ArrayList<>(unique.size());
        for (ClaimKey key : unique) {
            Claim claim = claims.get(key);
            if (claim == null) {
                return null;
            }
            resolved.add(claim);
        }
        return resolved;
    }

    private static String ref(String reference) {
        return reference == null ? "" : reference;
    }

    private static NbtCompound writeClaim(Claim claim) {
        NbtCompound tag = claim.storedExtra().copy();
        tag.putString("Dim", claim.dimension());
        tag.putInt("X", claim.chunkX());
        tag.putInt("Z", claim.chunkZ());
        tag.putUuid("Parcel", claim.parcelId());
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

    private static NbtCompound writeParcel(Parcel parcel) {
        NbtCompound tag = parcel.storedExtra().copy();
        tag.putUuid("Id", parcel.id());
        tag.putString("Name", parcel.name());
        tag.put("Flags", parcel.flags().copy());
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
        UUID parcelId = tag.containsUuid("Parcel") ? tag.getUuid("Parcel") : UUID.randomUUID();
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
                parcelId,
                owner,
                tag.getString("OwnerName"),
                tag.getString("Name"),
                members,
                flags,
                unknownKeys(tag, CLAIM_KEYS));
    }

    private static @Nullable Parcel readParcel(NbtCompound tag) {
        if (!tag.containsUuid("Id")) {
            return null;
        }
        NbtCompound flags = tag.contains("Flags", NbtElement.COMPOUND_TYPE)
                ? tag.getCompound("Flags").copy()
                : new NbtCompound();
        return new Parcel(tag.getUuid("Id"), tag.getString("Name"), flags, unknownKeys(tag, PARCEL_KEYS));
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

    static Result ok(Claim claim) {
        return new Result(Denial.NONE, claim, "");
    }

    static Result fail(Denial denial, @Nullable Claim claim, String subject) {
        return new Result(denial, claim, subject == null ? "" : subject);
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
        GATE,
        PARCEL_NOT_FOUND,
        NOT_ADJACENT,
        DIFFERENT_DIMENSION,
        SAME_PARCEL,
        ENTIRE_PARCEL,
        MIXED_PARCEL,
        NAME_INVALID,
        NOT_FOR_SALE,
        SALE_STALE,
        WHOLE_ONLY
    }

    public record Result(Denial denial, @Nullable Claim claim, String subject) {
        public boolean succeeded() {
            return denial == Denial.NONE;
        }
    }
}

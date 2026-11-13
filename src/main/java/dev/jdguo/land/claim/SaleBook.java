package dev.jdguo.land.claim;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One offer per parcel. A parcel offer sells every chunk together.
 * A chunk offer sells only the listed chunks, which are split off on purchase.
 * There is no price here; a bank gate can still veto the transfer.
 */
public final class SaleBook {
    private static final Set<String> SALE_KEYS = Set.of("Id", "Kind", "Parcel", "Seller", "SellerName", "Chunks");

    private final Map<UUID, Listing> byParcel = new LinkedHashMap<>();
    private int revision = 1;

    public int revision() {
        return revision;
    }

    public @Nullable SaleInfo find(UUID parcelId) {
        Listing listing = byParcel.get(parcelId);
        return listing == null ? null : listing.info();
    }

    public List<SaleInfo> list() {
        List<SaleInfo> sales = new ArrayList<>();
        for (Listing listing : byParcel.values()) {
            sales.add(listing.info());
        }
        sales.sort(Comparator.comparing(sale -> sale.id().toString()));
        return sales;
    }

    public ClaimMap.Result sellParcel(ClaimMap map, UUID seller, String sellerName, UUID parcelId) {
        ParcelInfo parcel = map.info(parcelId);
        if (parcel == null) {
            return ClaimMap.fail(ClaimMap.Denial.PARCEL_NOT_FOUND, null, "");
        }
        if (!parcel.owner().equals(seller)) {
            return ClaimMap.fail(ClaimMap.Denial.NOT_OWNER, map.get(parcel.chunks().get(0).dimension(), parcel.chunks().get(0).chunkX(), parcel.chunks().get(0).chunkZ()), parcel.ownerName());
        }
        put(parcelId, SaleKind.PARCEL, seller, displayName(parcel.ownerName(), sellerName), new ArrayList<>(parcel.chunks()));
        return ClaimMap.ok(map.get(parcel.chunks().get(0).dimension(), parcel.chunks().get(0).chunkX(), parcel.chunks().get(0).chunkZ()));
    }

    public ClaimMap.Result sellChunks(ClaimMap map, UUID seller, String sellerName, List<ClaimKey> keys) {
        List<Claim> claims = resolve(map, keys);
        if (claims == null) {
            return ClaimMap.fail(ClaimMap.Denial.NOT_CLAIMED, null, "");
        }
        if (claims.isEmpty()) {
            return ClaimMap.fail(ClaimMap.Denial.NOT_CLAIMED, null, "");
        }
        UUID parcelId = claims.get(0).parcelId();
        for (Claim claim : claims) {
            if (!claim.owner().equals(seller)) {
                return ClaimMap.fail(ClaimMap.Denial.NOT_OWNER, claim, claim.ownerName());
            }
            if (!claim.parcelId().equals(parcelId)) {
                return ClaimMap.fail(ClaimMap.Denial.MIXED_PARCEL, claim, "");
            }
        }
        List<ClaimKey> chunkKeys = new ArrayList<>();
        for (Claim claim : claims) {
            chunkKeys.add(claim.key());
        }
        ParcelInfo parcel = map.info(parcelId);
        if (parcel != null && same(chunkKeys, parcel.chunks())) {
            return sellParcel(map, seller, sellerName, parcelId);
        }
        put(parcelId, SaleKind.CHUNKS, seller, displayName(claims.get(0).ownerName(), sellerName), chunkKeys);
        return ClaimMap.ok(claims.get(0));
    }

    public ClaimMap.Result cancel(ClaimMap map, UUID seller, UUID parcelId) {
        Listing listing = byParcel.get(parcelId);
        if (listing == null) {
            return ClaimMap.fail(ClaimMap.Denial.NOT_FOR_SALE, null, "");
        }
        if (!listing.seller.equals(seller)) {
            return ClaimMap.fail(ClaimMap.Denial.NOT_OWNER, null, listing.sellerName);
        }
        byParcel.remove(parcelId);
        revision++;
        Claim claim = map.get(listing.chunks.get(0).dimension(), listing.chunks.get(0).chunkX(), listing.chunks.get(0).chunkZ());
        return ClaimMap.ok(claim);
    }

    public ClaimMap.Result buyParcel(ClaimMap map, UUID buyer, String buyerName, UUID parcelId, int maxClaims) {
        Listing listing = byParcel.get(parcelId);
        if (listing == null || listing.kind != SaleKind.PARCEL) {
            return ClaimMap.fail(ClaimMap.Denial.NOT_FOR_SALE, null, "");
        }
        if (listing.seller.equals(buyer)) {
            return ClaimMap.fail(ClaimMap.Denial.SELF, null, listing.sellerName);
        }
        ParcelInfo parcel = map.info(parcelId);
        if (parcel == null || !parcel.owner().equals(listing.seller) || !same(listing.chunks, parcel.chunks())) {
            byParcel.remove(parcelId);
            revision++;
            return ClaimMap.fail(ClaimMap.Denial.SALE_STALE, null, "");
        }
        ClaimMap.Result result = map.transferParcel(listing.seller, parcelId, buyer, buyerName, maxClaims, reference(listing));
        if (result.succeeded()) {
            byParcel.remove(parcelId);
            revision++;
        }
        return result;
    }

    public ClaimMap.Result buyChunks(ClaimMap map, UUID buyer, String buyerName, List<ClaimKey> keys, int maxClaims) {
        List<ClaimKey> wanted = unique(keys);
        if (wanted.isEmpty()) {
            return ClaimMap.fail(ClaimMap.Denial.NOT_CLAIMED, null, "");
        }
        Claim first = map.get(wanted.get(0).dimension(), wanted.get(0).chunkX(), wanted.get(0).chunkZ());
        if (first == null) {
            return ClaimMap.fail(ClaimMap.Denial.NOT_CLAIMED, null, "");
        }
        Listing listing = byParcel.get(first.parcelId());
        if (listing == null) {
            return ClaimMap.fail(ClaimMap.Denial.NOT_FOR_SALE, first, "");
        }
        if (listing.kind == SaleKind.PARCEL) {
            if (same(wanted, listing.chunks)) {
                return buyParcel(map, buyer, buyerName, listing.parcelId, maxClaims);
            }
            return ClaimMap.fail(ClaimMap.Denial.WHOLE_ONLY, first, "");
        }
        if (listing.seller.equals(buyer)) {
            return ClaimMap.fail(ClaimMap.Denial.SELF, first, listing.sellerName);
        }
        if (!containsAll(listing.chunks, wanted)) {
            return ClaimMap.fail(ClaimMap.Denial.NOT_FOR_SALE, first, "");
        }
        for (ClaimKey key : wanted) {
            Claim claim = map.get(key.dimension(), key.chunkX(), key.chunkZ());
            if (claim == null || !claim.owner().equals(listing.seller) || !claim.parcelId().equals(listing.parcelId)) {
                prune(map);
                return ClaimMap.fail(ClaimMap.Denial.SALE_STALE, claim, "");
            }
        }
        ClaimMap.Result result = map.transferChunks(listing.seller, wanted, buyer, buyerName, maxClaims, reference(listing));
        if (result.succeeded()) {
            listing.chunks.removeAll(wanted);
            if (listing.chunks.isEmpty()) {
                byParcel.remove(listing.parcelId);
            }
            revision++;
        }
        return result;
    }

    public void prune(ClaimMap map) {
        List<UUID> drop = new ArrayList<>();
        for (Listing listing : byParcel.values()) {
            if (listing.kind == SaleKind.PARCEL) {
                ParcelInfo parcel = map.info(listing.parcelId);
                if (parcel == null || !parcel.owner().equals(listing.seller) || !same(listing.chunks, parcel.chunks())) {
                    drop.add(listing.parcelId);
                }
                continue;
            }
            listing.chunks.removeIf(key -> {
                Claim claim = map.get(key.dimension(), key.chunkX(), key.chunkZ());
                return claim == null || !claim.owner().equals(listing.seller) || !claim.parcelId().equals(listing.parcelId);
            });
            if (listing.chunks.isEmpty()) {
                drop.add(listing.parcelId);
            }
        }
        if (!drop.isEmpty()) {
            drop.forEach(byParcel::remove);
            revision++;
        }
    }

    public NbtList toNbt() {
        NbtList list = new NbtList();
        List<Listing> ordered = new ArrayList<>(byParcel.values());
        ordered.sort(Comparator.comparing(listing -> listing.id.toString()));
        for (Listing listing : ordered) {
            list.add(write(listing));
        }
        return list;
    }

    public void read(NbtList list) {
        byParcel.clear();
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Listing listing = readOne(list.getCompound(i));
            if (listing != null) {
                byParcel.putIfAbsent(listing.parcelId, listing);
            }
        }
    }

    private void put(UUID parcelId, SaleKind kind, UUID seller, String sellerName, List<ClaimKey> chunks) {
        Listing listing = new Listing();
        listing.id = UUID.randomUUID();
        listing.kind = kind;
        listing.parcelId = parcelId;
        listing.seller = seller;
        listing.sellerName = sellerName;
        listing.chunks = new ArrayList<>(chunks);
        listing.chunks.sort(CHUNK_ORDER);
        listing.extra = new NbtCompound();
        byParcel.put(parcelId, listing);
        revision++;
    }

    private static String reference(Listing listing) {
        return "sale:" + listing.id;
    }

    private static String displayName(String ownerName, String fallback) {
        if (ownerName != null && !ownerName.isBlank()) {
            return ownerName;
        }
        return fallback == null ? "" : fallback;
    }

    private static @Nullable List<Claim> resolve(ClaimMap map, List<ClaimKey> keys) {
        List<ClaimKey> unique = unique(keys);
        if (unique.isEmpty()) {
            return List.of();
        }
        List<Claim> claims = new ArrayList<>();
        for (ClaimKey key : unique) {
            Claim claim = map.get(key.dimension(), key.chunkX(), key.chunkZ());
            if (claim == null) {
                return null;
            }
            claims.add(claim);
        }
        return claims;
    }

    private static List<ClaimKey> unique(List<ClaimKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        Set<ClaimKey> seen = new HashSet<>();
        List<ClaimKey> unique = new ArrayList<>();
        for (ClaimKey key : keys) {
            if (key != null && seen.add(key)) {
                unique.add(key);
            }
        }
        return unique;
    }

    private static boolean same(List<ClaimKey> left, List<ClaimKey> right) {
        return left.size() == right.size() && containsAll(left, right);
    }

    private static boolean containsAll(List<ClaimKey> haystack, List<ClaimKey> needles) {
        return new HashSet<>(haystack).containsAll(needles);
    }

    private static NbtCompound write(Listing listing) {
        NbtCompound tag = listing.extra.copy();
        tag.putUuid("Id", listing.id);
        tag.putString("Kind", listing.kind == SaleKind.PARCEL ? "parcel" : "chunks");
        tag.putUuid("Parcel", listing.parcelId);
        tag.putUuid("Seller", listing.seller);
        tag.putString("SellerName", listing.sellerName);
        NbtList chunks = new NbtList();
        List<ClaimKey> ordered = new ArrayList<>(listing.chunks);
        ordered.sort(CHUNK_ORDER);
        for (ClaimKey key : ordered) {
            NbtCompound chunk = new NbtCompound();
            chunk.putString("Dim", key.dimension());
            chunk.putInt("X", key.chunkX());
            chunk.putInt("Z", key.chunkZ());
            chunks.add(chunk);
        }
        tag.put("Chunks", chunks);
        return tag;
    }

    private static @Nullable Listing readOne(NbtCompound tag) {
        if (!tag.containsUuid("Id") || !tag.containsUuid("Parcel") || !tag.containsUuid("Seller")) {
            return null;
        }
        String kindName = tag.getString("Kind");
        SaleKind kind = "parcel".equals(kindName) ? SaleKind.PARCEL : "chunks".equals(kindName) ? SaleKind.CHUNKS : null;
        if (kind == null) {
            return null;
        }
        NbtList chunkList = tag.getList("Chunks", NbtElement.COMPOUND_TYPE);
        List<ClaimKey> chunks = new ArrayList<>();
        for (int i = 0; i < chunkList.size(); i++) {
            NbtCompound chunk = chunkList.getCompound(i);
            if (!chunk.contains("Dim", NbtElement.STRING_TYPE) || !chunk.contains("X", NbtElement.INT_TYPE) || !chunk.contains("Z", NbtElement.INT_TYPE)) {
                continue;
            }
            chunks.add(new ClaimKey(chunk.getString("Dim"), chunk.getInt("X"), chunk.getInt("Z")));
        }
        if (chunks.isEmpty()) {
            return null;
        }
        Listing listing = new Listing();
        listing.id = tag.getUuid("Id");
        listing.kind = kind;
        listing.parcelId = tag.getUuid("Parcel");
        listing.seller = tag.getUuid("Seller");
        listing.sellerName = tag.getString("SellerName");
        listing.chunks = chunks;
        listing.extra = unknown(tag);
        return listing;
    }

    private static NbtCompound unknown(NbtCompound source) {
        NbtCompound extra = new NbtCompound();
        for (String key : source.getKeys()) {
            if (!SALE_KEYS.contains(key)) {
                NbtElement value = source.get(key);
                if (value != null) {
                    extra.put(key, value.copy());
                }
            }
        }
        return extra;
    }

    private static final Comparator<ClaimKey> CHUNK_ORDER = Comparator
            .comparing(ClaimKey::dimension)
            .thenComparingInt(ClaimKey::chunkX)
            .thenComparingInt(ClaimKey::chunkZ);

    private static final class Listing {
        private UUID id;
        private SaleKind kind;
        private UUID parcelId;
        private UUID seller;
        private String sellerName;
        private List<ClaimKey> chunks;
        private NbtCompound extra;

        private SaleInfo info() {
            return new SaleInfo(id, kind, parcelId, seller, sellerName, List.copyOf(chunks));
        }
    }
}

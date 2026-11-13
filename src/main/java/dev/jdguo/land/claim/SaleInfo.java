package dev.jdguo.land.claim;

import java.util.List;
import java.util.UUID;

/**
 * One land offer. {@link SaleKind#PARCEL} is the whole parcel.
 * {@link SaleKind#CHUNKS} is only the listed 16×16 chunks.
 */
public record SaleInfo(UUID id, SaleKind kind, UUID parcelId, UUID seller, String sellerName, List<ClaimKey> chunks) {
    public SaleInfo {
        chunks = List.copyOf(chunks);
        sellerName = sellerName == null ? "" : sellerName;
    }
}

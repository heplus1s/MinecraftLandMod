package dev.jdguo.land.client;
import java.util.Objects;
import java.util.UUID;
public final class ClaimColorBoundary {
    private ClaimColorBoundary() {}
    public static boolean isBoundary(UUID parcel, UUID sale, UUID neighborParcel, UUID neighborSale) {
        return parcel==null || neighborParcel==null || !parcel.equals(neighborParcel) || !Objects.equals(sale,neighborSale);
    }
}

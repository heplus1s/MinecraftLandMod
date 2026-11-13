package dev.jdguo.land.ui;

import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.claim.SaleInfo;
import dev.jdguo.land.claim.SaleKind;
import dev.jdguo.land.network.SaleSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * Offers the server has listed. Replaced as a whole when a sales packet arrives.
 */
public final class ClientSales {
    private static volatile View view = View.EMPTY;

    private ClientSales() {
    }

    public static void replace(SaleSnapshot snapshot) {
        view = View.from(snapshot);
    }

    public static SaleInfo find(String dimension, int chunkX, int chunkZ) {
        return view.byChunk.get(new ClaimKey(dimension, chunkX, chunkZ));
    }

    public static boolean forSale(String dimension, int chunkX, int chunkZ) {
        return find(dimension, chunkX, chunkZ) != null;
    }

    private static final class View {
        private static final View EMPTY = new View(Map.of());

        private final Map<ClaimKey, SaleInfo> byChunk;

        private View(Map<ClaimKey, SaleInfo> byChunk) {
            this.byChunk = byChunk;
        }

        private static View from(SaleSnapshot snapshot) {
            Map<ClaimKey, SaleInfo> byChunk = new HashMap<>();
            for (SaleInfo sale : snapshot.sales()) {
                for (ClaimKey key : sale.chunks()) {
                    byChunk.put(key, sale);
                }
            }
            return new View(byChunk);
        }
    }

    public static boolean wholeParcel(SaleInfo sale) {
        return sale != null && sale.kind() == SaleKind.PARCEL;
    }
}

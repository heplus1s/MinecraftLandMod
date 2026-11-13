package dev.jdguo.land.trade;

import dev.jdguo.land.claim.ClaimMap;
import dev.jdguo.land.config.LandConfig;
import dev.jdguo.land.message.LandMessages;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Chinese replies for a sale, whether it came from a command or the map UI.
 */
public final class SaleChat {
    public enum Verb {
        SELL_PARCEL,
        SELL_CHUNKS,
        CANCEL,
        BUY_PARCEL,
        BUY_CHUNKS
    }

    private SaleChat() {
    }

    public static int reply(ServerPlayerEntity actor, ClaimMap.Result result, Verb verb, int chunks, @Nullable UUID sellerId, @Nullable String sellerName) {
        say(actor, actorLine(result, verb, chunks, sellerName));
        if (result.succeeded() && sellerId != null && actor.getServer() != null && !sellerId.equals(actor.getUuid())) {
            ServerPlayerEntity seller = actor.getServer().getPlayerManager().getPlayer(sellerId);
            if (seller != null) {
                String buyer = LandMessages.who(actor.getGameProfile().getName());
                if (verb == Verb.BUY_PARCEL) {
                    say(seller, buyer + " 买下了你的宗地（" + chunks + " 块）。");
                } else if (verb == Verb.BUY_CHUNKS) {
                    say(seller, buyer + " 买下了你标出的 " + chunks + " 块。");
                }
            }
        }
        return result.succeeded() ? 1 : 0;
    }

    private static String actorLine(ClaimMap.Result result, Verb verb, int chunks, @Nullable String sellerName) {
        if (result.succeeded()) {
            return switch (verb) {
                case SELL_PARCEL -> "已将宗地标为出售（" + chunks + " 块）。";
                case SELL_CHUNKS -> "已将 " + chunks + " 块标为出售。";
                case CANCEL -> "已取消出售。";
                case BUY_PARCEL -> "已买下这块宗地（" + chunks + " 块）。";
                case BUY_CHUNKS -> "已买下 " + chunks + " 块。";
            };
        }
        String who = LandMessages.who(sellerName == null ? result.subject() : sellerName);
        return switch (result.denial()) {
            case NOT_CLAIMED -> "此处没有领地。";
            case NOT_OWNER -> "你不是这块宗地的主人。";
            case PARCEL_NOT_FOUND -> "没有这块宗地。";
            case NOT_FOR_SALE -> "这块地没有在出售。";
            case SALE_STALE -> "这块宗地已经变了，出售已取消。";
            case WHOLE_ONLY -> "这块宗地要整块购买。";
            case SELF -> "不能买自己的地。";
            case MIXED_PARCEL -> "这些区块不在同一块宗地里。";
            case AT_LIMIT -> "领地数量已达上限（" + LandConfig.maxClaimsPerPlayer() + "）。";
            case GATE -> "现在不能购买。";
            case OWNED_BY_OTHER -> "此区块已被 " + who + " 占领。";
            default -> "现在不能购买。";
        };
    }

    private static void say(ServerPlayerEntity player, String body) {
        player.sendMessage(LandMessages.chat(body), false);
    }
}

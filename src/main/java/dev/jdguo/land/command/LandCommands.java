package dev.jdguo.land.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.jdguo.land.claim.Claim;
import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.claim.ClaimMap;
import dev.jdguo.land.claim.Member;
import dev.jdguo.land.claim.ParcelInfo;
import dev.jdguo.land.config.LandConfig;
import dev.jdguo.land.message.LandMessages;
import dev.jdguo.land.world.ClaimSavedData;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LandCommands {
    private static final int LIST_LIMIT = 100;
    private static final int CHUNK_PRINT_LIMIT = 30;

    private LandCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register(LandCommands::register);
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registry, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("land")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .executes(LandCommands::usage)
                .then(CommandManager.literal("claim")
                        .executes(LandCommands::claim)
                        .then(CommandManager.literal("join")
                                .executes(LandCommands::claimJoinAdjacent)
                                .then(CommandManager.argument("parcel", StringArgumentType.greedyString())
                                        .suggests(LandCommands::suggestParcels)
                                        .executes(LandCommands::claimJoinNamed))))
                .then(CommandManager.literal("abandon").executes(LandCommands::abandon))
                .then(CommandManager.literal("info").executes(LandCommands::info))
                .then(CommandManager.literal("list").executes(LandCommands::list))
                .then(CommandManager.literal("trust")
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                .executes(context -> trust(context, true))))
                .then(CommandManager.literal("untrust")
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                .executes(context -> trust(context, false))))
                .then(CommandManager.literal("transfer")
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                .executes(LandCommands::transfer)))
                .then(CommandManager.literal("mark")
                        .executes(LandCommands::markToggle)
                        .then(CommandManager.literal("clear").executes(LandCommands::markClear))
                        .then(CommandManager.literal("list").executes(LandCommands::markList)))
                .then(CommandManager.literal("split")
                        .executes(context -> split(context, ""))
                        .then(CommandManager.literal("transfer")
                                .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                        .executes(LandCommands::splitTransfer)))
                        .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                .executes(context -> split(context, StringArgumentType.getString(context, "name")))))
                .then(CommandManager.literal("parcel")
                        .executes(LandCommands::parcelUsage)
                        .then(CommandManager.literal("list").executes(LandCommands::parcelList))
                        .then(CommandManager.literal("info")
                                .executes(LandCommands::parcelInfoStanding)
                                .then(CommandManager.argument("parcel", StringArgumentType.greedyString())
                                        .suggests(LandCommands::suggestParcels)
                                        .executes(LandCommands::parcelInfoNamed)))
                        .then(CommandManager.literal("rename")
                                .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                        .executes(LandCommands::rename)))
                        .then(CommandManager.literal("merge")
                                .then(CommandManager.argument("parcel", StringArgumentType.greedyString())
                                        .suggests(LandCommands::suggestParcels)
                                        .executes(LandCommands::merge)))
                        .then(CommandManager.literal("transfer")
                                .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                        .executes(LandCommands::transferParcel))))
                .then(CommandManager.literal("area")
                        .executes(context -> area(context, null))
                        .then(CommandManager.argument("parcel", StringArgumentType.greedyString())
                                .suggests(LandCommands::suggestParcels)
                                .executes(context -> area(context, StringArgumentType.getString(context, "parcel")))))
                .then(CommandManager.literal("sell")
                        .executes(LandCommands::sellUsage)
                        .then(CommandManager.literal("parcel").executes(LandCommands::sellParcel))
                        .then(CommandManager.literal("chunks").executes(LandCommands::sellChunks))
                        .then(CommandManager.literal("cancel").executes(LandCommands::sellCancel)))
                .then(CommandManager.literal("buy")
                        .executes(LandCommands::buyStanding)
                        .then(CommandManager.literal("parcel").executes(LandCommands::buyParcel))
                        .then(CommandManager.literal("chunks").executes(LandCommands::buyChunks))));
    }

    static String parcelSuggestionTooltip(ParcelInfo info) {
        if (info.chunks().isEmpty()) {
            return info.chunkCount() + " chunks";
        }
        ClaimKey at = info.chunks().get(0);
        return info.chunkCount() + " chunks @ " + at.chunkX() + " " + at.chunkZ();
    }

    private static CompletableFuture<Suggestions> suggestParcels(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            return Suggestions.empty();
        }
        ClaimMap map = ClaimSavedData.get(player.getServer()).claims();
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        String compact = remaining.replace("-", "");
        for (ParcelInfo info : map.parcelsOwnedBy(player.getUuid())) {
            String id = info.id().toString();
            String idCompact = id.replace("-", "");
            if (!remaining.isEmpty() && !id.startsWith(remaining) && !idCompact.startsWith(compact)) {
                continue;
            }
            builder.suggest(id, new LiteralMessage(parcelSuggestionTooltip(info)));
        }
        return builder.buildFuture();
    }

    private static int usage(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        player(context).sendMessage(Text.literal(LandMessages.USAGE), false);
        return 0;
    }

    private static int parcelUsage(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        player(context).sendMessage(Text.literal(LandMessages.PARCEL_USAGE), false);
        return 0;
    }

    private static int sellUsage(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        player(context).sendMessage(Text.literal(LandMessages.SELL_USAGE), false);
        return 0;
    }

    private static int sellParcel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        Claim claim = standingClaim(player, data);
        if (claim == null) {
            return 0;
        }
        return data.sellParcel(player, claim.parcelId()).succeeded() ? 1 : 0;
    }

    private static int sellChunks(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        boolean marked = ChunkMarks.size(player.getUuid()) > 0;
        List<ClaimKey> keys = markedOrStanding(player);
        ClaimMap.Result result = ClaimSavedData.get(player.getServer()).sellChunks(player, keys);
        if (result.succeeded() && marked) {
            ChunkMarks.clear(player.getUuid());
        }
        return result.succeeded() ? 1 : 0;
    }

    private static int sellCancel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        Claim claim = standingClaim(player, data);
        if (claim == null) {
            return 0;
        }
        return data.cancelSale(player, claim.parcelId()).succeeded() ? 1 : 0;
    }

    private static int buyStanding(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return ClaimSavedData.get(player(context).getServer()).buyStanding(player(context)).succeeded() ? 1 : 0;
    }

    private static int buyParcel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        Claim claim = standingClaim(player, data);
        if (claim == null) {
            return 0;
        }
        return data.buyParcel(player, claim.parcelId()).succeeded() ? 1 : 0;
    }

    private static int buyChunks(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        boolean marked = ChunkMarks.size(player.getUuid()) > 0;
        List<ClaimKey> keys = markedOrStanding(player);
        ClaimMap.Result result = ClaimSavedData.get(player.getServer()).buyChunks(player, keys);
        if (result.succeeded() && marked) {
            ChunkMarks.clear(player.getUuid());
        }
        return result.succeeded() ? 1 : 0;
    }

    private static Claim standingClaim(ServerPlayerEntity player, ClaimSavedData data) {
        data.touch(player);
        ClaimKey key = ClaimSavedData.standing(player);
        Claim claim = data.claims().get(key.dimension(), key.chunkX(), key.chunkZ());
        if (claim == null) {
            say(player, "此处没有领地。");
            return null;
        }
        return claim;
    }

    private static int claim(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimMap.Result result = ClaimSavedData.get(player.getServer()).claim(player, LandConfig.maxClaimsPerPlayer());
        switch (result.denial()) {
            case NONE -> say(player, "已占领此区块。");
            case ALREADY_OWNED -> say(player, "你已经占领了此区块。");
            case OWNED_BY_OTHER -> say(player, "此区块已被 " + LandMessages.who(result.subject()) + " 占领。");
            case AT_LIMIT -> say(player, "领地数量已达上限（" + LandConfig.maxClaimsPerPlayer() + "）。");
            case GATE -> say(player, "现在不能占领此区块。");
            default -> say(player, "现在不能占领此区块。");
        }
        return result.succeeded() ? 1 : 0;
    }

    private static int claimJoinAdjacent(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        data.touch(player);
        ClaimKey key = ClaimSavedData.standing(player);
        List<ParcelInfo> adjacent = data.claims().adjacentOwnedParcels(player.getUuid(), key.dimension(), key.chunkX(), key.chunkZ());
        if (adjacent.isEmpty()) {
            say(player, "旁边没有可以并入的宗地。");
            return 0;
        }
        if (adjacent.size() > 1) {
            say(player, "旁边有多块宗地，请指定名称或编号。");
            for (ParcelInfo info : adjacent) {
                say(player, label(info) + " · " + info.id());
            }
            return 0;
        }
        return finishJoin(player, data, adjacent.get(0).id());
    }

    private static int claimJoinNamed(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        data.touch(player);
        ParcelInfo parcel = ownedParcel(player, data.claims(), StringArgumentType.getString(context, "parcel"));
        if (parcel == null) {
            return 0;
        }
        return finishJoin(player, data, parcel.id());
    }

    private static int finishJoin(ServerPlayerEntity player, ClaimSavedData data, UUID parcelId) {
        ClaimMap.Result result = data.claimJoin(player, parcelId, LandConfig.maxClaimsPerPlayer());
        switch (result.denial()) {
            case NONE -> {
                ParcelInfo info = data.claims().info(parcelId);
                say(player, "已将此区块并入" + label(info) + "，现为 " + (info == null ? 1 : info.chunkCount()) + " 块。");
            }
            case ALREADY_OWNED -> say(player, "你已经占领了此区块。");
            case OWNED_BY_OTHER -> say(player, "此区块已被 " + LandMessages.who(result.subject()) + " 占领。");
            case NOT_OWNER -> say(player, "你不是这块宗地的主人。");
            case NOT_ADJACENT -> say(player, "这块宗地和脚下的区块不相邻。");
            case DIFFERENT_DIMENSION -> say(player, "只能并入同一维度的宗地。");
            case PARCEL_NOT_FOUND -> say(player, "没有这块宗地。");
            case AT_LIMIT -> say(player, "领地数量已达上限（" + LandConfig.maxClaimsPerPlayer() + "）。");
            case GATE -> say(player, "现在不能并入这块宗地。");
            default -> say(player, "现在不能并入这块宗地。");
        }
        return result.succeeded() ? 1 : 0;
    }

    private static int abandon(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimKey key = ClaimSavedData.standing(player);
        ClaimMap.Result result = ClaimSavedData.get(player.getServer()).abandon(player);
        if (result.succeeded()) {
            ChunkMarks.remove(player.getUuid(), key);
        }
        switch (result.denial()) {
            case NONE -> say(player, "已放弃此区块。");
            case NOT_CLAIMED -> say(player, "此处没有领地。");
            case NOT_OWNER -> say(player, "你不是这块领地的主人。");
            case GATE -> say(player, "现在不能放弃此区块。");
            default -> say(player, "现在不能放弃此区块。");
        }
        return result.succeeded() ? 1 : 0;
    }

    private static int info(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        data.touch(player);
        ClaimKey key = ClaimSavedData.standing(player);
        Claim claim = data.claims().get(key.dimension(), key.chunkX(), key.chunkZ());
        if (claim == null) {
            say(player, "此处没有领地。");
            return 0;
        }
        ParcelInfo parcel = data.claims().info(claim.parcelId());
        sayParcelHeader(player, parcel);
        say(player, "此区块成员：" + memberList(claim));
        say(player, "位置：" + LandMessages.dimension(claim.dimension()) + " 区块 (" + claim.chunkX() + ", " + claim.chunkZ() + ")");
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        data.touch(player);
        List<Claim> owned = data.claims().ownedBy(player.getUuid());
        if (owned.isEmpty()) {
            say(player, "你还没有领地。");
            return 0;
        }
        say(player, "你共有 " + owned.size() + " 块领地：");
        int shown = 0;
        for (Claim claim : owned) {
            if (shown == LIST_LIMIT) {
                say(player, "还有 " + (owned.size() - shown) + " 块没有列出。");
                break;
            }
            ParcelInfo parcel = data.claims().info(claim.parcelId());
            String line = LandMessages.dimension(claim.dimension()) + " 区块 (" + claim.chunkX() + ", " + claim.chunkZ() + ")";
            if (parcel != null && (!parcel.name().isBlank() || parcel.chunkCount() > 1)) {
                line += " · " + label(parcel);
            }
            say(player, line);
            shown++;
        }
        return 1;
    }

    private static int trust(CommandContext<ServerCommandSource> context, boolean add) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        GameProfile profile = oneProfile(context, player);
        if (profile == null) {
            return 0;
        }
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        ClaimMap.Result result = add
                ? data.trust(player, profile.getId(), profile.getName())
                : data.untrust(player, profile.getId(), profile.getName());
        String name = LandMessages.who(profile.getName());
        if (add) {
            switch (result.denial()) {
                case NONE -> say(player, "已将 " + name + " 添加为成员。");
                case NOT_CLAIMED -> say(player, "此处没有领地。");
                case NOT_OWNER -> say(player, "你不是这块领地的主人。");
                case SELF -> say(player, "不能把自己添加为成员。");
                case ALREADY_MEMBER -> say(player, name + " 已经是成员。");
                case GATE -> say(player, "现在不能添加成员。");
                default -> say(player, "现在不能添加成员。");
            }
        } else {
            switch (result.denial()) {
                case NONE -> say(player, "已将 " + name + " 移出成员。");
                case NOT_CLAIMED -> say(player, "此处没有领地。");
                case NOT_OWNER -> say(player, "你不是这块领地的主人。");
                case NOT_MEMBER -> say(player, name + " 不是成员。");
                case GATE -> say(player, "现在不能移出成员。");
                default -> say(player, "现在不能移出成员。");
            }
        }
        return result.succeeded() ? 1 : 0;
    }

    private static int transfer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        GameProfile profile = oneProfile(context, player);
        if (profile == null) {
            return 0;
        }
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        ClaimKey key = ClaimSavedData.standing(player);
        Claim existing = data.claims().get(key.dimension(), key.chunkX(), key.chunkZ());
        int parcelSize = existing == null ? 0 : data.claims().parcelChunkCount(existing.parcelId());
        ClaimMap.Result result = data.transfer(player, profile.getId(), profile.getName(), LandConfig.maxClaimsPerPlayer());
        if (result.succeeded()) {
            ChunkMarks.remove(player.getUuid(), key);
        }
        String name = LandMessages.who(profile.getName());
        switch (result.denial()) {
            case NONE -> say(player, parcelSize > 1
                    ? "已将此区块从宗地中拆出并转让给 " + name + "。"
                    : "已将领地转让给 " + name + "。");
            case NOT_CLAIMED -> say(player, "此处没有领地。");
            case NOT_OWNER -> say(player, "你不是这块领地的主人。");
            case SELF -> say(player, "这块领地已经是你的。");
            case AT_LIMIT -> say(player, name + " 的领地数量已达上限。");
            case GATE -> say(player, "现在不能转让此区块。");
            default -> say(player, "现在不能转让此区块。");
        }
        return result.succeeded() ? 1 : 0;
    }

    private static int markToggle(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        data.touch(player);
        ClaimKey key = ClaimSavedData.standing(player);
        Claim claim = data.claims().get(key.dimension(), key.chunkX(), key.chunkZ());
        if (claim == null) {
            say(player, "此处没有领地。");
            return 0;
        }
        if (!claim.owner().equals(player.getUuid())) {
            say(player, "你不是这块领地的主人。");
            return 0;
        }
        ChunkMarks.Change change = ChunkMarks.toggle(player.getUuid(), key);
        int count = ChunkMarks.size(player.getUuid());
        switch (change) {
            case ADDED -> say(player, "已标记此区块（共 " + count + " 块）。");
            case REMOVED -> say(player, "已取消标记此区块（共 " + count + " 块）。");
            case FULL -> say(player, "一次最多标记 " + ChunkMarks.MAX + " 块。");
        }
        return change == ChunkMarks.Change.FULL ? 0 : 1;
    }

    private static int markClear(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        int cleared = ChunkMarks.clear(player.getUuid());
        say(player, cleared == 0 ? "没有已标记的区块。" : "已清除标记。");
        return cleared == 0 ? 0 : 1;
    }

    private static int markList(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        List<ClaimKey> marks = ChunkMarks.get(player.getUuid());
        if (marks.isEmpty()) {
            say(player, "没有已标记的区块。");
            return 0;
        }
        say(player, "已标记 " + marks.size() + " 块：");
        int shown = 0;
        for (ClaimKey key : marks) {
            if (shown == LIST_LIMIT) {
                say(player, "还有 " + (marks.size() - shown) + " 块没有列出。");
                break;
            }
            say(player, LandMessages.dimension(key.dimension()) + " 区块 (" + key.chunkX() + ", " + key.chunkZ() + ")");
            shown++;
        }
        return 1;
    }

    private static int split(CommandContext<ServerCommandSource> context, String rawName) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        boolean marked = ChunkMarks.size(player.getUuid()) > 0;
        List<ClaimKey> keys = markedOrStanding(player);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        ClaimMap.Result result = data.split(player, keys, rawName);
        if (result.succeeded() && marked) {
            ChunkMarks.clear(player.getUuid());
        }
        switch (result.denial()) {
            case NONE -> {
                ParcelInfo created = data.claims().info(result.claim().parcelId());
                if (marked) {
                    say(player, "已把标记的 " + keys.size() + " 块拆成新宗地 " + label(created) + "。");
                } else {
                    say(player, "已把脚下的区块拆成新宗地 " + label(created) + "。");
                }
            }
            case NOT_CLAIMED -> say(player, marked ? "这些区块里有的还没有领地。" : "此处没有领地。");
            case NOT_OWNER -> say(player, "你不是这块宗地的主人。");
            case MIXED_PARCEL -> say(player, "这些区块不在同一块宗地里。");
            case ENTIRE_PARCEL -> say(player, "不能把整块宗地拆出去。");
            case NAME_INVALID -> say(player, "名称过长或含有无法使用的字符。");
            case GATE -> say(player, "现在不能拆分这块宗地。");
            default -> say(player, "现在不能拆分这块宗地。");
        }
        return result.succeeded() ? 1 : 0;
    }

    private static int splitTransfer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        GameProfile profile = oneProfile(context, player);
        if (profile == null) {
            return 0;
        }
        boolean marked = ChunkMarks.size(player.getUuid()) > 0;
        List<ClaimKey> keys = markedOrStanding(player);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        ClaimMap.Result result = data.transferChunks(player, keys, profile.getId(), profile.getName(), LandConfig.maxClaimsPerPlayer());
        if (result.succeeded() && marked) {
            ChunkMarks.clear(player.getUuid());
        }
        String name = LandMessages.who(profile.getName());
        switch (result.denial()) {
            case NONE -> say(player, "已将 " + keys.size() + " 块转让给 " + name + "。");
            case NOT_CLAIMED -> say(player, marked ? "这些区块里有的还没有领地。" : "此处没有领地。");
            case NOT_OWNER -> say(player, "你不是这块宗地的主人。");
            case MIXED_PARCEL -> say(player, "这些区块不在同一块宗地里。");
            case SELF -> say(player, "不能转让给自己。");
            case AT_LIMIT -> say(player, name + " 的领地数量已达上限。");
            case GATE -> say(player, "现在不能转让这些区块。");
            default -> say(player, "现在不能转让这些区块。");
        }
        return result.succeeded() ? 1 : 0;
    }

    private static int parcelList(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        data.touch(player);
        List<ParcelInfo> owned = data.claims().parcelsOwnedBy(player.getUuid());
        if (owned.isEmpty()) {
            say(player, "你还没有宗地。");
            return 0;
        }
        say(player, "你共有 " + owned.size() + " 宗地：");
        int shown = 0;
        for (ParcelInfo info : owned) {
            if (shown == LIST_LIMIT) {
                say(player, "还有 " + (owned.size() - shown) + " 宗地没有列出。");
                break;
            }
            ClaimKey at = info.chunks().get(0);
            say(player, label(info) + " · " + info.chunkCount() + " 块 · "
                    + LandMessages.dimension(info.dimension()) + " (" + at.chunkX() + ", " + at.chunkZ() + ") · " + info.id());
            shown++;
        }
        return owned.size();
    }

    private static int parcelInfoStanding(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        data.touch(player);
        ClaimKey key = ClaimSavedData.standing(player);
        Claim claim = data.claims().get(key.dimension(), key.chunkX(), key.chunkZ());
        if (claim == null) {
            say(player, "此处没有领地。");
            return 0;
        }
        return describeParcel(player, data.claims().info(claim.parcelId()));
    }

    private static int parcelInfoNamed(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        data.touch(player);
        ParcelInfo info = ownedParcel(player, data.claims(), StringArgumentType.getString(context, "parcel"));
        if (info == null) {
            return 0;
        }
        return describeParcel(player, info);
    }

    private static int describeParcel(ServerPlayerEntity player, ParcelInfo info) {
        if (info == null) {
            say(player, "没有这块宗地。");
            return 0;
        }
        sayParcelHeader(player, info);
        int shown = 0;
        for (ClaimKey key : info.chunks()) {
            if (shown == CHUNK_PRINT_LIMIT) {
                say(player, "还有 " + (info.chunkCount() - shown) + " 块没有列出。");
                break;
            }
            say(player, LandMessages.dimension(key.dimension()) + " 区块 (" + key.chunkX() + ", " + key.chunkZ() + ")");
            shown++;
        }
        return info.chunkCount();
    }

    private static int rename(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        ParcelInfo standing = standingParcel(player, data);
        if (standing == null) {
            return 0;
        }
        String raw = StringArgumentType.getString(context, "name");
        if ("-".equals(raw.trim())) {
            raw = "";
        }
        ClaimMap.Result result = data.renameParcel(player, standing.id(), raw);
        switch (result.denial()) {
            case NONE -> {
                ParcelInfo updated = data.claims().info(standing.id());
                if (updated == null || updated.name().isBlank()) {
                    say(player, "已清除宗地名称。");
                } else {
                    say(player, "已将宗地改名为 " + updated.name() + "。");
                }
            }
            case NOT_OWNER -> say(player, "你不是这块宗地的主人。");
            case NAME_INVALID -> say(player, "名称过长或含有无法使用的字符。");
            case GATE -> say(player, "现在不能给这块宗地改名。");
            default -> say(player, "现在不能给这块宗地改名。");
        }
        return result.succeeded() ? 1 : 0;
    }

    private static int merge(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        ParcelInfo standing = standingParcel(player, data);
        if (standing == null) {
            return 0;
        }
        ParcelInfo source = ownedParcel(player, data.claims(), StringArgumentType.getString(context, "parcel"));
        if (source == null) {
            return 0;
        }
        ClaimMap.Result result = data.merge(player, standing.id(), source.id());
        switch (result.denial()) {
            case NONE -> {
                ParcelInfo target = data.claims().info(standing.id());
                say(player, "已将「" + label(source) + "」并入「" + label(target) + "」，现为 "
                        + (target == null ? 0 : target.chunkCount()) + " 块。");
            }
            case SAME_PARCEL -> say(player, "这已经是同一块宗地。");
            case NOT_ADJACENT -> say(player, "这两块宗地没有挨在一起。");
            case DIFFERENT_DIMENSION -> say(player, "只能合并同一维度的宗地。");
            case NOT_OWNER -> say(player, "你不是这块宗地的主人。");
            case PARCEL_NOT_FOUND -> say(player, "没有这块宗地。");
            case GATE -> say(player, "现在不能合并这两块宗地。");
            default -> say(player, "现在不能合并这两块宗地。");
        }
        return result.succeeded() ? 1 : 0;
    }

    private static int transferParcel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        GameProfile profile = oneProfile(context, player);
        if (profile == null) {
            return 0;
        }
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        ParcelInfo standing = standingParcel(player, data);
        if (standing == null) {
            return 0;
        }
        int chunks = standing.chunkCount();
        ClaimMap.Result result = data.transferParcel(player.getUuid(), standing.id(), profile.getId(), profile.getName(),
                LandConfig.maxClaimsPerPlayer(), "");
        String name = LandMessages.who(profile.getName());
        switch (result.denial()) {
            case NONE -> say(player, "已将宗地转让给 " + name + "（" + chunks + " 块）。");
            case NOT_OWNER -> say(player, "你不是这块宗地的主人。");
            case SELF -> say(player, "这宗地已经是你的。");
            case AT_LIMIT -> say(player, name + " 的领地数量已达上限。");
            case PARCEL_NOT_FOUND -> say(player, "没有这块宗地。");
            case GATE -> say(player, "现在不能转让这块宗地。");
            default -> say(player, "现在不能转让这块宗地。");
        }
        return result.succeeded() ? 1 : 0;
    }

    private static int area(CommandContext<ServerCommandSource> context, String rawParcel) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        data.touch(player);
        ClaimMap map = data.claims();
        ParcelInfo info;
        if (rawParcel == null) {
            ClaimKey key = ClaimSavedData.standing(player);
            Claim claim = map.get(key.dimension(), key.chunkX(), key.chunkZ());
            info = claim == null ? null : map.info(claim.parcelId());
            if (info == null) {
                say(player, "此处没有领地。");
            } else {
                say(player, "此宗地" + label(info) + "：" + info.chunkCount() + " 块（" + info.blockCount() + " 方块）。");
            }
        } else {
            info = ownedParcel(player, map, rawParcel);
            if (info == null) {
                return 0;
            }
            say(player, "宗地" + label(info) + "：" + info.chunkCount() + " 块（" + info.blockCount() + " 方块）。");
        }
        say(player, "你名下：" + map.countOwned(player.getUuid()) + " 块，" + map.countParcelsOwned(player.getUuid()) + " 宗地。");
        return info == null ? 0 : info.chunkCount();
    }

    private static ParcelInfo standingParcel(ServerPlayerEntity player, ClaimSavedData data) {
        data.touch(player);
        ClaimKey key = ClaimSavedData.standing(player);
        Claim claim = data.claims().get(key.dimension(), key.chunkX(), key.chunkZ());
        if (claim == null) {
            say(player, "此处没有领地。");
            return null;
        }
        if (!claim.owner().equals(player.getUuid())) {
            say(player, "你不是这块宗地的主人。");
            return null;
        }
        return data.claims().info(claim.parcelId());
    }

    private static void sayParcelHeader(ServerPlayerEntity player, ParcelInfo info) {
        if (info == null) {
            say(player, "没有这块宗地。");
            return;
        }
        say(player, "宗地：" + label(info));
        say(player, "编号：" + info.id());
        say(player, "主人：" + LandMessages.who(info.ownerName()));
        say(player, "范围：" + info.chunkCount() + " 块（" + info.blockCount() + " 方块）");
    }

    private static ParcelInfo ownedParcel(ServerPlayerEntity player, ClaimMap map, String raw) {
        Lookup lookup = lookup(map, player.getUuid(), raw);
        return switch (lookup.kind) {
            case FOUND -> lookup.info;
            case AMBIGUOUS -> {
                say(player, "有多块宗地匹配，请把编号写全。");
                yield null;
            }
            case NOT_OWNER -> {
                say(player, "你不是这块宗地的主人。");
                yield null;
            }
            case NOT_FOUND -> {
                say(player, "没有这块宗地。");
                yield null;
            }
        };
    }

    private static Lookup lookup(ClaimMap map, UUID owner, String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return Lookup.missing();
        }
        List<ParcelInfo> owned = map.parcelsOwnedBy(owner);
        List<ParcelInfo> named = new ArrayList<>();
        for (ParcelInfo info : owned) {
            if (text.equals(info.name())) {
                named.add(info);
            }
        }
        if (named.size() == 1) {
            return Lookup.found(named.get(0));
        }
        if (named.size() > 1) {
            return new Lookup(LookupKind.AMBIGUOUS, null);
        }
        UUID parsed = tryUuid(text);
        if (parsed != null) {
            ParcelInfo info = map.info(parsed);
            if (info == null) {
                return Lookup.missing();
            }
            if (!info.owner().equals(owner)) {
                return new Lookup(LookupKind.NOT_OWNER, info);
            }
            return Lookup.found(info);
        }
        String compact = text.toLowerCase(Locale.ROOT).replace("-", "");
        if (isHexPrefix(compact)) {
            List<ParcelInfo> prefix = new ArrayList<>();
            for (ParcelInfo info : owned) {
                if (info.id().toString().replace("-", "").startsWith(compact)) {
                    prefix.add(info);
                }
            }
            if (prefix.size() == 1) {
                return Lookup.found(prefix.get(0));
            }
            if (prefix.size() > 1) {
                return new Lookup(LookupKind.AMBIGUOUS, null);
            }
        }
        return Lookup.missing();
    }

    private static boolean isHexPrefix(String compact) {
        if (compact.length() < 4 || compact.length() > 32) {
            return false;
        }
        for (int index = 0; index < compact.length(); index++) {
            char character = compact.charAt(index);
            boolean hex = (character >= '0' && character <= '9') || (character >= 'a' && character <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static UUID tryUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static List<ClaimKey> markedOrStanding(ServerPlayerEntity player) {
        List<ClaimKey> marked = ChunkMarks.get(player.getUuid());
        if (!marked.isEmpty()) {
            return marked;
        }
        return List.of(ClaimSavedData.standing(player));
    }

    private static String label(ParcelInfo info) {
        if (info == null || info.name().isBlank()) {
            return info == null ? "未命名" : "未命名（" + info.shortId() + "）";
        }
        return info.name();
    }

    private static GameProfile oneProfile(CommandContext<ServerCommandSource> context, ServerPlayerEntity player) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "player");
        if (profiles.size() != 1) {
            say(player, "请指定一名玩家。");
            return null;
        }
        return profiles.iterator().next();
    }

    private static String memberList(Claim claim) {
        if (claim.members().isEmpty()) {
            return "无";
        }
        List<String> names = new ArrayList<>();
        for (Member member : claim.members().values()) {
            names.add(LandMessages.who(member.name()));
        }
        return String.join("、", names);
    }

    private static ServerPlayerEntity player(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrThrow();
    }

    private static void say(ServerPlayerEntity player, String body) {
        player.sendMessage(LandMessages.chat(body), false);
    }

    private enum LookupKind {
        FOUND,
        NOT_FOUND,
        NOT_OWNER,
        AMBIGUOUS
    }

    private record Lookup(LookupKind kind, ParcelInfo info) {
        private static Lookup found(ParcelInfo info) {
            return new Lookup(LookupKind.FOUND, info);
        }

        private static Lookup missing() {
            return new Lookup(LookupKind.NOT_FOUND, null);
        }
    }
}

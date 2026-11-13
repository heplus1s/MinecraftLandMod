package dev.jdguo.land.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.jdguo.land.claim.Claim;
import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.claim.ClaimMap;
import dev.jdguo.land.claim.Member;
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

public final class LandCommands {
    private static final int LIST_LIMIT = 100;

    private LandCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register(LandCommands::register);
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registry, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("land")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .executes(LandCommands::usage)
                .then(CommandManager.literal("claim").executes(LandCommands::claim))
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
                                .executes(LandCommands::transfer))));
    }

    private static int usage(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        player(context).sendMessage(Text.literal(LandMessages.USAGE), false);
        return 0;
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

    private static int abandon(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = player(context);
        ClaimMap.Result result = ClaimSavedData.get(player.getServer()).abandon(player);
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
        if (!claim.name().isBlank()) {
            say(player, "名称：" + claim.name());
        }
        say(player, "主人：" + LandMessages.who(claim.ownerName()));
        say(player, "成员：" + memberList(claim));
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
            say(player, LandMessages.dimension(claim.dimension()) + " 区块 (" + claim.chunkX() + ", " + claim.chunkZ() + ")");
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
        ClaimMap.Result result = ClaimSavedData.get(player.getServer())
                .transfer(player, profile.getId(), profile.getName(), LandConfig.maxClaimsPerPlayer());
        String name = LandMessages.who(profile.getName());
        switch (result.denial()) {
            case NONE -> say(player, "已将领地转让给 " + name + "。");
            case NOT_CLAIMED -> say(player, "此处没有领地。");
            case NOT_OWNER -> say(player, "你不是这块领地的主人。");
            case SELF -> say(player, "这块领地已经是你的。");
            case AT_LIMIT -> say(player, name + " 的领地数量已达上限。");
            case GATE -> say(player, "现在不能转让此区块。");
            default -> say(player, "现在不能转让此区块。");
        }
        return result.succeeded() ? 1 : 0;
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
}

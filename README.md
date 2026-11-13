# 领地 / Land

Fabric 1.20.1 的区块领地模组。服务端负责保护，原版客户端可以进服，不需要装这个模组，也不需要资源包。

A chunk-claim mod for Fabric 1.20.1. The server enforces protection. A vanilla client can join and play without this mod and without a resource pack.

这不是 Open Parties and Claims，也不是别的模组的一部分。

This is a new mod. It is not Open Parties and Claims, and it is not part of another project.

## 安装

服务端和单人游戏都要装 Fabric Loader、Fabric API，以及本模组。`fabric.mod.json` 里的 environment 是 `*`，入口是 `ModInitializer`，单人游戏的集成服务端会一起启动保护。不要只装一个服务端专用入口，那样单人世界不会生效。

进专用服务器的玩家不需要装本模组。装了的话，客户端会收到领地数据，用来画地图，不参与保护判定。

Put Fabric Loader, Fabric API, and this mod on the dedicated server. For singleplayer, put the same jars in a Fabric client. The mod uses environment `*` and a `ModInitializer`, so the integrated server runs the protection too.

Players who only join the server do not install anything. If their client also has this mod, it receives claim data for the map overlay. Protection is still decided on the server.

构建好的文件是 `landmod-1.0.0.jar`。

## 命令

命令本身是英文，聊天提示是中文，前缀是 `[领地]`。

Commands are English. Chat replies are Chinese and start with `[领地]`.

| 命令 | 作用 |
| --- | --- |
| `/land claim` | 占领脚下这个区块 |
| `/land abandon` | 放弃脚下这个区块。只有主人可以 |
| `/land info` | 查看脚下区块的主人和成员 |
| `/land trust <player>` | 把一名玩家加进脚下这块领地 |
| `/land untrust <player>` | 把这名玩家从脚下这块领地移出 |
| `/land transfer <player>` | 把脚下这块领地转让给这名玩家。原来的成员会被清掉 |
| `/land list` | 列出你拥有的区块 |

`trust`、`untrust`、`transfer`、`abandon` 都只处理你站着的那一块，不会一下子改掉你名下所有区块。玩家名用游戏里的档案名，需要这个人至少进过这个服务器。

`trust`, `untrust`, `transfer`, and `abandon` apply only to the chunk you are standing in. The player argument is a game profile, so that person needs to have joined the server before.

权限等级 2 及以上的管理员可以破坏、放置、开容器，不受领地限制。管理员不能因此放弃或转让别人的领地，领地数量上限对他们同样有效。

Operators at permission level 2 or higher bypass block protection. They do not bypass the claim limit, and they cannot abandon or transfer someone else's chunk with these commands.

## 保护范围

非成员不能做这些事：

- 破坏方块
- 放置方块
- 打开容器或物品界面，包括箱子、漏斗、熔炉、末影箱、工作台、铁砧、附魔台，以及船箱、运输矿车、有箱子的驴
- 爆炸破坏方块。苦力怕、恶魂、床和重生锚的爆炸也算。若爆炸是某名玩家引起的（例如这名玩家点燃的 TNT），而这名玩家本来就能在这块领地里建造，方块会被炸掉

管理员绕过上述玩家操作。没有玩家来源的爆炸不会拆掉已占领区块里的方块。

这一版不管门、按钮、拉杆、物品展示框、活塞、流体，也不管凋灵本体砸方块和末影龙。

Non-members cannot break blocks, place blocks, open containers and item screens, or have explosions destroy blocks in the claim. An explosion caused by a player who is allowed to build there can still break those blocks. Operator bypass applies to player actions. Creeper, ghast, bed, and respawn-anchor explosions are included. Doors, buttons, levers, item frames, pistons, fluids, the wither boss, and the ender dragon are not handled in this version.

## 配置

第一次运行会在 `config/landmod.json` 写下：

```json
{
  "maxClaimsPerPlayer": 32
}
```

这是每个玩家可以拥有的区块数。`0` 或负数表示不限制。改完保存即可，下一次执行领地命令时会重新读取。这个文件如果已经存在，模组不会整份覆盖，避免以后加配置项时被清掉。

`maxClaimsPerPlayer` is how many chunks one player may own. `0` or a negative number means no limit. The default is 32. Edits are picked up the next time someone uses a land command. An existing file is not rewritten.

## 存档

所有维度的领地都写在主世界的 `data/landmod_claims.dat` 里，和下界、末地的区块放在同一份数据中。格式版本是 1。以后升级时，缺字段的旧存档仍然能读；这份代码在读入后再写回时，会保留自己不认识的键，避免把更新版本写下的内容抹掉。

Claims from every dimension are stored in the overworld file `data/landmod_claims.dat`. The format version is 1. Older files that omit optional fields still load. Keys this version does not understand are written back out, so a newer save is not wiped by running this version.

## Xaero 地图

地图显示是可选的。服务端、没装 Xaero 的客户端、以及原版客户端，领地保护都照常工作。

如果客户端同时装了本模组和这两份地图模组，已占领的区块会画在世界地图和小地图上：

- Xaero's World Map 1.37.2（Fabric 1.20）
- Xaero's Minimap 23.9.3（Fabric 1.20）

你自己的领地是绿色，别人的领地是橙色。鼠标停在上面时，有自定义名称就显示名称和主人，否则显示主人的领地。

本模组编译时对照的是这两份官方 highlighter API：

- 世界地图：`xaero.map.highlight.AbstractHighlighter`、`ChunkHighlighter`、`HighlighterRegistry`
- 小地图：`xaero.common.minimap.highlight` 下同名的一套

它们都是 `compileOnly`，不会打进 jar，也没有写进 `depends`。这两份版本会在地图会话里创建注册表，登记自己的内容后立刻调用 `end()` 把列表冻住，中间没有公开事件。因此客户端在 `end()` 一开始、列表还没冻住时登记自己的 highlighter。没装对应地图模组时，这个 mixin 不会启用。

Xaero is optional. Dedicated servers and clients without those map mods still protect claims. Vanilla clients need no land mod, no map mod, and no resource pack.

When the client has this mod plus Xaero's World Map 1.37.2 and Minimap 23.9.3 for Fabric 1.20, claimed chunks are drawn on both maps. Your claims are green. Other players' claims are orange. The hover text shows the claim name when one is stored, otherwise the owner.

The jars are a compile-only dependency. In these versions each highlighter registry is sealed inside `end()` with no public registration event before that, so the client mixin registers at the start of `end()`. The mixin is skipped when that map mod is absent.

## 以后的接口

这一版没有银行、欠款或商店。以后可以做两件事，而不用改存档格式：

- 调用 `ClaimGates.register` 注册一个 `ClaimGate`，用来拒绝占领、放弃、转让或信任。
- 往区块的 `Flags` NBT 里写标记。本版本会原样保存这个复合标签。

区块上还有一个可选的 `Name`。现在的命令不能改它；如果存档里已经有名称，查询和地图提示会显示。

There is no bank, debt, or shop code. `ClaimGates.register` can veto claim, abandon, transfer, and trust later. Per-chunk `Flags` NBT is stored and returned unchanged. `Name` is saved and shown when present; no command sets it yet.

## 构建

需要 Java 17。当前工程用 Java 21 运行 Gradle，编译目标是 17。

```
./gradlew test remapJar
```

给玩家或服务器用的是重映射后的 `build/libs/landmod-1.0.0.jar`，不是带 `-dev` 或 `-sources` 的那个。

Java 17 is the target. `./gradlew test remapJar` runs the tests and writes the remapped jar to `build/libs/landmod-1.0.0.jar`.

## 许可

MIT License. Copyright (c) 2026 heplus1s.

# 领地 / Land

Fabric 1.20.1 的区块领地模组。最小单位仍是原版 16×16 区块，若干区块组成一块有编号、可命名的宗地。服务端负责保护，原版客户端可以进服，不需要装这个模组，也不需要资源包。

A chunk-claim mod for Fabric 1.20.1. The smallest unit is still one vanilla 16×16 chunk. A parcel is a named, id'd group of those chunks. The server enforces protection. A vanilla client can join and play without this mod and without a resource pack.

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
| `/land claim` | 占领脚下这个区块，并新建一块宗地 |
| `/land claim join` | 把脚下区块并入旁边那块你的宗地。旁边有多块时要写出名称或编号 |
| `/land claim join <parcel>` | 并入指定的宗地。它必须是你的，并且和脚下区块共边 |
| `/land abandon` | 放弃脚下这个区块。只有主人可以。宗地里最后一块被放弃后，宗地记录删除 |
| `/land info` | 查看脚下区块所在宗地、主人，以及这个区块的成员 |
| `/land list` | 列出你拥有的区块 |
| `/land trust <player>` | 把一名玩家加进脚下这个区块 |
| `/land untrust <player>` | 把这名玩家从脚下这个区块移出 |
| `/land transfer <player>` | 转让脚下这个区块。宗地里还有别的区块时，这一块会先拆出去 |
| `/land mark` | 标记或取消标记脚下区块，供拆分使用 |
| `/land mark clear` | 清除标记 |
| `/land mark list` | 列出已标记的区块 |
| `/land split` | 把标记的区块，或没有标记时脚下这一块，拆成你仍拥有的新宗地 |
| `/land split <name>` | 同上，并给新宗地起名 |
| `/land split transfer <player>` | 把标记的区块，或脚下这一块，转让给这名玩家 |
| `/land parcel list` | 列出你的宗地、块数和编号 |
| `/land parcel info` | 查看脚下宗地。也可以 `/land parcel info <parcel>` |
| `/land parcel rename <name>` | 给脚下宗地改名。`<name>` 为 `-` 时清除名称 |
| `/land parcel merge <parcel>` | 把指定宗地并入脚下这块。两块要共边，且在同一维度 |
| `/land parcel transfer <player>` | 把脚下整块宗地转让给这名玩家 |
| `/land area` | 查询脚下宗地的区块数，以及你名下的总区块数 |
| `/land area <parcel>` | 查询指定宗地的区块数 |

斜对角不算相邻。信任仍然只作用在单个区块上，合并或并入不会把成员复制到别的区块。转让会清掉被转走的那些区块上的成员。放弃或拆分中间的一块后，剩下的区块仍算同一宗地，模组不会自动再拆开。

玩家名用游戏里的档案名，需要这个人至少进过这个服务器。宗地参数可以是名称、完整编号，或至少 4 位且能唯一对上的编号前缀。补全只提示编号。一次最多标记 512 块。

`trust` and `untrust` still apply to the chunk you are standing in. `/land transfer` does too: if that chunk is part of a larger parcel, it is carved off first. `/land parcel transfer` moves the whole parcel. `/land split` carves marked chunks, or the chunk underfoot when nothing is marked, into a new parcel you still own. `/land split transfer` gives those chunks to someone else as one parcel. Join and merge need a shared edge in the same dimension. Diagonal chunks do not count. Trust is not copied onto other chunks. Transfer clears members on the chunks that change owner. Abandoning or carving out a bridge chunk leaves the rest as one parcel.

The player argument is a game profile, so that person needs to have joined the server before. A parcel argument is its name, its full id, or a unique id prefix of at least 4 hex digits. Tab completion offers ids only. A player can mark at most 512 chunks. `/land parcel rename -` clears the name.

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

这是每个玩家可以拥有的区块数，不是宗地数。一块 4 个区块的宗地占 4 个名额。`0` 或负数表示不限制。改完保存即可，下一次执行领地命令或转让时会重新读取。这个文件如果已经存在，模组不会整份覆盖，避免以后加配置项时被清掉。

`maxClaimsPerPlayer` is how many chunks one player may own, not how many parcels. A parcel of 4 chunks uses 4 of the allowance. `0` or a negative number means no limit. The default is 32. Edits are picked up the next time someone uses a land command or a transfer runs. An existing file is not rewritten.

## 存档

所有维度的领地都写在主世界的 `data/landmod_claims.dat` 里。格式版本现在是 2。版本 1 的旧档会在读入时给每个区块单独生成一块宗地，主人、成员、名称、标记和原来不认识的键都保留，不会清空存档。下一次保存会把这些编号写回去。同一宗地编号下如果出现了不同主人或不同维度，读入时会拆成多块宗地，原来的编号留给排序后的第一组。更高的格式号会原样保留，这份代码不认识的键也会写回去。

Claims from every dimension are stored in the overworld file `data/landmod_claims.dat`. The format version is 2. A version 1 save still loads: each existing chunk becomes its own parcel, and the owner, members, name, flags, and unknown keys stay. The new ids are written on the next save. If one parcel id covers more than one owner or dimension, load splits it and keeps the original id on the first group. A higher format number is kept, and keys this version does not understand are written back out.

## Xaero 地图

地图显示是可选的。服务端、没装 Xaero 的客户端、以及原版客户端，领地保护都照常工作。

如果客户端同时装了本模组和这两份地图模组，已占领的区块会画在世界地图和小地图上：

- Xaero's World Map 1.37.2（Fabric 1.20）
- Xaero's Minimap 23.9.3（Fabric 1.20）

你自己的领地是绿色，别人的领地是橙色。同一宗地的区块连在一起，宗地交界处画边，哪怕两边是同一个主人。鼠标停在上面时，有名称就显示名称、主人和块数，没有名称就显示主人、块数和编号前 8 位。

本模组编译时对照的是这两份官方 highlighter API：

- 世界地图：`xaero.map.highlight.AbstractHighlighter`、`ChunkHighlighter`、`HighlighterRegistry`
- 小地图：`xaero.common.minimap.highlight` 下同名的一套

它们都是 `compileOnly`，不会打进 jar，也没有写进 `depends`。这两份版本会在地图会话里创建注册表，登记自己的内容后立刻调用 `end()` 把列表冻住，中间没有公开事件。因此客户端在 `end()` 一开始、列表还没冻住时登记自己的 highlighter。没装对应地图模组时，这个 mixin 不会启用。

Xaero is optional. Dedicated servers and clients without those map mods still protect claims. Vanilla clients need no land mod, no map mod, and no resource pack.

When the client has this mod plus Xaero's World Map 1.37.2 and Minimap 23.9.3 for Fabric 1.20, claimed chunks are drawn on both maps. Your claims are green. Other players' claims are orange. Chunks in the same parcel share a fill, and a border is drawn where the parcel changes, including between two parcels of one owner. The hover text shows the parcel name, owner, and chunk count. An unnamed parcel shows the owner, the chunk count, and the first 8 digits of its id.

The jars are a compile-only dependency. In these versions each highlighter registry is sealed inside `end()` with no public registration event before that, so the client mixin registers at the start of `end()`. The mixin is skipped when that map mod is absent.

## 给银行和市场用的接口

本模组不算钱，也不做担保。别的模组在服务端线程调用 `dev.jdguo.land.api.LandApi`。

面积用区块数。`ParcelInfo.chunkCount()` 是宗地里有几块 16×16 的区块，`blockCount()` 是 `chunkCount * 256`，表示水平方块列，不是体积。方块坐标换成区块要用算术右移 `block >> 4`，负数才不会错；`LandApi.parcelAtBlock` 已经这样算。

交易和抵押可以这样接：

- `LandApi.transferParcel` 转让整块宗地。`reference` 留给托管单号，命令触发时是空字符串。
- `LandApi.transferChunk` / `transferChunks` 转让一块或几块。没有覆盖整块宗地时，这些区块会拆成买方的一块新宗地。
- `LandApi.split` 把选出的区块拆成原主人仍持有的新宗地，至少要留下一块。
- `LandApi.seizeParcel` / `seizeChunk` 不要求当前主人在场，给抵押没收用。`respectLimit` 为 false 时不看数量上限。
- `LandApi.putParcelFlag` 和区块上的 `Flags` 用来记抵押或托管标记。合并时目标宗地已有的同名标记优先，被盖掉的来源标记会收进目标的 `Absorbed` 里，不直接丢掉。
- `ClaimGates.register` 可以拒绝占领、并入、放弃、信任、拆分、合并、改名、转让和没收。整块转让会走 `allowParcelTransfer`，每一块还会再走 `allowTransfer`。从大宗地里卖出几块时还会走 `allowSplit`。锁定抵押时这几个都要拒绝，只在 `reference` 对上借据时放行 `allowSeizeParcel`。
- `LandEvents.register` 在变更已经写入之后通知。只含一块的宗地被转让时走 `afterParcelTransfer`。从大宗地拆出去卖掉走 `afterChunkTransfer`。没收整块走 `afterSeizeParcel`。

玩家命令不调用没收。

There is still no currency, debt ledger, or shop in this mod. Another mod calls `dev.jdguo.land.api.LandApi` on the server thread.

Valuation uses the chunk count. `ParcelInfo.chunkCount()` is the number of 16×16 chunks. `blockCount()` is `chunkCount * 256` horizontal columns, not a volume. Convert a block coordinate with `block >> 4`; `LandApi.parcelAtBlock` does that, including for negative coordinates.

- `LandApi.transferParcel` transfers a whole parcel. `reference` is an escrow id; player commands pass an empty string.
- `LandApi.transferChunk` and `transferChunks` transfer one chunk or several. A partial set is carved into one new parcel for the buyer. Transferring every chunk keeps the parcel id.
- `LandApi.split` carves selected chunks into a new parcel the same owner still holds. One chunk of the source has to remain.
- `LandApi.seizeParcel` and `seizeChunk` change ownership without the current owner. They are the collateral path. `respectLimit` false skips the chunk cap.
- `LandApi.putParcelFlag`, and the per-chunk `Flags` compound, store collateral or escrow markers. On merge, the target keeps a conflicting flag and the displaced source flags are kept under `Absorbed` on the target.
- `ClaimGates.register` can veto claim, join, abandon, trust, split, merge, rename, transfer, and seizure. A whole-parcel transfer calls `allowParcelTransfer` and `allowTransfer` for each chunk. Selling some chunks of a larger parcel also calls `allowSplit`. A collateral lock should veto those, and allow `allowSeizeParcel` only when `reference` matches the loan.
- `LandEvents.register` runs after the change is stored. Transferring a one-chunk parcel fires `afterParcelTransfer`. A partial sale fires `afterChunkTransfer`. Seizing a whole parcel fires `afterSeizeParcel`.

Player commands do not seize land.

## 构建

需要 Java 17。当前工程用 Java 21 运行 Gradle，编译目标是 17。

```
./gradlew test remapJar
```

给玩家或服务器用的是重映射后的 `build/libs/landmod-1.0.0.jar`，不是带 `-dev` 或 `-sources` 的那个。

Java 17 is the target. `./gradlew test remapJar` runs the tests and writes the remapped jar to `build/libs/landmod-1.0.0.jar`.

## 许可

MIT License. Copyright (c) 2026 heplus1s.

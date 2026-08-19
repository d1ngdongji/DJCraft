# DJCraft 数据包开发文档

> 适用版本：DJCraft 2.2.0、Minecraft 1.21.1、NeoForge 21.1.218。代码核对日期：2026-08-07。
> 本文只描述服务器数据包。玩家规则与运行时结算见
> [游戏机制与实现](gameplay-mechanics.md)，客户端美术与 JSON Profile 见
> [资源包开发文档](resource-pack.md)，曲目与节拍时间线见
> [曲目包功能文档](trackpack-feature.md)。

## 1. 当前数据包能力总览

DJCraft 提供物品 DJ 战斗时序、专用行为和即时射线 Profile 的服务器数据包加载器。除此之外，数据包还可以使用原版 Minecraft 机制：

- 覆盖任意物品的 DJ 冷却、切换前摇和命中能量成本；
- 为原版或其他模组物品选择蓄力、触发、三叉戟式、重锤式、盾牌等既有 DJ 动作；
- 将已进入 trigger family 的物品配置为服务端权威即时射线武器；
- 通过 `djcraft:swift` 和 `djcraft:smash` 物品标签接入节拍类别伤害规则；
- 覆盖或删除 DJCraft 内置配方；
- 为 DJCraft 物品新增原版配方；
- 在附属 Mod/整合包自己的战利品表、进度、函数、谓词和标签中引用 DJCraft 注册内容；
- 使用原版物品堆栈组件语法生成带 DJCraft 数据组件的唱片（需谨慎，见本文限制）。

内置数据包还定义三个可由附魔台抽取的原版数据驱动附魔：`djcraft:aerial_step`（靴子，最高 II，
每级增加 1 次空中多段跳）、`djcraft:rending`（三叉戟，最高 III，每级使 DJ 三叉戟施加的基础
撕裂 II 再提高 1 级）和 `djcraft:ray_overcharge`（标签 `#djcraft:enchantable/ray_weapon`，最高 IV，
每级增加 2 射线伤害）。整合包可按原版 `data/<namespace>/enchantment/` 与附魔标签规则覆盖它们。

以下内容目前**不是数据包能力**：曲目包、战斗节拍 definition、能量的获取/上限规则、移动能力参数、第一人称动画、武器音效 Profile、HUD 布局。

## 2. 基础结构

Minecraft 1.21.1 数据包使用数据包格式 48：

```text
MyDJCraftDataPack/
├─ pack.mcmeta
└─ data/
   ├─ djcraft/
   │  ├─ recipe/
   │  └─ tags/item/
   └─ <item_namespace>/
      └─ djcraft/
         ├─ item_timing/
         ├─ item_behaviors/
         └─ ray_weapons/
   └─ <your_namespace>/
      ├─ recipe/
      ├─ tags/
      ├─ loot_table/
      ├─ advancement/
      └─ function/
```

```json
{
  "pack": {
    "pack_format": 48,
    "description": "DJCraft server data overrides"
  }
}
```

Minecraft 1.21.1 使用单数目录名，如 `recipe`、`loot_table`、`advancement`、`function`。

使用 `/reload` 重新加载服务器数据包。DJCraft 的 `/dj reload` 只重载外置曲目包，不等价于 `/reload`。

## 3. 物品 DJ 战斗 Profile

### 3.1 路径与资源 ID

Profile 路径：

```text
data/<物品命名空间>/djcraft/item_timing/<物品路径>.json
```

目录前后的命名空间和路径共同组成目标物品 ID。例如 `minecraft:crossbow` 对应：

```text
data/minecraft/djcraft/item_timing/crossbow.json
```

若物品 ID 是 `examplemod:weapons/rifle`，对应路径是：

```text
data/examplemod/djcraft/item_timing/weapons/rifle.json
```

### 3.2 Schema 与回退

```json
{
  "beat_cooldown": 4,
  "switch_warmup": 1,
  "attack_energy_cost": 2.5,
  "use_energy_cost": 3
}
```

- `beat_cooldown`：可选的 DJ 冷却拍数，也是蓄力型武器计算满力时间时读取的拍数；
- `switch_warmup`：可选的切换到该物品时的前摇拍数；
- `attack_energy_cost`：可选的主手普通攻击能量成本；三叉戟左键范围攻击也读取此值；
- `use_energy_cost`：可选的 DJCraft 已接管右键动作能量成本，包括弓释放、弩发射、风弹发射和三叉戟投掷；
- 时序值必须是 0 到 Java 32 位有符号整数上限之间的整数；能量值必须是有限、非负的 JSON 数字，允许小数；
- 至少提供一个字段，不允许未知字段。

缺少 `beat_cooldown` 时，继续按物品主手攻击速度计算 1–4 拍。缺少 `switch_warmup` 时，使用该物品最终得到的 `beat_cooldown`。因此只覆盖冷却会同时改变默认切换前摇；如需关闭前摇，应显式写 `"switch_warmup": 0`。

缺少能量字段时，对应动作成本为 0。能量只在节拍判定命中且动作通过服务端物品/冷却校验后扣除；判定 Miss 不扣除。服务端权威扣费，客户端同步 Profile 仅用于在本地能量不足时提前拦截。`attack_energy_cost` 可配置到任意已注册物品，因为普通主手攻击都经过 DJCraft 判定；`use_energy_cost` 不会自动接管普通食物、方块等任意右键逻辑，仅对上述已有 DJ 专用右键流程生效。

DJCraft 内置以下 Profile，行为与迁移前一致：

| 物品 | `beat_cooldown` | `switch_warmup` | `attack_energy_cost` | `use_energy_cost` |
|---|---:|---:|---:|---:|
| `minecraft:bow` | 2 | 0 | 0 | 0 |
| `minecraft:crossbow` | 4 | 1 | 0 | 0 |
| `djcraft:laser_crossbow` | 2 | 1 | 0 | 3 |
| `djcraft:magic_crossbow` | 2 | 1 | 0 | 3 |
| `djcraft:explosive_bow` | 2 | 1 | 0 | 6 |
| `minecraft:trident` | 2 | 1 | 5 | 10 |
| `minecraft:mace` | 按攻击速度计算 | 1 | 8 | 0 |

### 3.3 覆盖、校验与同步

同一资源 ID 遵循标准数据包优先级，由高优先级数据包整文件覆盖。`/reload` 成功准备完所有合法 Profile 后，DJCraft 会原子替换运行时快照；删除覆盖文件并再次 `/reload` 会恢复下层数据包或计算回退。

未知物品、非法负数、时序小数、非有限能量数、错误类型、未知字段和空 Profile 会记录明确错误并忽略该文件，其他合法文件仍会生效。解析只发生在重载阶段，不发生在战斗、Tick 或渲染热路径。

服务端是权威来源。玩家加入以及服务器执行 `/reload` 时，服务端会将完整有效 Profile 快照同步给相关客户端；客户端不需要安装服务器数据包副本。同步结果同时用于客户端冷却、切换动画、Tooltip 和能量预检，以及服务端攻击冷却校验与最终扣费。Tooltip 会分别显示非零的“攻击耗能”和“使用耗能”；值为 0 或缺少对应字段时不显示该行。

旧配置项 `itemBeatCooldowns`、`itemSwitchWarmups` 已移除且不再作为回退来源。

### 3.4 即时射线武器 Profile

射线 Profile 按目标物品 ID 存放：

```text
data/<物品命名空间>/djcraft/ray_weapons/<物品路径>.json
```

内置激光弩使用：

```json
{
  "range": 96.0,
  "base_damage": 12.0,
  "pierce_entities": true,
  "effect": "djcraft:laser_crossbow",
  "horizontal_aim_assist_percent": 7.0,
  "vertical_aim_assist_percent": 7.0
}
```

内置魔法弩使用相同的 96 格射程与软辅助瞄准，但设置 `base_damage: 16.0`、
`pierce_entities: false` 和 `effect: "djcraft:magic_crossbow"`，因此服务端只伤害最近的合法目标。

爆炸弓还使用可选延迟发射和爆炸字段：

```json
{
  "range": 96.0,
  "base_damage": 0.0,
  "pierce_entities": false,
  "effect": "djcraft:explosive_bow",
  "horizontal_aim_assist_percent": 7.0,
  "vertical_aim_assist_percent": 7.0,
  "auto_charge_beats": 1,
  "explosion": {
    "radius": 5.0,
    "damage": 18.0,
    "airborne_radius": 8.0,
    "airborne_damage": 36.0,
    "explode_at_max_range": true
  }
}
```

- `range` 必须是 `(0, 1024]` 的有限数，表示服务端方块截断前的最大格数；
- `base_damage` 必须是 `0..Float.MAX_VALUE` 的有限数，最终仍乘动作建立时的节拍类别倍率并经过正常伤害事件；
  `djcraft:ray_overcharge` 每级在结算前增加 2：有直伤时增加直伤，存在 `explosion` 时同时增加其中心伤害；
- `pierce_entities` 为 `true` 时按射线进入距离伤害全部合法生物，为 `false` 时只伤害最近目标，且权威射线端点停在该目标的碰撞箱交点；
- `effect` 是客户端 `ray_effects` 资源 ID，只选择视觉样式，不参与命中或伤害；
- `horizontal_aim_assist_percent`、`vertical_aim_assist_percent` 分别是视线前向距离上的横向/纵向
  软捕获百分比，必须是 `[0, 100]` 的有限数；省略时分别默认 `7.0`，设为 `0` 可关闭对应方向；
- `auto_charge_beats` 可选，必须是 `[0, 32]` 的整数；`0` 或省略表示立即开火，正数表示从实际按下
  时间的连续虚拟节拍值开始等待对应拍数，并在服务端 tick 首次跨过目标值时开火。等待期间由服务端
  持续验证原手、来源槽、物品和会话；
- `explosion` 可选；存在时五个子字段均必填且不允许未知字段。两个半径必须是 `(0, 64]` 的有限数，
  两个伤害必须是非负有限数；直接命中的实体未着地时使用 `airborne_*`，否则使用普通值。
  `explode_at_max_range` 控制射线既未命中实体也未命中方块时是否仍在最大射程末端爆炸；
- 爆炸伤害按距离和原版 `Explosion.getSeenPercent` 墙体暴露率线性衰减，只选择与射线相同的合法敌对
  目标。该功能不会调用原版爆炸、破坏地形、点火或生成原版粒子；
- 前四个字段必填，其余字段可选，且不允许未知字段。非法或未知物品文件只被忽略，不影响其他 Profile。

Profile 只有在物品已经通过 Java 继承、`item_behaviors` 或注册行为进入 trigger family 后才生效；
它不会把普通物品的右键自动改成攻击。触发请求通过 proof、动作来源、冷却和能量验证后，服务端
从自己的玩家眼位/视线优先检查准星直击；直射未命中合法目标时，才在配置的矩形视线锥中选择
与准星夹角最小、未被方块遮挡的合法目标并修正射线方向。随后仍由服务端按修正后的单条射线
计算固体方块截断、合法目标、伤害和耐久。客户端同步快照仅用于即时视觉
预测，服务器广播的端点与命中点才是权威结果。缺失 Profile 时，弩继续使用弹药发射，其他 trigger
物品继续调用自己的标准 `ItemStack.use()` 或注册执行器。

同一资源 ID 使用标准数据包整文件覆盖。服务器 `/reload` 或玩家登录后会同步完整有效快照；删除
覆盖并重载会恢复低优先级版本。运行时查询使用不可变快照，不在战斗热路径读取 JSON。

### 3.5 节拍类别武器标签

曲目 definition 的 `category` 由服务端结合以下原版物品标签解释：

- `data/djcraft/tags/item/swift.json`：在 `weakbeat` 上免除 50% 伤害惩罚；
- `data/djcraft/tags/item/smash.json`：在 `downbeat` 上将伤害提高到 150%。

内置数据中，`minecraft:mace` 属于 `djcraft:smash`，`minecraft:golden_sword` 属于
`djcraft:swift`，`minecraft:trident` 同时属于 `djcraft:swift` 和 `djcraft:smash`。
数据包可以按标准标签合并规则加入其他武器；
使用 `"replace": true` 则会替换低优先级包提供的成员。标签在服务器数据包重载后生效，
最终伤害不信任客户端标签状态。客户端 Tooltip 会分别显示物品当前所属的 Swift 和
Smash 标签；同时属于两者时显示两行。

### 3.6 物品动作行为映射

行为 Profile 路径为：

```text
data/<namespace>/djcraft/item_behaviors/<profile>.json
```

文件名定义 Profile ID，不直接定义目标物品。目标由选择器指定：

```json
{
  "priority": 100,
  "selectors": {
    "items": ["examplemod:long_spear"],
    "tags": ["examplemod:spears"]
  },
  "behavior": "djcraft:trident",
  "melee": {
    "cylinder_length": 5.0,
    "radius": 1.0
  }
}
```

- `priority` 可省略，默认 `0`，范围 `0..1000`；
- `selectors.items` 和 `selectors.tags` 是资源 ID 数组，至少一个非空且不能包含重复项；
- `behavior` 支持 `djcraft:bow`、`djcraft:crossbow`、`djcraft:shield`、`djcraft:charge`、
  `djcraft:trigger`、`djcraft:trident`、`djcraft:mace`、`djcraft:none`，以及附属 Mod 在
  `RegisterDJItemBehaviorsEvent` 中注册的其他资源 ID；
- 兼容旧配置的 `bow`、`crossbow`、`shield` 仍只接受对应原版基类；`charge`、`trigger`、
  `trident`、`mace` 可应用到其他注册物品；
- `charge` 接入标准长按使用与释放事件；释放是否生成效果仍由原物品的标准释放逻辑决定；
- `trigger` 在 Hit 后由服务端调用来源物品栈的 `ItemStack.use()`，并追踪该调用期间生成、owner
  为玩家的投射物；
- `trident` 选择单远端半球胶囊范围左键和即时三叉戟投掷；默认圆柱长度 5、半径 1。左键对实际
  受伤目标追加 1.5 三维径向击退（玩家眼位指向目标中心）；右键投射物永久无重力，带忠诚时固定
  3 拍后返回，并在投掷伤害后
  施加 `djcraft:rend` II 8 秒；`djcraft:rending` 每级再提高 1 个撕裂等级。生成的 DJ 三叉戟使用原版 2 倍宽高碰撞箱和同步发光轮廓；忠诚仅在
  投射物中心进入主人眼位 0.25 格内时完成回收，不使用扩大碰撞箱产生的提前吸附。投射物不穿透实体
  或方块，首次碰撞会立即开始忠诚返程；返程以最高 2.5 速度再攻击一个敌对生物。近战打回沿攻击者
  瞄准方向以 2.5 速度重新发射；右键投掷每次实际伤害独立增加连击，不使用通用 sequence 去重；
  `mace` 选择同类范围左键，默认圆柱长度 2、半径 2；
- `djcraft:none` 用于退出 Java 继承提供的自动行为，不会给普通物品增加新能力。

`melee` 可省略并按所选行为使用注册默认值，也可部分覆盖。普通软捕获行为支持：

```json
"melee": {
  "reach": 4.25,
  "horizontal_angle_degrees": 30.0,
  "vertical_angle_degrees": 20.0
}
```

注册范围行为支持 `cylinder_length` 和 `radius`。距离、长度、半径必须是 `(0, 64]` 的有限数，
两个角度必须是 `[0, 90)` 的有限数。软捕获字段写入范围行为、胶囊字段写入普通行为、未知字段或
非法数值都会只拒绝该 Profile。缺失字段从注册默认值补齐；`/reload` 只改变之后开启的攻击窗口，
已有 2 tick 窗口继续使用自己的不可变快照。参数是服务端权威数据，不随行为 ID 映射同步给客户端。
内置三叉戟和重锤 Profile 分别位于
`data/minecraft/djcraft/item_behaviors/trident.json` 与
`data/minecraft/djcraft/item_behaviors/mace.json`；同资源路径的高优先级数据包文件可整体覆盖它们。

没有显式匹配时，真实子类仍按 Java 继承关系零配置获得内置行为。解析顺序为精确物品、命中标签、Java 继承回退；精确物品始终高于标签，同层按 `priority` 降序、Profile ID 字典序选择。同优先级冲突会记录警告。

重载时会分别拒绝错误 Profile，保留其他合法文件，并原子发布最终物品映射。未在 common setup
注册表中出现的行为 ID 视为非法；附属 Mod 的注册必须早于首次服务端数据包重载。玩家加入及
`/reload` 后，服务端把完整显式“物品 ID → 行为 ID”映射（包括 `djcraft:none` 和附属行为）同步
给客户端；客户端数据包副本和标签状态不作为战斗权威。同步不传输 Java 兼容谓词或执行器，
因此对应附属 Mod 必须在两端注册相同 ID。删除覆盖文件并重载会恢复下层数据包或 Java 继承回退。

该格式本身只选择已注册行为，不能注册 Java 类、方法、网络 payload 或脚本。使用标准
`use`/释放/投射物 owner 契约的第三方物品可通过 `charge` 或 `trigger` 纯数据包接入；需要新
服务端逻辑时，附属 Mod 可先用公共 API 注册 trigger-family 执行器，再由此 Profile 选择其 ID。
`disabledByCanAttack` 是代码注册项属性，默认 `true`，不能由数据包 Profile 覆盖；内置盾牌以及
不可分配给物品的 `djcraft:mining`、`djcraft:eating`、`djcraft:dash`、`djcraft:ground_jump`、
`djcraft:double_jump`、`djcraft:ground_slam` 为 `false`，
不可分配给物品的 `djcraft:melee` 为 `true`。每种动作按自己的注册行为读取该值；盾牌左键解析
`djcraft:melee`，右键举盾解析 `djcraft:shield`。
这些非物品行为仅供对应内置动作的客户端判定和服务端 proof 复核共享策略，不能在 `item_behaviors` Profile
中选择。依赖私有客户端状态、专用网络包或全新 proof 生命周期的武器仍需要扩展 DJCraft API。

## 4. 当前内置配方

### 4.1 空白唱片：唱片碎片

资源 ID：`djcraft:empty_disc_from_fragments`

```json
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [
    { "item": "minecraft:disc_fragment_5" },
    { "item": "minecraft:disc_fragment_5" }
  ],
  "result": {
    "id": "djcraft:empty_disc",
    "count": 1
  }
}
```

### 4.2 空白唱片：任意原版音乐唱片

资源 ID：`djcraft:empty_disc_from_discs`

输入使用原版标签 `minecraft:music_discs`，输出一个 `djcraft:empty_disc`。

### 4.3 瓶中音符

资源 ID：`djcraft:note_in_a_bottle`

无序合成输入为一个 `minecraft:glass_bottle` 和任意一个
`minecraft:music_discs` 标签成员，输出一个 `djcraft:note_in_a_bottle`。

### 4.4 DJ 刻录台

资源 ID：`djcraft:dj_crafting_table`

无序合成输入为一个 `minecraft:crafting_table` 和一个 `minecraft:note_block`，
输出一个 `djcraft:dj_crafting_table`。旧的误命名
`djcraft:portable_jokebox` 配方资源已移除。

### 4.5 Flowery

资源 ID：`djcraft:flowery`

无序合成输入为金锭、蒲公英、钻石块和下界之星各一个，输出一个
`djcraft:flowery`。

### 4.6 便携点唱机

资源 ID：`djcraft:portable_jukebox`

无序合成输入为一个 `minecraft:note_block`，输出一个
`djcraft:portable_jukebox`。

## 5. 覆盖内置配方

数据包中使用相同 namespace 和路径即可覆盖。例如将空白唱片成本改为四个唱片碎片：

```text
data/djcraft/recipe/empty_disc_from_fragments.json
```

```json
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [
    { "item": "minecraft:disc_fragment_5" },
    { "item": "minecraft:disc_fragment_5" },
    { "item": "minecraft:disc_fragment_5" },
    { "item": "minecraft:disc_fragment_5" }
  ],
  "result": {
    "id": "djcraft:empty_disc",
    "count": 1
  }
}
```

要移除配方，使用当前 Minecraft 1.21.1/NeoForge 支持的配方禁用方式或让更高优先级数据包覆盖它。上线前用 `/recipe` 或配方查看 Mod 验证最终加载结果。

## 6. 为 DJCraft 物品新增配方

当前注册物品 ID：

| ID | 说明 |
|---|---|
| `djcraft:empty_disc` | 空白/已刻录唱片共用物品类型，最大堆叠 1 |
| `djcraft:portable_jukebox` | 54 槽便携点唱机，最大堆叠 1 |
| `djcraft:dj_crafting_table` | DJ 刻录台方块物品 |
| `djcraft:djfumo` | DJ Fumo 物品，最大堆叠 1 |
| `djcraft:note_in_a_bottle` | 瓶中音符，最大堆叠 1 |
| `djcraft:band_of_energy` | 能量手环，最大堆叠 1 |
| `djcraft:flowery` | Flowery 被动物品，最大堆叠 1 |

这些注册 ID 的实际被动效果、生效槽位和获取循环见
[游戏机制与实现：被动物品与获取方式](gameplay-mechanics.md#passives)。

## 7. 标签兼容

DJCraft 提供 `djcraft:swift` 与 `djcraft:smash` 物品标签，用于服务端节拍类别伤害倍率。
专用动作由 Java 继承回退或 `item_behaviors` Profile 判断。内置回退包括：

- `BowItem`
- `CrossbowItem`
- `TridentItem`
- `MaceItem`
- `WindChargeItem`
- `ShieldItem`
- 其他主手攻击走通用近战路径

仅加入 `swift`/`smash` 标签不会选择动作执行器；第三方物品需要继承对应原版类型，或通过
`item_behaviors` 明确选择一个已注册行为。标准蓄力/触发型物品可分别选择 `djcraft:charge`
和 `djcraft:trigger`，无需 Java 兼容代码；附属 Mod 也可通过公共注册事件提供新 ID，再由数据包
选择。

三叉戟、重锤、弓、弩、风弹和盾牌的实际 DJ 动作已集中到
[游戏机制与实现：武器专用机制](gameplay-mechanics.md#weapons) 与
[盾牌与招架](gameplay-mechanics.md#shield)。本数据包只控制已经接入流程的时序、能耗、
行为选择和类别标签；JSON 本身不能创造新的 Java 执行逻辑。

附属 Mod/整合包仍可在自己的标签中引用 DJCraft 内容，例如：

```text
data/examplepack/tags/item/dj_storage.json
```

```json
{
  "replace": false,
  "values": [
    "djcraft:portable_jukebox"
  ]
}
```

该标签只有被其他配方、战利品表或附属 Mod 代码读取时才产生效果。

## 8. 战利品表、进度与函数

DJCraft 通过 NeoForge 追加式全局战利品修改器提供以下内置结构箱子掉落；每个概率均按
每次生成箱子战利品独立计算，成功时追加 1 个物品，不替换原版战利品：

| 原版箱子战利品表 | 追加物品 | 概率 |
|---|---|---:|
| `minecraft:chests/abandoned_mineshaft` | `djcraft:band_of_energy` | 20% |
| `minecraft:chests/ruined_portal` | `djcraft:band_of_energy` | 15% |
| `minecraft:chests/ancient_city` | `djcraft:djfumo` | 4% |

入口列表位于 `data/neoforge/loot_modifiers/global_loot_modifiers.json`，各修改器位于
`data/djcraft/loot_modifiers/`，并通过 `neoforge:add_table` 追加
`data/djcraft/loot_table/inject/` 中的子战利品表。更高优先级数据包可按 NeoForge
全局战利品修改器合并/替换规则调整或移除这些入口。

其他数据包也可按原版规则引用 DJCraft 物品：

- 将 `djcraft:empty_disc` 加入结构/实体战利品表；
- 以获得 `djcraft:portable_jukebox` 作为进度条件；
- 在函数中执行 `/give @s djcraft:empty_disc`；
- 使用 `/dj` 命令控制已加载曲目，但命令权限仍由服务端权限系统决定。

`/dj play` 的曲目参数来自 DJCraft JAR 内置 `djcraft/trackpacks/<packId>/` 或外置
`<游戏目录>/djcraft/trackpacks`，不是数据包资源 ID。数据包不能把 `track.json` 放在
`data/<namespace>/...` 后自动注册为曲目。

## 9. 带数据组件的唱片

已刻录唱片使用三个 DJCraft 数据组件：

| 组件 ID | 持久化值 | 正常写入方 |
|---|---|---|
| `djcraft:track_pack_id` | string | DJ 刻录台服务端处理器 |
| `djcraft:disc_id` | UUID | DJ 刻录台服务端处理器 |
| `djcraft:disc_statistics` | `{max_combo, total_play_time_ms}` | DJ 会话统计服务 |

Minecraft 1.21.1 的物品堆栈 JSON/NBT 语法能够表达数据组件，但不建议数据包手工批量生成“已刻录唱片”：

- `track_pack_id` 必须对应服务端真实已加载曲目；
- `disc_id` 应全局唯一，静态战利品表难以保证；
- 重复 UUID 会在播放解析时被重新分配；
- 伪造统计可能直接改变镀金唱片显示；
- 不同命令/战利品上下文的数据组件编码语法应以 Minecraft 1.21.1 为准。

正常内容制作应发放空白唱片，再让玩家在 DJ 刻录台选择曲目。

## 10. 仍由配置管理的功能

以下值在 NeoForge 配置中，而非数据包中：

- `maxTrackPackDownloadMiB`；
- `toleranceRechargeTicks`；
- `maxAirJumps`、作为冲刺方向冲量的 `dashHorizontalSpeed`；
- 调试 HUD 和调试节拍声音。

配置修改方式和生效时机应遵循服务器生成的 DJCraft 配置文件；不要在数据包中创建同名
JSON，当前代码不会读取。默认值和对玩法的精确影响见
[游戏机制与实现：配置](gameplay-mechanics.md#operations)。

基础容错上限、不卡拍攻击剩余伤害和不攻击断连阈值属于随世界保存的原版 gamerule，而非配置或
数据包字段：`djcraftBaseMaxToleranceChances`、`djcraftOffBeatAttackDamagePercent`、
`djcraftIdleAttackableBeatsBeforeComboReset`。旧 `maxToleranceChances` 配置值不会自动迁移。

## 11. 当前未完成的数据包扩展点

1. **没有数据包曲目注册表。** 曲目只从 DJCraft JAR 内置目录或游戏目录外置目录/`.djcraft` 扫描。
2. **战斗 Profile 仍不完整。** 物品冷却、换物预热和已判定动作的能量成本已数据化，
   `item_behaviors` 可选择内置或附属 Mod 注册的行为 ID，并可覆盖已注册普通近战距离/角度或
   胶囊长度/半径；伤害公式、窗口时长和移动规则仍不能由 JSON 任意定义。只配置
   `use_energy_cost` 不会自动选择 `charge` 或 `trigger`。
3. **物品标签扩展范围有限。** `djcraft:swift` 与 `djcraft:smash` 已用于节拍类别伤害，
   但点唱机唱片限制等其他物品识别仍未通过标签扩展。
4. **没有自定义配方类型。** 刻录不是工作台配方，而是 UI + 服务端网络处理。
5. **只有已声明的 Schema。** 除 `djcraft/item_timing`、`djcraft/item_behaviors`、
   `djcraft/cyber_grind` 和原版资源外，任意自定义 JSON 不会被自动扫描。
6. **属性数据化未完成。** `djcraft:max_energy` 的默认值和玩家挂载由 Java 注册代码控制。

## 12. 验证清单

- 用 `/reload` 检查数据包错误，不要用 `/dj reload` 代替。
- 修改、覆盖或删除物品时序 Profile 后执行 `/reload`，核对服务器日志、Tooltip、切换前摇和实际攻击冷却。
- 多人环境确认新玩家加入和在线 `/reload` 后均收到服务端 Profile，不依赖客户端安装同一数据包。
- 用 `/recipe give` 或配方查看 Mod 确认最终配方 ID 和优先级。
- 核对 1.21.1 单数目录名以及结果字段 `id`。
- 发放空白唱片后实际经过 DJ 刻录台，验证服务端有对应曲目。
- 多人服务器同时检查服务端和客户端曲目包一致性；数据包不会同步曲目音频。
- 为标准第三方蓄力/触发武器分别测试 `charge`/`trigger` 行为；确认服务端执行原物品逻辑、
  弹药/耐久落在动作来源槽位，且发射后切换物品不改变投射物倍率或声音身份。

## 13. 电子血宫预设

文件路径为 `data/<namespace>/djcraft/cyber_grind/<id>.json`，资源 ID 是
`<namespace>:<id>`。同一资源 ID 按正常数据包优先级覆盖。`/reload` 会重建预设列表并同步
摘要；只影响之后创建的实例，已经开始或正在准备的实例持有不可变快照。维度本身位于
`data/djcraft/dimension/cyber_grind.json` 和 `data/djcraft/dimension_type/cyber_grind.json`，
它们属于世界动态注册表资源，不能把 `/reload` 当成修改活动世界维度定义的安全方式。
内置维度类型使用 `fixed_time: 18000` 固定午夜视觉时间，因此不会关闭主世界或其他维度的
昼夜循环；修改这个字段需要完整退出并重新加载世界验证。

完整格式：

```json
{
  "display_name": {"text": "Default Cyber Grind"},
  "description": {"text": "Endless survival"},
  "advance_threshold": 5,
  "warning_ticks": 40,
  "party_budget_per_extra_player": 0.75,
  "budget_ranges": [
    {"min_wave": 1, "max_wave": 10, "base_budget": 8, "budget_per_wave": 2, "max_budget": 26},
    {"min_wave": 11, "base_budget": 30, "budget_per_wave": 3, "max_budget": 160}
  ],
  "entries": [
    {
      "entity": "minecraft:zombie",
      "cost": 1,
      "draw_weight": 10,
      "min_wave": 1,
      "chance": 1.0,
      "min_count": 1,
      "max_count": 12,
      "nbt": "{CustomName:'{\"text\":\"Example\"}'}"
    }
  ]
}
```

`budget_ranges` 必须从第 1 波开始，无间隙、无重叠并最终以省略 `max_wave` 的区间覆盖到
无上限。某波基础预算为
`min(max_budget, base_budget + (wave - min_wave) * budget_per_wave)`，再乘存活玩家倍率并
向下取整。`chance` 必须在 `[0,1]`；`cost`、`draw_weight` 为正数；数量必须满足
`0 <= min_count <= max_count`。整体结构或预算区间错误会拒绝整个预设；单个敌人条目错误只
忽略该条目并记录资源 ID 和原因，没有剩余有效条目时仍会拒绝预设。

`nbt` 在资源加载时按 SNBT 解析，UTF-8 字节数最多 64 KiB。名称、属性、装备和 `Tags`
等普通实体数据会合并；`id`、UUID、`Pos`、`Rotation`、`Motion`、维度、乘客和传送相关
保留字段会被删除，最终实体类型、位置、旋转、速度、维度和乘客始终由服务器决定。生成器
每波只对条目执行一次概率判定，先满足可容纳的最小数量，再按 `draw_weight` 补充且绝不
超过预算；概率产生空波时回退到当前波可容纳的最低成本条目。

内置 `djcraft:default` 包含僵尸、骷髅、蜘蛛、苦力怕、尸壳、溺尸、掠夺者、卫道士、
末影人、凋灵骷髅、女巫、烈焰人、旋风人、唤魔者、劫掠兽、监守者和凋灵；监守者从第
20 波起以 12% 概率出现，凋灵从第 30 波起以 8% 概率出现，二者每波至多 1 只。末影龙不在
默认池中。没有任何有效预设时，刻录台电子血宫页会显示不可进入原因。

内置兼容预设 `djcraft:cataclysm` 使用标准 `neoforge:mod_loaded` 数据加载条件，仅在安装
Mod ID 为 `cataclysm` 的 L_Ender's Cataclysm 时出现。它包含适合固定干燥平台的普通敌人、
小 Boss，以及末影守卫、先驱者、下界合金巨兽、焰魔、远古遗魂、咒翼灵骸和斯库拉等
后期 Boss；利维坦因离水无敌而明确排除。可驯服宠物、纯水生小怪和技术性投射物不会进入
怪池。自定义电子血宫
预设同样可以在根对象使用 `neoforge:conditions`；条件不满足时文件会在解析前静默跳过。

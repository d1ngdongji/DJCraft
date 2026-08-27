# DJCraft 资源包开发文档

> 适用版本：DJCraft 2.2.0、Minecraft 1.21.1、NeoForge 21.1.218。代码核对日期：2026-08-07。
> 本文描述普通 Minecraft 客户端资源包。HUD、声音和动画的玩家可见机制见
> [游戏机制与实现](gameplay-mechanics.md#feedback)；外置曲目包的 OGG、beat、唱片和
> 连击贴图规范见 [曲目包功能文档](trackpack-feature.md)。

## 1. 能覆盖的内容

DJCraft 客户端资源可分为五类：

| 类别 | 是否支持 F3+T | 是否有 DJCraft 自定义加载器 | 典型用途 |
|---|---|---|---|
| 原版资源 | 是 | 否 | 语言、模型、贴图、声音、着色器 |
| 第一人称动画 JSON | 是 | 是 | 覆盖 DJCraft 内置固定动作 clip |
| 武器音效 Profile JSON | 是 | 是 | 为附属 Mod 武器配置语义音效规则 |
| 射线效果 Profile JSON | 是 | 是 | 配置即时射线的光束、爆发和枪口偏移 |
| 动态曲目包资源 | 是；新增文件还需曲目包重载 | 是 | OGG、beat PNG/GIF、唱片模型/贴图、连击数字 |

资源包不会改变服务端战斗逻辑、配方、属性或曲目时间线。相关内容分别使用数据包、配置文件、曲目包或附属 Mod 代码。

## 2. 基础目录

Minecraft 1.21.1 资源包可使用 `pack_format: 34`。最小结构：

```text
MyDJCraftResources/
├─ pack.mcmeta
└─ assets/
   ├─ djcraft/                 # 覆盖 DJCraft 固有资源
   └─ <addon_modid>/           # 附属 Mod 自有声音与武器音效 Profile
```

```json
{
  "pack": {
    "pack_format": 34,
    "description": "My DJCraft resources"
  }
}
```

修改后使用 `F3+T`。第一人称动画、武器音效 Profile、射线效果 Profile、Falling beat PNG/GIF 和连击数字颜色都会在正常客户端资源重载中重新解析。

## 3. 可直接覆盖的固定资源

### 3.1 HUD 贴图

```text
assets/djcraft/textures/crosshair/vshape.png
assets/djcraft/textures/crosshair/dot.png
assets/djcraft/textures/crosshair/horizontal_line.png
assets/djcraft/textures/crosshair/vertical_line.png

assets/djcraft/textures/gui/combo/0.png ... 9.png
assets/djcraft/textures/gui/combo/50/0.png ... 9.png
assets/djcraft/textures/gui/energy/frame.png
assets/djcraft/textures/gui/energy/fill.png
assets/djcraft/textures/gui/tolerance/heart.png
assets/djcraft/textures/gui/tolerance/heart_empty.png
assets/djcraft/textures/gui/beats/blue_beat.png
assets/djcraft/textures/gui/beats/green_beat.png
assets/djcraft/textures/gui/beats/white_beat.png
```

`beats/blue_beat.png` 是 Falling beat 的默认及图片缺失、损坏或超限时的内置后备；
绿色和白色版本可由 definition 或事件 props 使用完整资源位置选择。
基础数字用于 1–49 连击，`combo/50/` 数字用于没有自定义连击贴图的曲目达到 50 连击后。普通资源包可以替换两套固定资源。

连击数字的发光颜色不是单独配置项。资源重载时会统计每张数字贴图所有非全透明像素，取出现次数最多的 RGB 作为该数字的效果颜色。完全透明或无法读取的内置高连击贴图回退到对应基础数字；基础数字无法读取时使用白色效果。

连击泛光/残影/彩虹/对数增长阈值、中心准星、Flowery 与普通冲刺残影等玩家可见行为已
迁移到 [游戏机制与实现：HUD、声音与第一人称反馈](gameplay-mechanics.md#feedback) 和
[移动能力与 Flowery 冲刺](gameplay-mechanics.md#movement)。本节只定义可覆盖资源及其
加载回退；资源包不能改变这些玩法阈值和持续时间。

### 3.2 物品和方块资源

可覆盖的普通静态资源包括：

```text
assets/djcraft/blockstates/dj_crafting_table.json
assets/djcraft/models/block/dj_crafting_table.json
assets/djcraft/models/item/band_of_energy.json
assets/djcraft/models/item/dj_crafting_table.json
assets/djcraft/models/item/djfumo.json
assets/djcraft/models/item/note_in_a_bottle.json
assets/djcraft/models/item/laser_crossbow.json
assets/djcraft/models/item/laser_crossbow_charged.json
assets/djcraft/models/item/magic_crossbow.json
assets/djcraft/models/item/magic_crossbow_charged.json
assets/djcraft/models/item/assault_crossbow.json
assets/djcraft/models/item/assault_crossbow_charged.json
assets/djcraft/models/item/explosive_bow_base.json
assets/djcraft/models/item/explosive_bow.json
assets/djcraft/models/item/explosive_bow_stage1.json
assets/djcraft/models/item/explosive_bow_stage2.json
assets/djcraft/models/item/explosive_bow_stage3.json
assets/djcraft/models/item/perfect_disc.json
assets/djcraft/models/item/portable_jukebox.json
assets/djcraft/textures/block/dj_crafting_table/*.png
assets/djcraft/textures/item/*.png
assets/djcraft/textures/item/mp3/*.png
assets/djcraft/textures/mob_effect/rend.png
```

`assets/djcraft/models/item/empty_disc.json` 会被强制启用、位于资源包顶部的动态曲目资源包生成版本覆盖，以便按曲目和镀金状态选择模型。因此普通资源包不应依赖替换该文件；可改内置后备贴图 `textures/item/empty_disc.png` 和 `textures/item/perfect_disc.png`。

`djcraft:note_in_a_bottle` 使用普通 generated 物品模型；模型与贴图分别位于
`assets/djcraft/models/item/note_in_a_bottle.json` 和
`assets/djcraft/textures/item/note_in_a_bottle.png`，均可由客户端资源包覆盖。

`djcraft:band_of_energy` 同样使用普通 generated 物品模型；模型与贴图分别位于
`assets/djcraft/models/item/band_of_energy.json` 和
`assets/djcraft/textures/item/band_of_energy.png`。

`djcraft:laser_crossbow` 使用原版弩的持有/使用语义和 DJCraft 弩后坐动画。紫色
`textures/item/laser_crossbow.png` 用于未装填或 DJ 冷却状态，绿色
`textures/item/laser_crossbow_charged.png` 用于原版已装填或 DJ 可开火状态。模型只覆盖外观，
不会改变服务器弹药或射线规则。

`djcraft:magic_crossbow` 同样保留原版弩语义与 DJCraft 后坐动画，并使用独立的
`magic_crossbow.png` / `magic_crossbow_charged.png` 贴图和对应模型入口。

`djcraft:assault_crossbow` 使用 `assault_crossbow.png` / `assault_crossbow_charged.png`；其
`ray_effects/assault_crossbow.json` 定义无爆发的深灰色弹痕、600 ms 总寿命，以及从近端向远端推进的渐隐边界。

`djcraft:explosive_bow` 使用 `explosive_bow_stage0.png` 到 `stage3.png`。非 DJ 原版拉弓沿用
`pulling`/`pull` predicate；DJ 自动蓄力使用 `djcraft:auto_charge`，在虚拟节拍进度 0.25、0.5、0.75
切换到后三阶段。资源包覆盖模型时应同时保留两组 predicate，避免只在一种模式下显示蓄力。

`djcraft:rend` 的状态效果图标位于 `textures/mob_effect/rend.png`，内置资源为 18×18 暗红撕裂图案；
资源包可按同路径替换。效果的增伤等级、持续时间和施加条件由服务端玩法逻辑决定，不由图标控制。

### 3.3 语言

```text
assets/djcraft/lang/en_us.json
assets/djcraft/lang/zh_cn.json
```

遵循原版语言 JSON，键值均为字符串。资源包可以覆盖已有键；新增附属 Mod 文本应放在附属 Mod 自己的 namespace。

物品 DJ Profile Tooltip 使用以下语言键：

- `tooltip.djcraft.beat_cooldown`
- `tooltip.djcraft.use_beat_cooldown`
- `tooltip.djcraft.switch_warmup`
- `tooltip.djcraft.attack_energy_cost`
- `tooltip.djcraft.use_energy_cost`
- `tooltip.djcraft.item_tag.swift`
- `tooltip.djcraft.item_tag.smash`
- `tooltip.djcraft.dj_crafting_table_usage`
- `tooltip.djcraft.portable_jukebox_usage`

会话开始时的成就式曲目提示标题使用 `toast.djcraft.now_playing`。提示中的曲目名称来自
曲目包 `meta.display_name`（缺失时回退曲目包 ID），图标沿用动态唱片物品模型，因此
`disc.png` 及内置 `empty_disc.png` 后备贴图会像物品栏中的唱片一样响应资源重载。

内置附魔名称使用 `enchantment.djcraft.aerial_step`、`enchantment.djcraft.rending`、
`enchantment.djcraft.ray_overcharge` 和 `enchantment.djcraft.lingering_sweep`；资源包可只覆盖显示名称，
附魔等级、适用物品和效果仍由服务端数据包与代码决定。

攻击或使用能耗为 0 时，对应能耗行不显示。能耗数值来自服务器同步的数据包 Profile；资源包只能覆盖显示文本，不能改变实际成本。

### 3.4 着色器

```text
assets/djcraft/shaders/core/combo_glow.json
assets/djcraft/shaders/core/combo_glow.fsh
assets/djcraft/shaders/core/falling_impact_ring.json
assets/djcraft/shaders/core/falling_impact_ring.fsh
assets/djcraft/shaders/core/parry_shield_glow.json
assets/djcraft/shaders/core/parry_shield_glow.vsh
assets/djcraft/shaders/core/parry_shield_glow.fsh
assets/djcraft/shaders/core/ray_effect.json
assets/djcraft/shaders/core/ray_effect.vsh
assets/djcraft/shaders/core/ray_effect.fsh
```

`falling_impact_ring` 在一个矩形 HUD quad 内以片元距离场生成椭圆柔光环、亮核心、
延迟扩散环和起始闪光；Java 只提交进度与 definition `color`，不再拼接环形几何。

`ray_effect` 使用 `POSITION_TEX_COLOR` 顶点格式绘制相机朝向光束、径向爆发和缓存的三维球面，Java 设置
`EffectMode`、`Progress`、`Time`、`BeamFadeFromNear` 与 `BeamWidthAnimated`。它使用加法混合、深度测试和关闭深度写入；加载或编译失败
只会禁用射线视觉，不改变服务端伤害。

着色器接口与 Minecraft 1.21.1 渲染管线绑定。修改 uniform、attribute 或 sampler 名称前必须同步核对 `DJComboHudRenderer`、`DJFallingBeatRenderer`、`DJParryShieldRenderer` 与 `DJRayEffectRenderer` 的加载和赋值代码。着色器编译失败可能使对应视觉效果不可用。

### 3.5 射线效果 Profile

路径为 `assets/<namespace>/djcraft/ray_effects/<effect>.json`。服务器 `ray_weapons.effect` 发送
这个资源 ID，客户端按资源包优先级解析；缺失或非法样式会记录错误并跳过该效果，不影响玩法。

```json
{
  "core_color": "#A6FF70A0",
  "halo_color": "#32FF7058",
  "core_width": 0.035,
  "halo_width": 0.11,
  "beam_lifetime_ms": 180,
  "beam_fade_from_near": false,
  "beam_width_start_scale": 1.0,
  "beam_width_peak_scale": 0.0,
  "burst_lifetime_ms": 240,
  "burst_start_radius": 0.12,
  "burst_end_radius": 0.75,
  "pulse_speed": 8.0,
  "muzzle_burst_scale": 1.0,
  "contact_burst_scale": 1.0,
  "end_burst_scale": 1.0,
  "shockwave_lifetime_ms": 0,
  "shockwave_start_radius": 0.0,
  "shockwave_core_color": "#E03225CC",
  "shockwave_halo_color": "#991F1688",
  "first_person_main_muzzle": [0.0, -0.18, 0.45],
  "first_person_offhand_muzzle": [-0.22, -0.18, 0.45],
  "third_person_muzzle": [0.28, -0.35, 0.35]
}
```

颜色接受 `#RRGGBB` 或 `#RRGGBBAA`；宽度、寿命和 `pulse_speed` 必须为正，爆发半径必须非负且结束值不小于
起始值，偏移必须是三个有限数字。偏移顺序为 `[相机右侧、上方、前方]`；第一人称的
`first_person_main_muzzle` 与 `first_person_offhand_muzzle` 分别独立配置，适配装填弩在主手开火时移到
画面中央的姿态。第三人称 `third_person_muzzle` 仍以主手为基准，副手自动镜像横向分量。旧资源可继续只写
`first_person_muzzle`，此时它作为主手偏移且副手自动镜像；不能同时缺少旧字段和任一手别专用字段。
`muzzle_burst_scale`、`contact_burst_scale` 与 `end_burst_scale` 分别缩放枪口、权威实体交点和最终
截断点的爆发半径，必须为非负有限数，省略时均为 `1.0`；设为 `0` 可隐藏对应位置的爆发。
`beam_fade_from_near` 可省略并默认 `false`，此时整条光束同时渐隐；设为 `true` 时消失边界按 UV
从枪口向权威终点推进，让近端先消失、远端最后消失。光束在 `beam_lifetime_ms` 结束时完全消失；
所有射线光束均使用连续实线，不再沿长度方向生成周期性明暗带；`pulse_speed` 仅继续控制爆发和冲击波
等仍使用 `Time` 的动画。
`beam_width_peak_scale` 省略或设为 `0` 时禁用宽度动画；启用时必须不小于非负的
`beam_width_start_scale`。光束从起始倍率平滑增粗到峰值倍率，再收缩到宽度 0；动画期间不改变
颜色透明度；渲染器通过 `BeamWidthAnimated` 告知着色器由几何宽度负责消失。内置激光弩使用
`0.25` 起始倍率、`2.25` 峰值倍率和 360 ms 光束寿命。
爆发在枪口、每个权威实体交点和最终截断点以 `burst_start_radius` 为初始半径，
在寿命中点扩张至 `burst_end_radius`，随后收缩回初始半径并渐隐。所有 JSON 在
F3+T 准备阶段解析并原子发布，不在渲染热路径读取文件。

`shockwave_lifetime_ms` 省略或设为 `0` 时禁用球形冲击波；启用时必须是正整数。
`shockwave_start_radius` 为非负有限数，终止半径来自服务端权威射线载荷（爆炸弓为 5 或 8 格）。
两项 shockwave 颜色可省略并分别回退到普通 core/halo 颜色。渲染器使用固定缓存的 16×24 球面网格、
三次 ease-out 扩张和渐隐，不产生粒子；爆炸弓默认持续 450ms、从 0.25 格开始扩张。
请求的效果 Profile 缺失或解析失败时会记录一次错误，并退化为 `djcraft:generic` 的普通短射线；
该回退不包含球形冲击波，也不改变服务端端点、半径或伤害。

## 4. 声音资源

### 4.1 DJCraft 内置声音事件

当前内置事件：

| 声音事件 | 默认文件 |
|---|---|
| `djcraft:ability.dash` | `assets/djcraft/sounds/abilities/dash.ogg` |
| `djcraft:ability.flowery_dash_1` | `assets/djcraft/sounds/abilities/flowery_dash_1.ogg` |
| `djcraft:ability.flowery_dash_2` | `assets/djcraft/sounds/abilities/flowery_dash_2.ogg` |
| `djcraft:ability.flowery_dash_3` | `assets/djcraft/sounds/abilities/flowery_dash_3.ogg` |
| `djcraft:ability.flowery_dash_4` | `assets/djcraft/sounds/abilities/flowery_dash_4.ogg` |
| `djcraft:ability.double_jump` | `assets/djcraft/sounds/abilities/double_jump.ogg` |
| `djcraft:ability.ground_slam_whoosh` | `assets/djcraft/sounds/abilities/ground_slam_whoosh.ogg` |
| `djcraft:ability.ground_slam_land` | `assets/djcraft/sounds/abilities/ground_slam_land.ogg` |
| `djcraft:ability.ground_slam_impact` | `assets/djcraft/sounds/abilities/ground_slam_impact.ogg` |
| `djcraft:combat.parry` | `assets/djcraft/sounds/combat/parry.ogg` |
| `djcraft:weapon.laser_crossbow_shoot` | `assets/djcraft/sounds/weapons/laser_crossbow_shoot.ogg` |
| `djcraft:weapon.magic_crossbow_shoot` | `assets/djcraft/sounds/weapons/magic_crossbow_shoot.ogg` |
| `djcraft:weapon.assault_crossbow_shoot` | `assets/djcraft/sounds/weapons/assault_crossbow_shoot.ogg` |
| `djcraft:weapon.explosive_bow_charge` | `assets/djcraft/sounds/weapons/explosive_bow_charge.ogg` |
| `djcraft:weapon.explosive_bow_shoot` | `assets/djcraft/sounds/weapons/explosive_bow_shoot.ogg` |
| `djcraft:weapon.trident_redirect` | `assets/djcraft/sounds/weapons/trident_redirect.ogg` |
| `djcraft:weapon.ray_hit` | `assets/djcraft/sounds/weapons/ray_hit.ogg` |

可直接覆盖 OGG，或在 `assets/djcraft/sounds.json` 中按原版格式替换事件定义。

按世界坐标播放、需要距离衰减的 OGG 必须使用单声道。OpenAL 不会把双声道缓冲当作可空间化的点声源；即使
Profile 的 `spatial` 为 `true`，双声道文件仍可能让远处玩家听到近似固定响度。内置的冲刺、二段跳、
Flowery 冲刺、下砸落地/冲击、格挡、武器发射/蓄力/命中和三叉戟打回音效均保持单声道。只发给本人的
UI 提示和 DJ 曲目不受此限制。

下砸开始、普通触地和满足卡拍/下落高度门槛的范围砸地分别使用上述三个 `ground_slam_*` 事件；
声音资源不会改变 3 格门槛、4 格半径、1.0 竖直击飞或伤害数值。满足特殊砸地条件时使用固定
64 个 `BLOCK` 方块碎屑构成宽范围喷发，材质取服务端确认的脚下支撑方块；该粒子规则目前不是
资源包可配置项。

激光弩、魔法弩和冲锋弩的 `trigger_impact` 分别使用各自的射击事件，命中合法目标时共同使用
`djcraft:weapon.ray_hit`；对应 Profile 为 `assets/djcraft/djcraft/weapon_sounds/laser_crossbow.json`
、`magic_crossbow.json` 和 `assault_crossbow.json`。击中音使用共用的 OGG 文件。
只有节拍 Hit 的实际伤害会广播 `target_hit`。由 `djcraftOffBeatAttackDamagePercent` 放行的 Miss
可以匹配开火阶段的 `when.beat: "miss"` 规则，但即使伤害目标也不会触发 `target_hit`。原版受伤、
投射物碰撞、发射和爆炸等世界声音不受此限制。`target_hit` 的空间音源位于攻击玩家的位置，
而不是受伤目标的位置。

爆炸弓的 `charge_start` 使用提供的蓄力 OGG，按下后经过 1.0 虚拟节拍的 `trigger_impact` 使用提供的发射 OGG；冲击点
原版爆炸声由服务端玩法逻辑单独广播，不由资源 Profile 触发。对应 Profile 为
`assets/djcraft/djcraft/weapon_sounds/explosive_bow.json`。蓄力实例按射手、动作序号、手和 Profile
追踪；发射事件会先停止对应蓄力实例再播放发射音效，客户端取消蓄力时也会立即停止。

返回中的 DJ 三叉戟被近战打回时，由服务端在三叉戟位置播放 `weapon.trident_redirect`。内置文件是
由用户提供音频混合得到的 48 kHz 单声道 Ogg Vorbis，以确保多人游戏中的距离衰减；替换资源只改变
听感，不改变 2.5 重发速度或三拍返回时间。
DJ 投射物使用同步的原版发光轮廓作为主要高亮，并省略原版附魔闪烁渲染层，避免同一实体重复绘制
高亮效果；材质仍使用原版三叉戟实体纹理。近战打回时客户端会立即预测位置推进所需的速度和旋转
基线，随后接受服务端实体同步校正；资源包无需提供额外模型或动画。

`djcraft:flowery` 的冲刺 Profile 位于
`assets/djcraft/djcraft/weapon_sounds/flowery.json`，默认以 `2.0` 音量等权选择四个
`ability.flowery_dash_*` 事件，并替代普通 `ability.dash`。物品模型和贴图分别位于
`assets/djcraft/models/item/flowery.json` 与
`assets/djcraft/textures/item/flowery.png`，均可由普通客户端资源包覆盖。

注意：DJCraft 的动态曲目资源包也动态生成 `djcraft:sounds.json`，且作为 required、TOP 位置的客户端资源包启用。该动态 JSON 包含已加载曲目的 `trackpacks.<packId>` 事件。覆盖整个 `djcraft:sounds.json` 时必须实机确认资源合并优先级，不要假设可以删除动态曲目事件。

### 4.2 附属 Mod 自定义声音

附属 Mod 应在自己的 namespace 注册声音：

```text
assets/exampleaddon/sounds/weapons/rifle_fire.ogg
assets/exampleaddon/sounds.json
```

```json
{
  "weapon.rifle_fire": {
    "sounds": [
      { "name": "exampleaddon:weapons/rifle_fire", "stream": false }
    ]
  }
}
```

武器音效 Profile 随后引用事件 `exampleaddon:weapon.rifle_fire`，而不是直接引用 OGG 路径。

## 5. 武器音效 Profile

### 5.1 文件位置与 ID

自定义加载器扫描所有 namespace 下的：

```text
assets/<namespace>/djcraft/weapon_sounds/<path>.json
```

文件资源 ID 即 Profile ID。例如：

```text
assets/exampleaddon/djcraft/weapon_sounds/rifle.json
→ exampleaddon:rifle
```

普通物品默认以物品注册 ID 作为 Profile ID。因此物品 `exampleaddon:rifle` 与 Profile `exampleaddon:rifle` 会自动匹配，不需要 Java 注册器。若一个物品的声音身份取决于数据组件、附件或变体，再使用附属 Mod 接口文档中的 `RegisterDJWeaponSoundResolversEvent`。

不存在对应 Profile 时，系统回退到 `djcraft:generic`。Profile 也可用 `fallback` 显式建立回退链。

### 5.2 完整示例

```json
{
  "fallback": "djcraft:generic",
  "events": {
    "trigger_impact": [
      {
        "when": { "beat": "hit", "target": "hit" },
        "sounds": [
          { "event": "exampleaddon:weapon.rifle_hit", "weight": 3 },
          { "event": "exampleaddon:weapon.rifle_hit_alt", "weight": 1 }
        ],
        "volume": 1.2,
        "pitch": [0.96, 1.04],
        "spatial": true
      },
      {
        "when": { "beat": "miss" },
        "sounds": [
          { "event": "exampleaddon:weapon.rifle_miss" }
        ],
        "volume": 0.8,
        "pitch": 1.0,
        "spatial": true
      }
    ],
    "reload_start": [
      {
        "sounds": [
          { "event": "exampleaddon:weapon.rifle_reload" }
        ],
        "spatial": false
      }
    ]
  }
}
```

### 5.3 支持的语义键

```text
melee_strike       melee_thrust       melee_sweep
melee_critical     trigger_impact     charge_start
charge_release     unequip_start      equip_start
reload_start       inspect_start      use
use_start          use_release        ready
cancel
dash               double_jump        parry
target_hit
```

未知键会使该 Profile 的解析失败。

### 5.4 规则字段

| 字段 | 类型 | 默认值 | 规则 |
|---|---|---|---|
| `when.beat` | string | `any` | `any`、`hit`、`miss`、`not_applicable` |
| `when.target` | string | `any` | `any`、`hit`、`miss`、`unknown`、`not_applicable` |
| `sounds` | array | 必需 | 至少一个声音事件选择 |
| `sounds[].event` | resource ID | 必需 | 已在某个 `sounds.json` 中注册的声音事件 |
| `sounds[].weight` | integer | `1` | 1–100 |
| `volume` | number | `1.0` | 0–4 |
| `pitch` | number 或二元素数组 | `1.0` | 0.5–2；数组表示随机区间且最小值不能大于最大值 |
| `spatial` | boolean | `true` | 是否按世界位置播放 |

同一语义的规则按 JSON 数组顺序匹配，首个满足 `when` 的规则获胜。声音选择按 `weight` 加权，并以运行时种子确定选择和 pitch。回退链禁止循环；任一 Profile 解析失败或检测到循环时，整个武器音效重载保留上一次成功快照。

## 6. 第一人称动画资源

### 6.1 Clip 扫描范围

加载器扫描：

```text
assets/<namespace>/animations/*.json
```

所有 namespace 都会扫描，但加载器只解析名称严格符合
`animation.<资源 namespace>.first_person.<名称>` 的 Clip。普通实体动画以及名称中的作者 namespace
与资源文件 namespace 不一致的 Clip 会被忽略。附属 Mod 可直接在自己的 namespace 中导出 Clip；
资源包通过相同资源路径覆盖低优先级资源。例如：

```text
assets/exampleaddon/animations/rifle.animation.json
animation.exampleaddon.first_person.rifle_fire
```

不同有效文件不得声明相同 Clip 名称。发生冲突时按资源 ID 稳定保留首个 Clip，并记录冲突来源；需要覆盖已有文件时应使用相同资源路径，或在 Profile 中绑定新的唯一 Clip。

内置资源提供：

```text
animation.djcraft.first_person.idle
animation.djcraft.first_person.equip
animation.djcraft.first_person.unequip
animation.djcraft.first_person.melee_strike
animation.djcraft.first_person.melee_thrust
animation.djcraft.first_person.trident_thrust
animation.djcraft.first_person.melee_sweep
animation.djcraft.first_person.melee_critical
animation.djcraft.first_person.use
animation.djcraft.first_person.crossbow_fire
animation.djcraft.first_person.parry
```

`trident_thrust` 由内置三叉戟 Profile 绑定，不再由 Java 特判。`crossbow_fire` 由内置弩 Profile 绑定到 `trigger_impact`，固定持续 1 节拍；它只改变第一人称视觉后座，不改变弩的冷却、判定或发射逻辑。

### 6.2 JSON 结构

运行时消费 GeckoLib 风格导出的 `animations` 对象，读取两个可选的空间骨骼：

- `first_person_hand`：在每只手独立的 `ItemInHandRenderer` 栈帧中，原版基础持物定位之后、`renderItem` 和物品模型 display 变换之前应用。用于挥砍、突刺、后坐、使用、装备和待机等主运动，不会被特殊模型的第一人称旋转或缩放扭曲。
- `first_person_item`：在模型 `firstperson_*` display 变换之后、原版模型居中偏移之前应用。用于确实需要绕物品中心执行的局部自转或机械部件式运动。

内置动画已全部迁移到 `first_person_hand`。为兼容旧资源包，只有
`first_person_item` 的旧 Clip 仍按原来的模型局部/物品中心空间播放。
内置关键帧不是简单改名：它们以 Minecraft 1.21.1 通用物品第一人称 display
（rotation `[0,-90,25]`、translation `[1.13,3.2,1.13]`、scale `0.68`）为参考，
通过 `D × A旧 × D⁻¹` 换算到手部空间，并按各 Blockbench 动画原 snapping 加密采样。
因此通用 `item/generated`/`item/handheld` 模型应尽量复现迁移前轨迹，同时特殊模型不再
把主动画的方向和幅度变换第二次。换算工具位于
`scripts/animation/convert_legacy_item_space_to_hand_space.ps1`，已迁移文件带有
`djcraft_space: hand_reference_generated_1_21_1` 标记，工具会拒绝重复转换。

`trident_thrust` 保留原有的拆分语义，但不再由 Java 对三叉戟硬编码特判：
`first_person_hand` 只包含相机/持物空间的负 Z 突刺位移，
`first_person_item` 只包含绕物品中心的 Z 旋转。两个轨道共享相同时间键并由统一
双空间运行时同时采样。转换工具会识别该 Clip，跳过通用 display 共轭换算并生成上述双轨。

```json
{
  "format_version": "1.8.0",
  "animations": {
    "animation.djcraft.first_person.melee_strike": {
      "animation_length": 0.32,
      "bones": {
        "first_person_hand": {
          "position": {
            "0.0": { "vector": [3.8, -1.8, 2.0] },
            "0.32": { "vector": [0.0, 0.0, 0.0] }
          },
          "rotation": {
            "0.0": { "vector": [0.0, 8.0, 62.0] },
            "0.32": { "vector": [0.0, 0.0, 0.0] }
          }
        }
      }
    }
  }
}
```

约束：

- `animation_length` 必须为有限正数；
- 每个空间骨骼的 position 与 rotation 必须拥有完全相同的时间键；
- Clip 至少声明 `first_person_hand` 或 `first_person_item` 之一；两者同时存在时必须共享完全相同的时间键；
- 至少两个关键帧；
- 首帧必须为 `0.0`，末帧必须恰好等于 `animation_length`；
- 每个 `vector` 必须恰有三个有限数值；
- 运行时只做分段线性插值，导出文件中的其他插值、骨骼、scale、loop 等字段不会被读取；
- 多文件不得声明重复 Clip ID。

### 6.3 坐标换算

加载器执行如下转换：

```text
translationX = -positionX / 16 blocks
translationY =  positionY / 16 blocks
translationZ =  positionZ / 16 blocks
rotationX    = -rotationX degrees
rotationY    = -rotationY degrees
rotationZ    =  rotationZ degrees
scale        = 1.0（JSON scale 当前不读取）
```

两个空间使用相同的像素到方块、角度符号和副手镜像换算。采样时
IDLE、ACTION、IMPULSE 与 TRANSITION 会分别在两个空间内合成，之后手部空间只写入
`ItemInHandRenderer` 的当前手栈帧，物品中心空间只写入当前物品模型栈帧。主手事件不会污染副手，模型 display 缩放也不会改变手部空间位移幅度。

作者源应使用与运行时 JSON 相同的骨骼名；修改空间归属时必须同步更新 `.bbmodel`
与导出 JSON。将旧关键帧迁移到 `first_person_hand` 时不能只改骨骼名，应使用上述参考
display 换算或重新制作轨迹。转换结果仍必须在游戏内校准，不能仅凭 Blockbench 预览或
编译结果判断视觉正确。

动画进度由 DJ 会话虚拟节拍时钟驱动；JSON 的 `animation_length` 只用于把关键帧归一化为 `0..1`。

解析某个动画文档失败时会记录错误并继续资源重载；缺少曲线时使用 Java 安全后备姿势/无曲线行为。修改第一人称动画后仍需进游戏验证主手、用副手攻击/使用、换物、F 键交换、低 FPS 和 F3+T。

### 6.4 动画 Profile

Profile 路径为：

```text
assets/<namespace>/djcraft/animation_profiles/<path>.json
```

文件对应 Profile ID `<namespace>:<path>`。示例：

```json
{
  "priority": 100,
  "selectors": {
    "items": ["minecraft:diamond_sword"],
    "tags": ["minecraft:swords"],
    "behaviors": ["djcraft:bow"]
  },
  "animations": {
    "idle": {
      "clip": "animation.exampleaddon.first_person.sword_idle",
      "duration_beats": 4.0
    },
    "melee_strike": {
      "clip": "animation.exampleaddon.first_person.sword_strike",
      "duration_beats": 0.75
    }
  }
}
```

字段规则：

- `priority` 可省略，默认 `0`，范围 `0..1000`；
- `selectors.items`、`selectors.tags` 和 `selectors.behaviors` 都是资源 ID 数组，至少一个非空；
- `selectors.behaviors` 支持 `djcraft:bow`、`djcraft:crossbow`、`djcraft:shield`、
  `djcraft:charge`、`djcraft:trigger`、`djcraft:trident`、`djcraft:mace` 和 `djcraft:none`，
  也支持附属 Mod 通过 `RegisterDJItemBehaviorsEvent` 在 common setup 注册的行为 ID；选择时使用
  服务端同步的物品行为映射及 Java 继承回退，未注册行为 ID 会拒绝整个动画 Profile；
- 只有 `djcraft:generic` 可省略 `selectors`，作为所有物品的通用回退；
- `animations` 的内置键支持 `idle`、`melee_strike`、`melee_thrust`、`melee_sweep`、`melee_critical`、`trigger_impact`、`charge_start`、`charge_release`、`unequip_start`、`equip_start`、`reload_start`、`inspect_start`、`use`、`use_start`、`use_release`、`parry`、`ready`、`cancel`；
- 附属 Mod 可在客户端初始化时注册额外语义；其 JSON 键必须使用完整命名空间 ID，例如 `exampleaddon:spin`。未注册的命名空间键仍会拒绝整个 Profile；
- `clip` 必须引用本次重载成功解析的 Clip；
- `duration_beats` 可省略；声明时必须为有限正数，并强制覆盖事件传入的动画拍数；
- `idle` 必须声明 `duration_beats`，并按该拍数循环；
- 未知字段和未注册语义会拒绝整个 Profile；缺失 Clip 只丢弃对应语义绑定。语义注册表在客户端 setup 后冻结，`F3+T` 只重载绑定与 Clip，不会新增或移除语义。

每个语义独立选择：精确物品 → 命中标签 → 物品行为 → `djcraft:generic` → Java 安全回退。精确物品始终高于标签和行为；同层候选按 `priority` 降序、Profile ID 字典序选择。同优先级多重命中会记录一次警告。内置弩后坐 Profile 按 `djcraft:crossbow` 行为选择，因此兼容的第三方弩子类会自动获得相同视觉，仍可由精确物品或标签 Profile 覆盖。

未声明 `duration_beats` 时，动作先使用事件提供的拍数，再使用 Java 语义默认值。内置 `use` 是普通物品单次使用语义，默认持续 1 节拍；盾牌、弓、弩、三叉戟和风弹继续使用各自已有的专用动画语义。Profile 拍数只改变视觉曲线采样，不会修改攻击、冷却、READY、服务端状态或换物 warmup。副手继续镜像同一绑定。

Profile 和 Clip 在一次客户端资源重载中校验并发布为不可变快照。事件进入 ACTION、IMPULSE 或 TRANSITION 通道时会固定所选绑定；成功 F3+T 后清除旧视觉通道并从当前物品的新 `idle` 开始。标签来自客户端当前已同步的 item tags；新增标签需要配套数据包。

## 7. 动态曲目资源与普通资源包的边界

已加载曲目会由 `djcraft_tracks` 动态资源包提供：

```text
assets/djcraft/sounds/trackpacks/<packId>.ogg
assets/djcraft/textures/item/disc_<packId>.png
assets/djcraft/textures/item/perfect_disc_<packId>.png
assets/djcraft/models/item/disc_<packId>.json
assets/djcraft/models/item/perfect_disc_<packId>.json
assets/djcraft/textures/gui/combo/trackpacks/<packKey>/0.png ... 9.png
assets/djcraft/textures/gui/combo/trackpacks/<packKey>/<threshold>/0.png ... 9.png
assets/djcraft/textures/gui/beats/trackpacks/<packKey>/<relative-beat-path>.png
assets/djcraft/textures/gui/beats/trackpacks/<packKey>/<relative-beat-path>.gif
assets/djcraft/models/item/empty_disc.json
assets/djcraft/sounds.json
```

`packKey` 是由曲目包 ID 稳定派生的 UUID。该资源包是 required、TOP、不可由用户关闭。普通资源包适合修改固定后备资源；逐曲目的 OGG、beat、唱片和数字应放进曲目包，不应手工伪造上述动态路径。

动态曲目声音以 `stream: false` 注册，完整缓冲就绪后通过 NeoForge 声音事件把确切
`DJSoundInstance` 绑定到其 OpenAL channel。切歌或停止后才完成加载的旧实例会被立即停止；
绑定时的 seek 同时补偿服务器发送位置、半 RTT 和本地缓冲耗时。资源包不应依赖或替换该
内部 source 生命周期。

曲目包只有在 ID 满足小写资源路径字符规则、源音频文件存在且通过 Ogg 页连续性与 Vorbis 三个头/packet/EOS 结构预检后才会进入该动态资源包。该预检不会执行采样解码；合法容器中的损坏编码数据仍需实际播放冒烟测试发现。

曲目包的旧 `combo/<digit>.png` 格式从 1 连击生效；新 `combo/<threshold>/<digit>.png` 格式支持 `2..2147483647` 的多个阈值。阶段按当前连击数向下选择，缺失或无效数字继承上一阶段。曲目包成功加载任意自定义数字后，不再使用内置 `combo/50/` 阶段。

曲包 `beats/**/*.png|gif` 会映射到上面的稳定路径。Falling 渲染器在客户端资源
重载的准备阶段解码完整 RGBA/GIF 帧，应用阶段注册动态纹理；不会在 render/tick
路径读取文件。普通命名空间资源也可被 `BeatDefinition.texture` 直接引用。解码
限制为 1024×1024、256 GIF 帧、单资源 16,777,216 解码像素、一次快照
67,108,864 解码像素和 32 MiB 编码输入；单个坏资源只回退内置
`textures/gui/beats/blue_beat.png`。

## 8. 当前未完成或受限能力

1. **动画 scale/复杂插值未完成。** 运行时只读 position、rotation，并线性插值。
2. **按手、数据组件和判定结果选择动画未完成。** 当前选择器只支持精确物品 ID、item tag
   和同步后的内置行为，副手使用镜像。
3. **UI 布局数据化未完成。** ModernUI 布局、尺寸和颜色主要仍在 Java 中，资源包只能换文本与部分图片。
4. **曲目包动态资源优先级不可配置。** required TOP 资源包会接管动态 sounds、唱片模型和曲目贴图路径。
5. **资源格式没有 JSON Schema。** 动画和武器音效错误通过日志报告，当前仓库不提供独立校验工具。

## 9. 验证清单

- 启用资源包后执行 `F3+T`，确认日志没有 `Rejected weapon sound profile` 或动画解析错误。
- 检查所有自定义声音事件在 `sounds.json` 中存在，避免运行时只打印一次缺失警告。
- 验证武器 Profile 的首个匹配规则和 fallback 链。
- 验证连击数字非全透明，主色与预期发光颜色一致。
- 验证 Falling PNG 保留原色/透明度，GIF 帧延迟、循环和局部帧处置正确，并在 F3+T 后刷新。
- 检查 `FALLING`/`LEGACY` 客户端切换、判定线高度滑杆和不同 GUI scale。
- 修改动画后执行完整双手和换物烟雾测试。
- 修改 shader 后检查编译日志、窗口缩放、不同 GUI scale 和低端显卡后备表现。
- 涉及新增曲目文件时，同时执行曲目包重载，不能只依赖 F3+T 发现新增资源。

## 10. 电子血宫客户端资源

电子血宫的预设名称和说明由服务端数据包同步为文本组件，不要求客户端安装同一数据包。
ModernUI 页面、HUD 和退出确认框使用 `gui.djcraft.cyber_grind.*`、
`message.djcraft.cyber_grind.*` 语言键，可由普通资源包覆盖；布局尺寸和颜色目前仍在 Java
中，不提供独立 JSON 布局格式。

生成预警当前在世界半透明阶段使用原版 `minecraft:electric_spark` 粒子组成脉冲地面圆环，
没有新增或可替换的 DJCraft 光圈纹理资源。资源包若替换原版粒子图集会同时影响该光圈和
原版同类粒子。`djcraft:cyber_grind` 的天空/光照效果来自数据包中的维度类型，不属于
客户端资源包重载契约。应在游戏内检查不同画质、粒子设置、GUI scale 和多人延迟下的光圈
可见性；编译和资源 JSON 校验无法证明半透明世界渲染效果正确。

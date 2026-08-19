# DJCraft 曲目包功能文档

> 代码基线：DJCraft 2.1.1、Minecraft 1.21.1、NeoForge 21.1.218；文档核对日期：2026-08-02。
> 状态标记：**已完成**表示当前代码存在可达实现；**部分完成**表示主流程存在但有限制或未贯通；**未完成**表示数据结构或入口已经预留，但没有实际消费逻辑。

相关文档：[文档索引](README.md) · [游戏机制与实现](gameplay-mechanics.md) ·
[资源包](resource-pack.md) · [数据包](data-pack.md) · [附属 Mod 接口](addon-api.md)

## 1. 功能概述

曲目包（TrackPack）是 DJCraft 的内容与节奏规则载体。一个曲目包同时描述：

- 音频文件及曲目元信息；
- 战斗节拍时间线和每类节拍的判定参数；
- Legacy 准星预览方式、下落式谱面视觉和播放音量；
- 可选 beat、唱片、完美唱片和连击数字贴图；
- 客户端与服务端用于一致性校验的完整内容哈希。

曲目包被加载后，会参与动态声音资源注册、DJ 唱片刻录、便携点唱机播放、DJ 会话时钟、
战斗判定、准星渲染、节拍类别伤害规则、唱片统计和服务端向客户端下载等流程。上述玩法
流程和服务端/客户端实现归 [游戏机制与实现](gameplay-mechanics.md) 统一说明；本文只定义
曲包内容契约及其进入这些系统时的边界。

## 2. 支持的封装形式

外置曲目包存放目录为：

```text
<游戏目录>/djcraft/trackpacks/
```

模组自身还会从 JAR 内的只读目录加载自带曲包：

```text
<DJCraft JAR>/djcraft/trackpacks/<packId>/
```

源码作者把这类内容放在 `src/main/resources/djcraft/trackpacks/<packId>/`。内置来源只接受
直接子目录形式，不接受内嵌 `.djcraft`；目录内部的 `track.json`、音频和可选资源契约与
外置目录曲包完全相同。

### 2.1 目录曲目包

```text
djcraft/trackpacks/example_track/
├─ track.json                 # 必需
├─ track.ogg                  # 必需；实际名称由 meta.sound_file 指定
├─ disc.png                   # 可选，普通唱片贴图
├─ disc.jpg                   # 可选，仅播放器 UI 可作为普通封面后备
├─ perfect_disc.png           # 可选，镀金/完美唱片贴图
└─ combo/
   ├─ 0.png ... 9.png         # 可选，从 1 连击生效的兼容格式
   ├─ 20/0.png ... 20/9.png   # 可选，从 20 连击生效
   └─ 50/0.png ... 50/9.png   # 可选，可配置多个阈值
```

目录名经小写转换后成为 `packId`。

### 2.2 `.djcraft` 压缩曲目包

`.djcraft` 实际为 ZIP 文件，文件名去掉扩展名并转为小写后成为 `packId`：

```text
djcraft/trackpacks/example_track.djcraft
└─ ZIP 根目录
   ├─ track.json
   ├─ track.ogg
   ├─ disc.png
   ├─ perfect_disc.png
   ├─ combo/0.png ... combo/9.png
   └─ combo/<threshold>/0.png ... 9.png
```

`track.json` 必须位于压缩包根目录，不能再套一层文件夹。服务端下载功能只分发外置
`.djcraft` 文件，外置目录包和模组内置目录包均不能被客户端下载。

### 2.3 ID 规则

目录名或归档文件名先按既有行为转为小写，再由校验器只接受小写字母、数字、点、下划线和连字符，长度上限 128；这与曲包作为 Minecraft 动态资源路径片段时的字符约束一致。空 ID、`.`、`..`、空格、Unicode 和路径分隔符均被拒绝。

建议作者进一步只使用 Minecraft 资源路径安全字符：

```text
[a-z0-9._-]
```

这是因为曲目 ID 还会用于声音事件、模型和贴图资源路径；非法 ID 会在扫描阶段被拒绝，不会进入动态资源注册。

## 3. `track.json` 格式

### 3.1 完整示例

```json
{
  "meta": {
    "version": "1.0",
    "author": "DJCraft Team",
    "bpm": 128,
    "difficulty": "normal",
    "sound_file": "track.ogg",
    "offset_ms": 0,
    "playback_start_ms": 0,
    "total_duration_ms": 180000,
    "display_name": "Example Track"
  },
  "settings": {
    "crosshair_mode": "time",
    "crosshair_time_ms": 1400,
    "crosshair_beat_count": 4,
    "volume_multiplier": 1.0
  },
  "definitions": {
    "normal_hit": {
      "can_attack": true,
      "color": "#FFFFFF",
      "scale": 1.0,
      "category": "weakbeat",
      "haptic_intensity": 1.0,
      "tolerance": 0.1,
      "particle": null,
      "trigger": null,
      "texture": "beats/normal.gif",
      "landing_x_percent": 50.0,
      "spawn_advance_ms": 1400,
      "hit_behavior": "freeze_dissipate",
      "matched_hit_behavior": "bounce",
      "miss_behavior": "none",
      "rotation_rpm": 0.0
    },
    "strong_hit": {
      "can_attack": true,
      "color": "#FFAA00",
      "scale": 1.2,
      "category": "downbeat",
      "haptic_intensity": 1.0,
      "tolerance": 80.0
    }
  },
  "timeline": {
    "combat_line": [
      { "t": 1000, "type": "normal_hit" },
      { "t": 1469, "type": "strong_hit", "props": { "label": "drop" } }
    ],
    "lighting": [
      { "t": 1000, "type": "flash" }
    ]
  }
}
```

时间轴事件在加载时会按 `t` 从小到大排序。

### 3.2 `meta` 字段

| 字段 | 类型 | `createDefault()` 值 | 当前用途 | 状态 |
|---|---:|---|---|---|
| `version` | string | `"1.0"` | 格式版本预留 | **未完成：已解析但不校验、不参与兼容性判断** |
| `author` | string | `"Unknown"` | 播放器和刻录 UI 显示 | **已完成** |
| `bpm` | integer | `120` | UI、命令和日志显示 | **部分完成：播放与判定直接使用毫秒时间线，BPM 不生成节拍** |
| `difficulty` | string | `"normal"` | 摘要信息 | **未完成：游戏 UI 和规则未使用** |
| `sound_file` | string | `"track.ogg"` | 定位 OGG 音频 | **已完成** |
| `offset_ms` | integer | `0` | 从 DJ 会话原始时间中扣除，整体校准判定时间线 | **已完成** |
| `playback_start_ms` | integer | `0` | 原始音频与服务端会话时钟从该毫秒位置开始；开始位置之前的节拍不会补触发 | **已完成** |
| `total_duration_ms` | integer | `180000` | 原始音频的排他结束位置；到点截断音频、终止双端会话并按播放模式切歌 | **已完成** |
| `display_name` | string/null | 回退为 `packId` | 唱片名称和 UI 标题 | **已完成** |

注意：上表第三列是 `TrackMeta.createDefault()` 的辅助对象取值，不是 JSON 解析器的逐字段默认值。`meta`、有限且大于 0 的数值 `bpm` 和正整数 `total_duration_ms` 是加载必需项；小数 BPM 会按既有 `TrackMeta` 整数字段行为转换，因而不会阻止旧曲包加载。未填写 `playback_start_ms` 时 Gson 的整数零值使其从头播放，负值以及大于或等于 `total_duration_ms` 的值会被拒绝；`sound_file` 另有 `track.ogg` 运行时回退。作者应始终提供完整 `meta`。

两个裁剪字段都使用未应用 `offset_ms` 的原始音频坐标，最终播放区间为
`[playback_start_ms, total_duration_ms)`。例如开始位置为 `30000`、结束位置为
`150000` 时实际最多播放 120 秒；`total_duration_ms` 不是“从开始位置起再播放多少毫秒”。
结束位置之后的时间线事件不会触发。实际 OGG 若更早结束，个人播放按音频结束处理；组网
不会允许该客户端推进歌单，而是按当前播放 ID 重挂两次，失败后隔离到下一曲。

### 3.3 `settings` 字段

`settings` 整体可省略；省略时使用默认值。

| 字段 | 类型 | 默认值 | 当前用途 | 状态 |
|---|---:|---:|---|---|
| `crosshair_mode` | string | `"time"` | Legacy 模式中，`time` 按未来毫秒范围显示；`beat` 按未来节拍数显示 | **已完成** |
| `crosshair_time_ms` | integer | `1400` | Legacy `time` 模式准星预览窗口 | **已完成** |
| `crosshair_beat_count` | integer | `4` | Legacy `beat` 模式未来节拍数量 | **已完成** |
| `volume_multiplier` | number | `1.0` | 播放音量倍数 | **已完成** |

加载阶段只接受 `time` 或 `beat`；`crosshair_time_ms` 必须非负，`crosshair_beat_count` 必须为正数，`volume_multiplier` 必须为有限数值。

谱面呈现方式不是曲包设置。客户端配置 `beatPresentationMode` 默认为 `FALLING`，
玩家可切换为 `LEGACY`；下落式模式不读取上述三个 `crosshair_*` 字段。下落式判定线
高度由客户端整数滑杆 `fallingJudgeLineYPercent` 控制，默认 `66`，范围 `25..85`。
两种模式都保留屏幕中心的点状准星；每次判定都会让四条横竖线向外弹出并在 250ms
内缓动收回，Falling 不会因此启用 Legacy 的节拍预览或旧判定线反馈。

### 3.4 `definitions` 字段

`definitions` 是“节拍类型名 → 节拍定义”的对象。所有时间线事件的 `type` 都必须引用其中的键，否则整个曲目包在加载阶段被拒绝。

| 字段 | 类型 | 默认值 | 当前用途 | 状态 |
|---|---:|---:|---|---|
| `can_attack` | boolean | `true` | 为 `false` 时该节拍不能判定为命中，也不计入未命中连击重置的节拍数 | **已完成** |
| `color` | string | `#FFFFFF` | Legacy 节拍颜色；Falling 命中冲击波颜色 | **已完成** |
| `scale` | number | `1.0` | Falling beat 贴图尺寸倍率，最长边基准为 32 GUI 像素 | **已完成** |
| `category` | string | `"normal"` | 战斗类别：`normal`、`weakbeat` 或 `downbeat` | **已完成** |
| `haptic_intensity` | number | `1.0` | 震动强度预留 | **未完成：没有运行时代码读取** |
| `tolerance` | number | `0.1` | 判定容差 | **已完成** |
| `particle` | string/null | `null` | 粒子资源预留 | **未完成：只提供 `hasParticle()`，没有触发逻辑** |
| `trigger` | string/null | `null` | 条件触发预留 | **未完成：只提供 `hasTrigger()`，没有条件解析/执行逻辑** |
| `texture` | string/null | `"djcraft:textures/gui/beats/blue_beat.png"` | Falling beat 的曲包相对 PNG/GIF 或普通资源位置；缺失时使用内置蓝色标记 | **已完成** |
| `landing_x_percent` | number | `50.0` | beat 中心落点占整个 GUI 宽度的百分比 | **已完成** |
| `spawn_advance_ms` | integer | `1400` | 在 `t` 之前多少毫秒从屏幕顶部生成；替代 Falling 模式预览窗口 | **已完成** |
| `hit_behavior` | string | `"freeze_dissipate"` | 成功判定后的 beat 行为 | **已完成** |
| `matched_hit_behavior` | string | 继承 `hit_behavior` | `weakbeat`/`downbeat` 卡拍成功且动作物品标签正确匹配后的 beat 行为 | **已完成** |
| `miss_behavior` | string | `"none"` | 失败判定后的 beat 行为；失败始终另有 160ms 判定线红闪 | **已完成** |
| `rotation_rpm` | number | `0.0` | 从生成时刻开始的每分钟转数；负数反向 | **已完成** |

`tolerance` 的实际语义：

- `tolerance > 1.0`：直接解释为毫秒数；例如 `80.0` 表示 ±80 ms。
- `tolerance <= 1.0`：解释为相邻节拍间隔的比例；例如相邻间隔 500 ms、容差 `0.1`，判定窗口为 ±50 ms。
- 首尾孤立节拍无法取得相邻间隔时，基准间隔为 500 ms。

Falling 可视字段的约束为：`landing_x_percent` 在 `0..100`，`spawn_advance_ms`
在 `1..60000`，`rotation_rpm` 在 `-10000..10000` 且必须有限。越界值回退对应
默认值。行为支持 `none`、`freeze_dissipate`、`dissipate`、`bounce`：

- `freeze_dissipate`：停留 120ms，再在 180ms 内放大淡出；
- `dissipate`：立即在 220ms 内放大淡出；
- `bounce`：在 320ms 内最多向上反弹约 28 GUI 像素并淡出；
- `none`：不接管 beat，继续按原下落轨迹运动。

`matched_hit_behavior` 只在攻击类判定中生效：`weakbeat` 要求本次动作物品属于
`djcraft:swift`，`downbeat` 要求属于 `djcraft:smash`。只卡拍成功但标签不匹配时仍用
`hit_behavior`；`normal`、盾牌、冲刺和二段跳等非攻击判定也使用普通 `hit_behavior`。
客户端用实际动作 `ItemStack` 立即预测视觉，伤害与最终标签判定仍由服务端负责。

Falling beat 的位置锚点是贴图正中央：生成时中心位于 GUI 顶边，计划时间 `t`
到达时中心与判定线重合。命中后的停滞、消散或反弹从实际判定瞬间的位置开始，
提前或延后命中都不会把 beat 瞬移到完美判定位置。

命中始终从准确落点产生约 320ms 的荧光冲击环；该环由客户端片元 Shader
根据 definition `color` 生成，不需要曲包提供冲击波贴图。失败时若行为为 `none`，beat
不改变轨迹，只显示判定线红闪。纹理按原始 RGBA 渲染，`color` 不会染色自定义图片。

### 3.5 `timeline` 字段

| 字段 | 类型 | 说明 | 状态 |
|---|---|---|---|
| `combat_line` | array | 战斗判定主轨；每项含 `t`、`type`、可选 `props` | **已完成** |
| 其他任意键 | array | 被保存为命名特效轨，如示例中的 `lighting` | **未完成：已解析、可查询和计数，但没有运行时调度/消费器** |

事件字段：

| 字段 | 类型 | 默认值 | 状态 |
|---|---:|---|---|
| `t` | integer | `0` | **已完成**；单位为毫秒 |
| `type` | string | `"normal_hit"` | **已完成**；引用 `definitions` |
| `props` | object | 空对象 | **已完成**；按事件覆盖同名 definition 字段 |

`props` 只保留 number、boolean、string；数组、对象和 null 会被忽略。所有 number
会解析为 `Double`。DJCraft 识别以下核心覆盖键：

| props 键 | 类型 | 覆盖的 definition 字段 |
|---|---:|---|
| `can_attack` | boolean | `can_attack` |
| `color` | string | `color` |
| `scale` | number | `scale` |
| `category` | string | `category`；仅接受 `normal`、`weakbeat`、`downbeat` |
| `haptic_intensity` | number | `haptic_intensity` |
| `tolerance` | number | `tolerance` |
| `particle` | string | `particle`；空字符串等价于不启用 |
| `trigger` | string | `trigger`；空字符串等价于不启用 |
| `texture` | string | `texture` |
| `landing_x_percent` | number | `landing_x_percent` |
| `spawn_advance_ms` | integer-valued number | `spawn_advance_ms` |
| `hit_behavior` | string | `hit_behavior` |
| `matched_hit_behavior` | string | `matched_hit_behavior` |
| `miss_behavior` | string | `miss_behavior` |
| `rotation_rpm` | number | `rotation_rpm` |

缺失键沿用事件 `type` 对应 definition 的值。类型错误、非有限 number 和未知
`category` 会忽略并回退到 definition；未知键仍原样保留在 `BeatEvent.props()` 中，
供附属 Mod 消费。核心覆盖由 `TrackPack.resolveDefinition(BeatEvent)` 统一解析。

## 4. 可选资源

### 4.1 音频

- 音频通过动态生成的 `djcraft:sounds.json` 注册为 `djcraft:trackpacks.<packId>`。
- 资源层实际暴露路径为 `assets/djcraft/sounds/trackpacks/<packId>.ogg`。
- `sound_file` 可以改变包内源文件名，但对外动态声音事件仍按 `packId` 稳定命名。
- 加载时要求音频文件真实存在，并流式预检 Ogg 页连续性、Vorbis identification/comment/setup 三个头、版本、声道数、正采样率、至少一个音频 packet 和正常 EOS；不符合 Minecraft 流式解码输入结构的包不会注册。

### 4.2 唱片贴图

- `disc.png`：普通唱片物品模型和播放器封面。
- `disc.jpg`：只作为播放器普通封面后备；不会生成物品模型资源。
- `perfect_disc.png`：镀金唱片物品模型和播放器封面。
- 未提供自定义贴图时使用 DJCraft 内置普通/镀金唱片贴图。

动态物品模型按已加载曲目包 ID 排序后分配 `djcraft:pack_index`。唱片达到镀金条件后，`djcraft:gilded` 属性切换为完美唱片模型。

镀金条件为：

```text
最大连击 >= ceil(战斗节拍总数 × 80%)
```

且战斗节拍总数必须大于 0。

### 4.3 连击数字贴图

曲目包可以提供多阶段连击数字贴图：

- `combo/0.png` 至 `combo/9.png` 是兼容格式，从 1 连击开始生效；
- `combo/<threshold>/0.png` 至 `9.png` 从指定连击数开始生效，并允许配置多个阈值；
- 阈值目录必须是无前导零的十进制整数，范围为 `2..2147483647`；`combo/1/` 不支持，因为根目录兼容格式已经表示该阶段。

渲染时选择不超过当前连击数的最大阈值。每个阶段允许只提供部分数字：缺失、无法解码或完全透明的图片继承上一阶段的同一数字，根阶段则回退内置基础数字。有效图片的动态资源 ID 为：

```text
djcraft:textures/gui/combo/trackpacks/<packKey>/<digit>.png
djcraft:textures/gui/combo/trackpacks/<packKey>/<threshold>/<digit>.png
```

`packKey` 是由 `packId` 稳定派生的 UUID，用于保证 Minecraft 资源路径合法。没有成功加载任何自定义数字贴图的曲目使用内置阶段：1–49 连击使用基础数字，`>=50` 使用高连击数字。只要任意一张曲目包数字贴图成功加载，该曲目就完全禁用内置 `>=50` 阶段，由曲目包阶段和逐数字继承规则决定最终贴图。非法阈值会记录警告但不会阻止曲目包加载。

### 4.4 Falling beat PNG/GIF

曲包可在根目录增加小写资源路径：

```text
beats/normal.png
beats/accent.gif
beats/boss/drop.gif
```

definition 或事件 `props.texture` 使用同一相对路径。相对资源由动态曲目资源包暴露为：

```text
djcraft:textures/gui/beats/trackpacks/<packKey>/<path-after-beats/>
```

也可直接填写普通 Minecraft 资源位置，例如
`exampleaddon:textures/gui/beats/plasma.gif`，由正常客户端资源包提供。PNG 和 GIF
都会完整保留颜色与透明度；GIF 支持帧延迟、局部帧处置和有限/无限循环，每个 beat
从自己的生成时刻开始播放。

资源重载阶段执行解码，渲染帧只选择预解码帧。限制为：单边最多 1024 像素、GIF
最多 256 帧、单资源最多 16,777,216 解码像素、一次快照最多 67,108,864 解码
像素、单文件最多读取 32 MiB。路径非法、文件缺失、损坏或超限时记录警告并只让该
引用回退内置蓝色 beat，不阻止曲包和其他有效图片加载。内置可选贴图为
`djcraft:textures/gui/beats/blue_beat.png`、`green_beat.png` 和 `white_beat.png`。
F3+T、曲包 UI 重载、服务端
`/dj reload` 通知和下载完成后的资源刷新都会重建该快照。

## 5. 加载、重载与优先级

### 5.1 启动加载

模组初始化时挂载 JAR 内置曲包目录、创建外置曲包目录并扫描内容。扫描顺序为：

1. 路径排序后的全部模组内置目录包；
2. 文件名排序后的全部外置 `.djcraft`；
3. 路径排序后的全部外置目录包。

同一 `packId` 冲突时，后加载来源覆盖先加载来源，因此外置内容可覆盖内置默认曲包，
外置目录包又会覆盖同名外置 `.djcraft`，并记录警告。删除外置覆盖后，单包重载和全量重载
都会重新回退到内置版本。

### 5.2 哈希

系统维护三种 SHA-256：

- 定义哈希：只计算原始 `track.json`，保留给内部查询和兼容用途；
- 完整内容哈希：按相对路径排序后纳入每个文件的路径、长度和内容，用于服务端/客户端 verified 校验；
- 归档哈希：计算整个 `.djcraft` 文件，用于客户端下载完成后的完整性校验。

### 5.3 重载入口

- `/dj reload`：服务端重载，要求权限等级 2；随后向在线玩家重新同步哈希。
- DJ 刻录 UI 的“重载曲包”：客户端本地重扫并刷新 Minecraft 资源包。集成单人服务端与客户端共享重载后的完整内容哈希快照，因此编辑后的合法曲包会立即重新 verified，无需退出世界；远程多人仍只接受服务端下发的权威哈希，本地修改不能覆盖该快照。
- 下载完成：客户端校验、安装单包、重新计算验证集合并刷新资源包。
- `ReloadTracksPayload`：客户端网络处理器可触发本地全量重载。

资源刷新是异步的；解析后的曲目注册表先更新，动态声音/模型/贴图在资源刷新成功后可见。

## 6. 多人同步与下载

### 6.1 双端完整内容校验

玩家登录服务端或服务端 `/dj reload` 后，服务端下发全部 `packId → 完整内容 SHA-256`。客户端与本地完整内容哈希取交集，生成 `verifiedPackIds`，并把实际匹配结果回报服务端。

结果含义：

- ID 和哈希均一致：verified；
- 服务端有、本地没有：缺失；
- ID 相同但哈希不同：版本不一致；
- 仅客户端本地存在：不会进入服务端列表。

### 6.2 下载 GUI 与命令

播放器和刻录界面都可打开“下载”页。页面列出服务端公布的曲包及 verified/缺失/不匹配状态，传输时显示百分比、已下载量、总量和平均速度，并支持暂停、恢复与重新下载。暂停状态会同步到服务端，暂停期间不触发传输超时。

```text
/djclient download <trackpack>
```

该客户端命令只允许请求服务端已公布且尚未 verified 的曲目。服务端必须持有对应 `.djcraft` 源文件；如果服务端加载的是目录包，返回 `NOT_FOUND`。

### 6.3 传输协议

下载流程为：

1. 客户端发送下载请求；
2. 服务端检查 ID、并发状态、归档存在性和大小；
3. 服务端发送 transfer ID、总大小、归档 SHA-256；
4. 服务端按最大 256 KiB 的块发送，每个窗口最多 8 块；
5. 客户端按连续 offset 写入临时 `.part` 文件，并对窗口 ACK；
6. 下载完毕后客户端校验归档 SHA-256；
7. 临时文件原子移动为 `<packId>.djcraft`（不支持原子移动时普通替换）；
8. 客户端执行安全校验、单包加载、资源刷新，最后 ACK 完成。

默认压缩与解压总量上限为 256 MiB，可由 `maxTrackPackDownloadMiB` 配置，允许范围 1–2048 MiB。失败原因包括：不存在、过大、忙、IO 错误、哈希不一致、超时、重载失败和无效请求。

## 7. 唱片集成边界

曲包为刻录流程提供稳定的 `packId`、显示名、作者、内容哈希和可选唱片贴图。唱片本身的
`track_pack_id`、`disc_id` 与统计组件不属于 `track.json`，也不会写回曲包。

刻录校验、54 槽便携点唱机、物理唱片引用和玩家操作已迁移到
[游戏机制与实现：唱片刻录与便携点唱机](gameplay-mechanics.md#disc)。数据组件格式及
数据包生成限制见 [数据包：带数据组件的唱片](data-pack.md#9-带数据组件的唱片)。

## 8. 播放与会话字段边界

曲包直接影响会话的字段只有：

- `playback_start_ms`：原始音频起点；
- `total_duration_ms`：原始音频排他终点；
- `offset_ms`：从原始播放位置换算时间线时间；
- `combat_line`：判定和虚拟拍网格；
- `volume_multiplier`：客户端曲目播放音量。

播放列表、个人/组网会话、资源保留、停止原因和管理员命令不是曲包 Schema。完整生命周期
和时间公式见 [游戏机制与实现：播放列表与 DJ 会话](gameplay-mechanics.md#session) 与
[时间模型](gameplay-mechanics.md#timing)。

## 9. 判定与战斗字段边界

`combat_line` 事件通过 `TrackPack.resolveDefinition(BeatEvent)` 应用合法 `props`，
然后由双端共享判定器读取 `can_attack` 和 `tolerance`。`category` 只输出
`normal`、`weakbeat` 或 `downbeat`；最终伤害由服务端结合当前物品的
`djcraft:swift` / `djcraft:smash` 标签决定。

旧 `damage_rate` 已移除并会被加载器忽略。能量、连击、容错、物品冷却、武器范围、
盾牌和移动能力都不属于曲包 Schema；完整规则见
[游戏机制与实现：连击、容错与能量](gameplay-mechanics.md#resources)、
[通用战斗](gameplay-mechanics.md#combat-common) 和
[武器专用机制](gameplay-mechanics.md#weapons)。

## 10. 唱片统计与镀金资源

曲包只提供战斗节拍总数以及可选 `perfect_disc.png`。实体唱片按 UUID 保存统计，达到
`ceil(combat_line 事件数 × 80%)` 的历史最大连击后选择完美唱片资源；没有战斗节拍时
不能镀金。统计起点、有效时长、UUID 重定位和 pending 写回见
[游戏机制与实现：唱片统计与镀金](gameplay-mechanics.md#statistics)。

## 11. 安全与校验

`.djcraft` 安全校验包括：

- 压缩文件必须为普通文件且不超过配置上限；
- 解压后累计读取字节也不能超过同一上限，防止 ZIP bomb；
- 最多 4096 个 ZIP entry；
- `track.json` 最大 1 MiB；
- 必须含根目录 `track.json`；
- 拒绝绝对路径、`..` 越界、反斜杠和冒号路径；
- 音频及任意可选资源读取也执行相对路径约束；
- 下载后校验整个归档 SHA-256，再进入加载流程。

加载器还会在注册前拒绝：缺失 `meta`、非正 BPM/总时长、负播放起点、播放起点不早于裁剪结束位置、负事件时间、非法 `#RRGGBB` 颜色、未知准星模式、无效准星范围、非有限浮点数、缺失 definition 引用、缺失音频及结构不完整的 Ogg Vorbis 流。该检查不是完整 JSON Schema，也不会执行 Vorbis 采样解码。

## 12. 未完成与已知限制

以下项目均以当前代码可达性为依据，不代表最终产品承诺。

### 12.1 明确未完成的曲目格式能力

1. **`meta.version` 版本协商未完成。** 字段已定义并解析，但没有支持版本列表、迁移器或拒绝未知版本的逻辑。
2. **`meta.difficulty` 玩法/UI 集成未完成。** 除调试摘要外没有消费者。
3. **definition 的 `haptic_intensity` 未完成。** 没有手柄震动/触觉反馈实现。
4. **definition 的 `particle` 未完成。** 没有粒子注册、解析或命中触发器。
5. **definition 的 `trigger` 未完成。** 没有条件语言或运行时条件判断。
6. **特效轨调度未完成。** 非 `combat_line` 轨道会被加载到 `effectLines`，但没有按会话时钟派发灯光、粒子、镜头或其他效果。

### 12.2 部分完成或尚未贯通的流程

1. **服务端只分发外置压缩包。** 外置或内置目录包都能加载、播放和参与同 ID 覆盖，但不能通过下载协议提供给客户端；多人双方需要安装包含同一内置内容的模组版本才能通过完整内容哈希校验。
2. **音频预检不执行采样解码。** 加载阶段流式验证 Ogg 页连续性和 Vorbis 三个头/packet/EOS 结构，但合法容器中的损坏编码数据仍可能在实际解码时失败。
3. **`disc.jpg` 支持不一致。** ModernUI 播放器支持它作为普通封面后备，动态物品模型与资源枚举只支持 PNG。
4. **缺少曲目包作者工具链。** 仓库没有示例曲目包、JSON Schema、打包器、格式检查 CLI 或时间线编辑器；作者需手工制作并通过运行日志排错。

### 12.3 DJ 组网

组网会使用本节 5.2 的完整内容哈希决定成员能否准备，并只自动下载可分发的
`.djcraft` 归档。除此之外，歌单快照、邀请、房主转交、共享时钟、晚加入、死亡重生、
独立成员资源和统计归属均属于游戏机制，已迁移到
[游戏机制与实现：DJ 组网](gameplay-mechanics.md#group)。

## 13. 作者发布检查清单

- 使用仅含小写字母、数字、点、下划线和连字符的 `packId`。
- 将 `track.json` 和 OGG 放在目录或 ZIP 根目录。
- 明确填写全部 `meta` 字段，不依赖缺失字段回退。
- 使用毫秒编排 `combat_line`，并确保事件 `type` 能在 `definitions` 中找到。
- 为每种可攻击节拍设置合理的 `tolerance` 和 `category`。
- 可用 `props` 为单个事件覆盖 definition；`can_attack`、`color`、`scale`、
  `category`、`tolerance` 及 Falling 视觉字段都有现有运行时消费者。不要依赖
  `haptic_intensity`、`particle`、`trigger` 或非战斗轨产生尚未实现的实际效果。
- 如需服务器下载，发布 `.djcraft`，不要只安装目录包。
- 控制压缩及解压总量低于服务器的 `maxTrackPackDownloadMiB`。
- 可选贴图优先使用 PNG；建议与 Minecraft 物品/GUI 贴图保持合适尺寸和透明通道。
- 安装后执行 `/dj reload`（服务端）或刻录 UI 的“重载曲包”（客户端），检查日志中的加载、哈希与资源刷新错误。
- 在多人环境分别验证：哈希同步、下载、刻录、点唱机存取、播放、停止/切歌、战斗判定、唱片统计与镀金贴图。

## 14. 曲包格式实现索引

- 数据模型：`data/TrackPack.java`、`TrackMeta.java`、`TrackSettings.java`、`BeatDefinition.java`、`BeatEvent.java`、`Timeline.java`
- JSON 解析：`loader/TrackPackLoader.java`
- 扫描、哈希、文件流：`loader/TrackPackManager.java`
- 归档安全：`loader/TrackPackArchiveValidator.java`、`TrackPackIdValidator.java`
- 动态声音/模型/贴图：`sound/TrackPackResources.java`、`TrackPackRepositorySource.java`
- 双端哈希：`client/ClientTrackRegistry.java`、`network/packet/SyncTrackHashesPayload.java`
- 下载：`network/server/TrackPackTransferService.java`、`client/ClientTrackPackTransferService.java`

刻录、点唱机、会话、判定、组网和唱片统计的实现入口见
[游戏机制与实现：主要实现索引](gameplay-mechanics.md#implementation-index)。

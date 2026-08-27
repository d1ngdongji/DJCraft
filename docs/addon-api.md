# DJCraft 附属 Mod 开发接口文档

> 适用版本：DJCraft 2.2.0、Minecraft 1.21.1、NeoForge 21.1.218、Java 21。代码核对日期：2026-08-07。
> DJCraft 当前没有独立 `api` 源集、API artifact 或 `@ApiStatus` 稳定性标记。本文将接口按实际设计意图分级，不能把“public”自动理解为长期二进制兼容承诺。
> 玩家规则、状态生命周期和服务端结算见 [游戏机制与实现](gameplay-mechanics.md)；
> 本文只定义附属 Mod 可调用的接口与边界。

## 1. 接口稳定性分级

| 级别 | 当前接口 | 使用建议 |
|---|---|---|
| 正式扩展点 | `OnBeatEvent`、`RegisterDJItemBehaviorsEvent`、`DJItemBehaviorRegistry`、`DJItemBehaviorDefinition`、`DJTriggerBehaviorExecutor`、`RegisterDJWeaponSoundResolversEvent`、`DJWeaponSoundIdentityResolver`、`RegisterDJAnimationSemanticsEvent`、`DJAnimationSemantic` | 附属 Mod 优先使用 |
| 推荐只读接口 | 曲目数据 records、`TrackPackManager` 查询、`DJModeManager`/客户端活动会话查询、`BeatJudgmentEvaluator` | 可用，但升级 DJCraft 时重新编译和回归测试 |
| 条件性接口 | 数据组件、`djcraft:max_energy` 属性、`HotbarOrOffhandEffectItem`、物品时序/行为 Profile、物品标签、声音与动画 Profile 资源格式 | 可集成，需遵守服务端权威和资源重载规则 |
| 内部实现 | DJCraft 网络 payload、`DJActionSource`/`DJActionContext`、Mixin、OpenAL helper、客户端动画 runtime、会话状态写方法、下载服务 | 不建议附属 Mod 直接调用或复制协议 |

当前最缺少的高层接口是：安全启动/切换 DJ 会话、注册全新的输入/proof 生命周期族、按数据组件动态选择动画 Profile、数据包注册节拍能力。附属 Mod 不应靠反射修改内部集合来补齐这些能力；新行为 ID 应通过正式注册事件声明，再由数据包选择到物品。

## 2. 构建依赖

### 2.1 版本基线

附属 Mod 至少应使用与 DJCraft 相同的：

```text
Java 21
Minecraft 1.21.1
NeoForge 21.1.218
```

DJCraft 客户端运行时还要求 ModernUI 3.12+。主 Mod 会直接解析所支持的 Blockbench/GeckoLib 风格动画 JSON，不要求安装 GeckoLib；需要使用 GeckoLib 模型或渲染器的附属 Mod 应自行声明其依赖。附属 Mod 若只引用 DJCraft common API，通常不需要直接调用 ModernUI，但测试环境必须能完整启动 DJCraft。

仓库没有声明可供引用的 Maven 发布坐标。开发时可把 DJCraft 构建产物放入附属 Mod 的 `libs/`：

```groovy
dependencies {
    compileOnly files("libs/djcraft-2.1.2.jar")
    localRuntime files("libs/djcraft-2.1.2.jar")
}
```

具体 jar 文件名以实际构建产物为准。不要把 DJCraft jar 重打包进附属 Mod；正式发行时让加载器作为独立依赖解析。

### 2.2 `neoforge.mods.toml`

```toml
[[dependencies.exampleaddon]]
modId="djcraft"
type="required"
versionRange="[2.1.2,3)"
ordering="AFTER"
side="BOTH"
```

如果附属 Mod 的全部集成都能在 DJCraft 缺失时安全跳过，可以改为可选依赖，但任何直接类引用都必须隔离，避免类加载阶段 `NoClassDefFoundError`。

## 3. 侧与线程规则

DJCraft 严格区分 common/server 与 client：

- `otto.djgun.djcraft.client.*`、`DJModeManagerClient`、`DJSessionClient`、HUD、ModernUI 和 OpenAL 类只能在客户端加载；
- `DJModeManager`、`DJSession` 是服务端权威会话状态；
- `TrackPackManager` 是双端本地注册表，两端实例加载各自文件，不能把客户端查询结果当作服务端事实；
- `OnBeatEvent` 会在服务端和客户端各自发布；集成服务器中同一逻辑可能观察到两侧事件；
- 服务端状态修改必须在服务端线程执行；客户端渲染/UI 操作必须调度到客户端主线程；
- 不要从 common 类 import `net.minecraft.client.Minecraft` 或 DJCraft client 包。

客户端订阅器示例：

```java
@EventBusSubscriber(
        modid = ExampleAddon.MOD_ID,
        value = Dist.CLIENT)
public final class ExampleAddonClientEvents {
    @SubscribeEvent
    public static void onBeat(OnBeatEvent event) {
        // 此类只会在物理客户端加载。
    }
}
```

## 4. 节拍事件 `OnBeatEvent`

### 4.1 发布语义

包名：

```java
otto.djgun.djcraft.event.OnBeatEvent
```

事件在 `timeline.combat_line` 的某个节拍首次经过时发布：

- 服务端：由 `DJSession.tick()` 以 20 TPS 推进；
- 客户端：由 `DJSessionClient.tick()` 在客户端高精度时间线上推进；
- 一帧/tick 跨过多个节拍时，会按时间线顺序连续发布多个事件；
- 事件不可取消，也没有 result；
- 只覆盖 `combat_line`，不会为曲目包的其他 effect line 发布事件。

字段：

| 方法 | 含义 |
|---|---|
| `getPlayer()` | 对应侧的玩家对象 |
| `getBeat()` | 原始 `BeatEvent`，含计划时间 `t`、类型和 props |
| `getDefinition()` | 已应用核心 props 覆盖的 `BeatDefinition`；缺失 type 时使用默认定义 |
| `getExactTimeMs()` | 发布时实际 DJ 时间线时间，不等于节拍计划时间 |
| `getBeatIndex()` | `combat_line` 中的零基索引 |
| `canAttack()` | definition 的 `canAttack` |

节拍类别可通过 `getDefinition().category()` 读取；事件不再提供任意伤害倍率 getter。

需要时间误差时使用：

```java
long latenessMs = event.getExactTimeMs() - event.getBeat().t();
```

### 4.2 服务端订阅示例

```java
@EventBusSubscriber(modid = ExampleAddon.MOD_ID)
public final class ExampleAddonBeatEvents {
    @SubscribeEvent
    public static void onBeat(OnBeatEvent event) {
        if (event.getPlayer().level().isClientSide()) {
            return;
        }

        if (event.getBeat().type().equals("exampleaddon_overdrive")) {
            // 服务端权威逻辑：应用效果、生成实体或更新能力。
            // 不要在这里做阻塞 IO、网络访问或曲目文件扫描。
        }
    }
}
```

事件位于节拍/tick 热路径。监听器应保持 O(1) 或近似 O(1)，避免分配大对象、扫描注册表、磁盘 IO 和阻塞操作。

### 4.3 事件 props

`BeatEvent.props()` 是 `Map<String, Object>`。当前曲目解析器只保留 string、boolean 和 number，number 实际为 `Double`。DJCraft 会用 `can_attack`、`color`、`scale`、`category`、`haptic_intensity`、`tolerance`、`particle` 和 `trigger` 覆盖事件的基础 definition。监听 `OnBeatEvent` 时应优先读取 `event.getDefinition()`，不要自行重复解析这些核心键。

附属 Mod 的自定义键仍应显式检查类型：

```java
Object raw = event.getBeat().props().get("power");
double power = raw instanceof Number number ? number.doubleValue() : 1.0;
```

附属 Mod 可以约定自定义键，但应使用 namespace/前缀避免与核心字段冲突，例如 `exampleaddon:power`。在事件以外查询曲目数据时，可调用 `TrackPack.resolveDefinition(beat)` 获得同样的默认值和 props 覆盖语义；`getDefinition(String)` 只返回未覆盖的基础 definition。

## 5. 武器音效身份解析器

### 5.1 何时需要

默认情况下，DJCraft 使用物品注册 ID 作为武器音效 Profile ID：

```text
物品 exampleaddon:rifle → Profile exampleaddon:rifle
```

只要创建资源文件：

```text
assets/exampleaddon/djcraft/weapon_sounds/rifle.json
```

就不需要 Java 注册。

仅在以下场景注册 resolver：

- 同一物品根据数据组件使用多个音效 Profile；
- 附件、弹药类型或工作模式改变声音身份；
- 多个物品共享一个逻辑 Profile；
- 兼容第三方物品且不能修改其注册 ID。

Profile JSON 规范见 [资源包开发文档](resource-pack.md#5-武器音效-profile)。

### 5.2 注册事件

`RegisterDJWeaponSoundResolversEvent` 在 common setup 的 enqueue work 中通过 `NeoForge.EVENT_BUS` 发布一次，随后注册表立即冻结。resolver 必须 side-safe。

在附属 Mod 构造阶段注册监听器：

```java
@Mod(ExampleAddon.MOD_ID)
public final class ExampleAddon {
    public static final String MOD_ID = "exampleaddon";

    public ExampleAddon(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::registerDjSoundResolvers);
    }

    private void registerDjSoundResolvers(RegisterDJWeaponSoundResolversEvent event) {
        event.register(100, stack -> {
            if (!stack.is(ExampleItems.MODULAR_RIFLE.get())) {
                return null;
            }

            boolean suppressed = Boolean.TRUE.equals(
                    stack.get(ExampleComponents.SUPPRESSED.get()));
            return ResourceLocation.fromNamespaceAndPath(
                    MOD_ID, suppressed ? "rifle_suppressed" : "rifle");
        });
    }
}
```

resolver 规则：

- priority 越大越先执行；
- 返回非 null 的第一个 resolver 获胜；
- 返回 null 表示“不处理”；
- resolver 抛出运行时异常时 DJCraft 记录错误并继续下一个；
- 全部 resolver 返回 null 时回退到物品注册 ID；
- 空 ItemStack 回退到 `djcraft:generic`；
- 冻结后再注册会抛 `IllegalStateException`。

resolver 会在声音请求校验和物品身份解析中频繁调用。只读取 ItemStack/数据组件，不要做世界扫描、文件 IO 或客户端专用调用。

### 5.3 服务端安全校验

客户端发送武器声音意图时，服务端会验证玩家主手、副手或背包中确实持有能解析为该 Profile 的物品。resolver 必须在两端对同一 ItemStack 得出相同 ID，否则合法声音可能被服务端拒绝。

### 5.4 第一人称动画 Profile

附属 Mod 无需调用客户端 Java API即可为物品增加动画。将 Blockbench/GeckoLib Clip 放在：

```text
assets/<addon_namespace>/animations/*.json
```

Clip ID 必须使用 `animation.<addon_namespace>.first_person.<名称>`；加载器会忽略普通实体动画以及
作者 namespace 与资源文件 namespace 不一致的 Clip。

再创建：

```text
assets/<addon_namespace>/djcraft/animation_profiles/<path>.json
```

Profile 可用精确物品 ID 和 item tag 选择物品，并逐语义绑定 Clip 与可选的固定 `duration_beats`。每个语义按精确物品、标签、`djcraft:generic`、Java 安全回退的顺序独立解析，因此附属 Profile 不需要复制所有动作。

Clip 使用 `first_person_hand` 表示模型 display 之前的手部空间运动，使用
`first_person_item` 表示模型 display 之后的物品中心空间运动；两个骨骼可以同时存在，
但必须共享关键帧时间。一般武器挥动、后坐和换物应使用 `first_person_hand`，只有需要随
物品模型局部轴旋转的动作才应使用 `first_person_item`。旧的单
`first_person_item` Clip 继续兼容，但在带特殊第一人称 display 的模型上仍会继承该模型的
轴向与缩放。

该能力完全是 client-only：不能改变服务端伤害、弹药、冷却、READY 或 proof。资源路径、schema、内置语义和冲突顺序见[资源包开发文档](resource-pack.md#64-动画-profile)。当前没有按数据组件或附件动态选择动画 Profile 的 Java resolver。

### 5.5 注册和触发自定义动画语义

`RegisterDJAnimationSemanticsEvent` 是正式的 client-only 扩展点。DJCraft 在
`FMLClientSetupEvent` 的 enqueue work 中把它发布到 `NeoForge.EVENT_BUS` 一次，随后立即冻结注册表。
附属 Mod 必须在仅客户端加载的类中监听，并保存 `register` 返回的规范对象：

```java
@EventBusSubscriber(modid = ExampleAddon.MOD_ID, value = Dist.CLIENT)
public final class ExampleAddonAnimations {
    public static DJAnimationSemantic SPIN;

    @SubscribeEvent
    public static void registerSemantics(RegisterDJAnimationSemanticsEvent event) {
        SPIN = event.register(
                ResourceLocation.fromNamespaceAndPath(ExampleAddon.MOD_ID, "spin"),
                DJAnimationProfile.Channel.IMPULSE,
                75,
                0.5);
    }
}
```

参数依次为语义 ID、播放通道、优先级和未由事件/Profile 指定时的默认拍长：

- `ACTION`：同一只手保留一个持续动作，只有不低于当前优先级的新动作会替换它；
- `IMPULSE`：同一只手的新冲击会替换旧冲击；
- `TRANSITION`：用于物品/姿态过渡；附属语义按进入过渡处理，内置 `unequip_start` 保留专用交接行为；
- priority 范围是 `0..100`，默认拍长必须为有限非负数；
- 重复 ID、非法参数或冻结后的注册会抛出异常；
- 八参数重载还可声明无资源绑定时的 Y/Z 位移与 X/Z 旋转安全回退；四参数重载使用 no-op 回退。

资源 Profile 用完整 ID 绑定：

```json
{
  "selectors": {"items": ["exampleaddon:turntable_blade"]},
  "animations": {
    "exampleaddon:spin": {
      "clip": "animation.exampleaddon.first_person.spin",
      "duration_beats": 0.75
    }
  }
}
```

触发时使用注册返回的对象和实际手。运行时会从 DJ 会话虚拟拍时钟创建事件，并固定当前资源绑定：

```java
DJAnimationRuntime.getInstance().emit(
        ExampleAddonAnimations.SPIN,
        hand,
        stack,
        session,
        0.0,
        DJActionOutcome.NOT_JUDGED);
```

`durationBeats` 传 `0.0` 时使用 Profile 的 `duration_beats`，否则使用事件值，最后回退到注册时默认值。
自定义动画语义不会自动映射到 DJCraft 武器音效语义，也不会发送网络包；需要多人权威触发时，附属
Mod 应自行同步玩法结果，再在客户端调用 `emit` 或 `emitVisualOnly`。旧
`DJAnimationEvent.Kind` 和相应 runtime 重载仅为内置语义兼容保留，新代码不应继续扩展它。

## 6. 曲目数据查询

### 6.1 `TrackPackManager`

常用只读方法：

```java
TrackPackManager manager = TrackPackManager.getInstance();

Optional<TrackPack> pack = manager.getTrackPack("example_track");
boolean loaded = manager.isPackLoaded("example_track");
Collection<TrackPack> all = manager.getLoadedPacks();
Set<String> ids = manager.getLoadedPackIds();
Optional<String> definitionHash = manager.getPackHash("example_track");
```

注意：

- ID 在加载时转为小写，调用方应使用规范小写 ID；
- 返回的是当前侧本地注册表；多人客户端的本地列表不等于服务端列表；
- `getLoadedPacks()`、`getLoadedPackIds()` 和哈希 Map 返回快照；
- 不要在 tick/render 热路径反复读取文件流；
- `loadAllPacks()`、`reloadAllPacks()`、`installPreparedPack()` 属于管理/内部写接口，附属 Mod 不应在任意线程调用。

读取可选曲目文件：

```java
try (InputStream input = manager.openFileStream(packId, "myaddon/config.json")) {
    if (input != null) {
        // 在加载或后台准备阶段解析；不要在节拍、渲染或音频线程解析。
    }
}
```

曲目包作者与附属 Mod 可约定私有文件路径。该文件不会进入只覆盖 `track.json` 的定义哈希，但会进入 DJCraft 用于 verified 和组网准备的完整内容哈希；因此更改私有文件也会要求客户端持有完全相同的曲包内容。

### 6.2 数据 records

主要不可变数据类型：

```text
TrackPack
TrackMeta
TrackSettings
Timeline
BeatEvent
BeatDefinition
BeatPostJudgmentBehavior
DiscStatistics
DiscPlaybackReference
HitResult
```

建议只读使用。尽管 record 本身不可重新赋值，部分内部集合来自普通 Map/List，不能假设所有嵌套对象都深度不可变；附属 Mod 不应修改这些集合。

`TrackMeta` 的组件顺序为 `version, author, bpm, difficulty, soundFile, offsetMs,
playbackStartMs, totalDurationMs, displayName`。`playbackStartMs` 对应曲目包 JSON 的
`meta.playback_start_ms`；运行时通过 `TrackPack.getPlaybackStartMs()` 读取非负的有效值。
`totalDurationMs` 是同一原始音频坐标系中的排他结束位置，而不是从
`playbackStartMs` 起算的持续时长；有效播放区间为
`[playbackStartMs, totalDurationMs)`。`TrackPack.hasReachedPlaybackEnd(long)` 接受原始
音频播放位置，到达该边界时返回 `true`。会话时间线方法返回的已应用 `offsetMs` 的时间
不能直接传给该方法。
`TrackPack.resolveDefinition(BeatEvent)` 返回已应用核心事件 props 的有效 definition；
`TrackPack.getDefinition(String)` 继续返回曲包中声明的基础 definition。

`BeatDefinition` 的组件顺序为 `canAttack, color, scale, category, hapticIntensity,
tolerance, particle, trigger, texture, landingXPercent, spawnAdvanceMs, hitBehavior,
missBehavior, rotationRpm, matchedHitBehavior`。旧的八参数构造签名和上一版十四参数
Falling 构造签名继续作为重载存在并注入默认值，
但 record 的规范构造器和解构形状已经扩展，附属 Mod 升级时仍应重新编译。
`BeatDefinition.DEFAULT_TEXTURE` 为
`djcraft:textures/gui/beats/blue_beat.png`；规范构造器中的 null/空 texture 也会回退该值。
`BeatPostJudgmentBehavior` 值为 `NONE`、`FREEZE_DISSIPATE`、`DISSIPATE`、`BOUNCE`。
`matchedHitBehavior` 省略时继承 `hitBehavior`；
`behaviorAfterJudgment(hit, categoryMatched)` 可按判定结果选择最终视觉行为。

这些新增组件只影响客户端 Falling HUD；服务端解析相同曲包内容以保持定义和完整内容
哈希一致，但不会根据视觉字段改变 proof、判定、伤害或网络协议。事件 `props` 可用
对应 snake_case 键覆盖这些组件。附属 Mod 不应在 render 热路径自行解码
`texture`；应让 DJCraft 的客户端资源重载器管理曲包 PNG/GIF。

普通近战客户端处理器应调用
`BeatJudgeFacade.judgeMeleeAttackAndNotify(DJSessionClient, ItemStack)`；该入口读取注册的
`djcraft:melee` 行为策略。物品注册行为的专用攻击动作调用
`judgeAttackAndNotify(DJSessionClient, ItemStack)`，按该行为的 `disabledByCanAttack` 策略判定。
两者都必须传入本次动作的准确物品栈，并用与服务端一致的 `djcraft:swift` / `djcraft:smash`
标签规则预测 `matched_hit_behavior`；原单参数攻击重载保留并以当前主手作为兼容回退。
非攻击判定调用 `judgeAndNotify`，不会产生类别匹配视觉。该布尔结果不进入 proof 或网络协议，
不能作为伤害权威。

## 7. DJ 会话查询

### 7.1 服务端

```java
Optional<DJSession> session = DJModeManager.getInstance().getSession(player);

session.ifPresent(active -> {
    long timeMs = active.getCurrentTimeMs();
    int combo = active.getCombo();
    double energy = active.getEnergy();
    TrackPack pack = active.getTrackPack();
});
```

可用只读信息包括：当前时间、上一/下一节拍距离、曲目、玩家、session ID、跨会话连击、当前曲目连击、能量、容错次数、移动状态、触发节拍数、唱片 UUID 和当前曲目最大连击。对应方法包括 `getCombo()`、`getCurrentTrackCombo()` 与 `getCurrentTrackMaxCombo()`；兼容方法 `getSessionMaxCombo()` 返回值也表示当前曲目最大连击。

`DJSession` 还有 `setCombo`、`setEnergy`、`grantEnergy`、`tryConsumeEnergy`、连击确认、盾牌、时钟审计等 public 方法，但它们是 DJCraft 服务端状态机内部写接口。`setCombo(int)` 会把负数截为 0、同时更新当前曲目连击和当前曲目最大值、刷新空闲连击计时并同步客户端；`setEnergy(double)` 会按当前 `djcraft:max_energy` 截断并同步客户端。管理用途应优先使用权限等级 2 的 `/dj set combo <targets> <value>` 与 `/dj set energy <targets> <value>`。附属 Mod 直接调用其余写接口可能破坏 action sequence、网络同步或反作弊校验。除非针对当前版本完成联调，不应将它们视为稳定 API。

### 7.2 客户端

客户端优先使用：

```java
DJModeManagerClient.getInstance().getActiveSession()
```

而不是分别组合 `isInDJMode()`、`getSession()` 和 `isPlaying()`：

```java
DJModeManagerClient.getInstance().getActiveSession().ifPresent(session -> {
    long timelineMs = session.getCurrentTimeMs();
    int combo = session.getCombo();
    double energy = session.getEnergy();
});
```

这段代码必须位于 client-only 类。`startSession`、`stopSession`、`gameTick`、`renderTick` 和资源状态 apply/predict 方法由 DJCraft 网络与客户端循环拥有，附属 Mod 不应手工驱动。

### 7.3 通用玩家残影绘制

客户端视觉效果可在 `RenderPlayerEvent.Post` 中复用当前玩家已完成动画计算的模型姿态：

```java
DJPlayerAfterimageRenderer.emit(
        event.getRenderer(),
        (AbstractClientPlayer) event.getEntity(),
        event.getPartialTick(),
        0xAA55FFFF,
        500L);
```

签名为
`emit(PlayerRenderer, AbstractClientPlayer, float partialTick, int argb, long lifetimeMs)`。
它必须只从物理客户端的主渲染线程调用；`argb` 同时控制颜色与初始透明度，
`lifetimeMs` 必须大于 0。方法捕获玩家基础模型和第二层皮肤部件的姿态、可见性、缩放、
世界位置、身体朝向、皮肤和光照，之后由 DJCraft 的世界渲染入口在原位置淡出。它不会创建
实体，也不会复制盔甲、手持物、披风、名字或阴影。调用方负责通过自己的网络包通知需要看到
效果的客户端；该方法本身不发送网络消息。队列最多保留 64 个残影，超出时淘汰最旧快照。
快照同时冻结原版玩家渲染器的游泳与滑翔整体旋转；绘制使用全亮度自发光材质，调用方传入的
`argb` 仍决定颜色和初始透明度。

### 7.4 启动会话的限制

`DJModeManager.startSession(...)` 虽然是 public，但只创建服务端会话；它不会自动完成所有客户端播放启动、资源状态、移动状态和唱片引用验证。当前没有面向附属 Mod 的高层 `startSession` 服务 API。

因此：

- 管理用途优先调用已有 `/dj play` 命令；
- 玩家点播走 DJCraft 便携点唱机流程；
- 附属 Mod 不应直接发送 DJCraft 的 `PlayTrackPayload` 拼装内部协议；
- 若确需代码启动会话，应先为 DJCraft 增加正式 facade/event，而不是在附属 Mod 中复制 `DJSessionRequestHandler`。

停止服务端会话可用 `DJModeManager.stopSession(player, reason)`，它会向服务端玩家下发停止包；但 `StopReason` 和协议仍可能随版本变化。

## 8. 节拍判定接口

### 8.1 纯判定

`BeatJudgmentEvaluator` 不依赖世界，可用于工具、预览和测试：

```java
HitResult result = BeatJudgmentEvaluator.evaluate(timelineTimeMs, trackPack);

if (result.isHit()) {
    BeatCategory category = result.beatData().category();
    int beatIndex = result.beatIndex();
}
```

`HitResult` 不再携带任意曲目伤害倍率。`BeatDefinition.category()` 返回 `NORMAL`、
`WEAKBEAT` 或 `DOWNBEAT`；DJCraft 在服务端按 `djcraft:swift` / `djcraft:smash`
物品标签应用内置类别伤害规则。附属 Mod 如需接入同一规则，应通过数据包扩展标签，
不要由客户端自行修改伤害。

调用者传入的必须是已应用曲目 offset 的“时间线时间”。如果从会话取值，`DJSession.getCurrentTimeMs()` 和 `DJSessionClient.getCurrentTimeMs()` 已返回时间线时间。

### 8.2 客户端判定与反馈

DJCraft 自己的客户端武器处理必须使用：

```java
BeatJudgeFacade.judgeAndNotify(session)
```

它同时完成判定与客户端反馈。附属 Mod 若接入 DJCraft 客户端攻击流程，应优先复用 facade，不要调用 `BeatJudgeUtil.judge(...)` 后自行模仿通知；后者容易漏掉动画、声音或 proof 状态。

`BeatJudgeFacade` 位于 client 包，只能在客户端类中引用。

## 9. 物品数据组件与属性

### 9.1 唱片数据组件

```java
ModDataComponents.TRACK_PACK_ID
ModDataComponents.DISC_ID
ModDataComponents.DISC_STATISTICS
```

读取示例：

```java
String trackId = stack.get(ModDataComponents.TRACK_PACK_ID.get());
UUID discId = stack.get(ModDataComponents.DISC_ID.get());
DiscStatistics stats = stack.getOrDefault(
        ModDataComponents.DISC_STATISTICS.get(), DiscStatistics.EMPTY);
```

写入这些组件相当于创建/修改 DJCraft 唱片身份。必须在服务端执行，并保证：

- 物品确实是 `djcraft:empty_disc`；
- track ID 已在服务端加载；
- UUID 唯一；
- 统计非负且可信。

更安全的默认方案是让玩家使用 DJ 刻录台。当前没有公开 `createRecordedDisc` facade。

`DiscStatisticsCodec.CODEC` 可用于持久化兼容数据：

```json
{
  "max_combo": 12,
  "total_play_time_ms": 45000
}
```

### 9.2 最大能量属性

属性 ID：

```text
djcraft:max_energy
```

Java Holder：

```java
ModAttributes.MAX_ENERGY
```

默认值 50，范围 0–1024，已同步到客户端，并挂载到 Player。附属 Mod 可按原版
`AttributeModifier` 规则临时或永久修改最大能量。修改应在服务端进行，让属性同步负责
客户端显示。能量手环、恢复档位、创造模式和跨会话保留属于玩法规则，见
[游戏机制与实现：连击、容错与能量](gameplay-mechanics.md#resources)。

### 9.3 快捷栏/副手被动效果物品

需要采用“快捷栏任一格或副手存在时生效”规则的物品可继承：

```java
otto.djgun.djcraft.item.HotbarOrOffhandEffectItem
```

构造器签名与原版 `Item` 一致：

```java
public ExamplePassiveItem(Item.Properties properties) {
    super(properties);
}
```

服务端效果逻辑通过注册后的物品实例查询：

```java
ExampleItems.PASSIVE.get().isActiveFor(player)
```

`isActiveFor(Player)` 只扫描副手和物品栏槽位 `0..8`，不读取主背包其余槽位；同一物品出现
多次仍只返回一个布尔结果。该类型只统一生效位置，不会自动注册或执行具体效果，也不会同步
附属 Mod 自定义状态；调用方仍须在服务端权威逻辑中应用效果，并按需同步客户端。DJCraft
当前用它实现 DJ Fumo、Flowery、瓶中音符和能量手环的携带条件。

## 10. 自定义武器兼容

### 10.1 自动获得的能力

- 任意非空物品的普通主手攻击都会经过 DJCraft 通用近战判定、服务端 proof、冷却、能耗和
  音效/动画分类，不需要为每种普通近战武器编写客户端处理器；
- 继承 `BowItem`、`CrossbowItem`、`ShieldItem`、`TridentItem` 或 `MaceItem` 的物品默认按
  继承关系进入对应客户端和服务端流程；`WindChargeItem` 默认进入触发型流程；
- Java 继承回退可由 `djcraft/item_behaviors` 数据包 Profile 覆盖，也可用 `djcraft:none` 退出；
- 标准长按/释放物品可通过 `djcraft:charge` 接入两阶段判定；标准右键 `ItemStack.use()`
  触发物品可通过 `djcraft:trigger` 接入服务端执行和投射物追踪；
- `djcraft:trident`、`djcraft:mace` 可把其他注册物品接入胶囊范围近战执行器，尺寸可由物品
  Profile 覆盖；两者也会接入各自的即时右键投掷流程；
- 武器声音默认按物品注册 ID 匹配资源 Profile；
- 服务端管理员可通过 `data/<物品命名空间>/djcraft/item_timing/<物品路径>.json` 为附属 Mod
  物品分别覆盖 DJ 左右键冷却、切换前摇、普通攻击能量成本，以及已接入 DJ 专用右键流程的使用能量成本。

这些内置处理器的实际攻击、能耗和连击语义见
[游戏机制与实现：武器专用机制](gameplay-mechanics.md#weapons)。本节的“自动获得”只表示
进入现有处理路径，不把该路径承诺为稳定 Java API。

### 10.2 用数据包适配已有行为

行为 Profile 位于：

```text
data/<namespace>/djcraft/item_behaviors/<profile>.json
```

最小示例：

```json
{
  "priority": 100,
  "selectors": {
    "items": ["examplemod:charged_rifle"],
    "tags": ["examplemod:trigger_weapons"]
  },
  "behavior": "djcraft:trigger"
}
```

数据包可以选择所有已注册行为 ID。DJCraft 内置以下行为：

| 行为 | 适用范围与边界 |
|---|---|
| `djcraft:bow` | 兼容行为，只接受真实 `BowItem` 子类 |
| `djcraft:crossbow` | 兼容行为，只接受真实 `CrossbowItem` 子类 |
| `djcraft:shield` | 兼容行为，只接受真实 `ShieldItem` 子类 |
| `djcraft:charge` | 任意注册物品；接入标准长按与释放，最终效果仍由原物品释放逻辑产生 |
| `djcraft:trigger` | 任意注册物品；Hit 后在服务端调用动作来源栈的 `ItemStack.use()` |
| `djcraft:trident` | 任意注册物品；选择圆柱长 5、半径 1 的胶囊范围左键与即时投掷执行器 |
| `djcraft:mace` | 任意注册物品；选择持续 4 tick、圆柱长 2、半径 2 的胶囊范围左键与即时重锤投掷执行器 |
| `djcraft:none` | 退出显式或 Java 继承行为，不为物品增加动作能力 |

注册表还包含不可由 `item_behaviors` 分配给物品的动作行为：`djcraft:melee`、`djcraft:mining`、
`djcraft:eating`、`djcraft:dash`、`djcraft:ground_jump`、`djcraft:double_jump` 和
`djcraft:ground_slam`。其中挖掘与进食使用非攻击判定，Hit 后分别由服务端校验合格工具并调用原版
破坏流程、校验正在进食并调用原版完成流程。下砸无论判定结果都会移动；该行为的 Hit 只用于
服务端落地时解锁高度门槛、范围伤害、垂直击飞和连击奖励，不能由 `item_behaviors` 分配给物品。

选择器支持精确物品 ID 和 item tag。精确物品优先于标签；同层按 `priority` 降序、Profile ID
字典序解析。Profile 会在 `/reload` 时逐文件校验并原子发布，随后由服务端把完整显式映射同步给
在线客户端；新玩家加入时也会同步。客户端不需要安装同一数据包，客户端标签状态也不作为战斗权威。

`item_behaviors` 不能在 JSON 中注册 Java 类、脚本、payload 或任意伤害公式；它可以通过 `melee`
对象覆盖注册普通近战的 `reach`/两个角度，或范围近战的 `cylinder_length`/`radius`。附属 Mod
注册的自定义行为 ID 及其执行器属于代码注册表，也可以被这里的选择器引用。`item_timing` 只提供
`beat_cooldown`、`use_beat_cooldown`、`switch_warmup`、
`attack_energy_cost` 和 `use_energy_cost`；仅配置 `use_energy_cost` 不会自动选择右键行为。
对应 public record 为
`DJItemTimingProfile(Integer beatCooldown, Integer useBeatCooldown, Integer switchWarmup, Double attackEnergyCost, Double useEnergyCost)`；
原有两参数和四参数构造器继续保留，并令 `useBeatCooldown=null`。运行时可通过
`DJItemCooldownManager.getBeatCooldown(stack)` 查询左键值、通过 `getUseBeatCooldown(stack)` 查询带
旧字段回退的右键值；`hasExplicitUseBeatCooldown(stack)` 只判断新字段是否显式存在。

`djcraft:trident` family 的服务端执行器还会为生成的三叉戟启用永久无重力、三拍忠诚返回、返回中
可近战打回、原版 2 倍宽高碰撞箱、同步发光轮廓、0.25 格中心回收判定和投掷命中施加
`djcraft:rend`。投射物不穿透实体或方块，首次碰撞立即开始忠诚返程；有效会话内的返程以最高 2.5
速度再攻击一个敌对生物。去程和返程都由服务端以完整投射物 AABB 和目标当 tick 位移做相对运动
连续扫掠，按首次接触排序；去程的实体线段仍受方块截断。近战打回沿攻击者服务端视线方向以 2.5 速度重新发射。DJ 三叉戟右键投掷
每次实际伤害独立增加连击，不使用通用 sequence 去重。`djcraft:rend` 是普通注册表状态效果：等级从 amplifier 0
开始，每级使有攻击者的前置伤害增加 2；DJCraft 内置投掷施加 amplifier 2、160 tick。附属 Mod 可
通过 `ModEffects.REND` 或注册 ID 查询并施加，但这些行为不会为自定义投射物自动生成网络同步。
`djcraft:mace` family 的右键执行器会把动作物品栈复制到 `djcraft:thrown_mace` 实体用于渲染，
但不移除或损耗真实物品。投射物初速 1.75、重力 0.07、碰撞箱宽高 0.75 格、最长存在 60 tick，
服务端同样以完整重锤 AABB 和目标当 tick 位移做连续扫掠，且先用方块命中截断实体线段。基础伤害 16 并应用
现有节拍类别倍率；首次有效生物命中后为目标当前 Y 速度增加 1.6，无视击退抗性，然后消失。
它不运行原版重锤坠落猛击、范围伤害或重锤附魔命中逻辑。能量费用仍由物品的
`use_energy_cost` 决定，原版重锤内置值为 10。
左键基础窗口为 4 tick；`ModEnchantments.LINGERING_SWEEP` 对应公开资源键
`djcraft:lingering_sweep`，每级增加 3 tick且运行时不封顶；附魔 JSON 的 `max_level: 2` 只限制
正常生存获取。附魔 JSON 只允许 `#minecraft:enchantable/mace`，通过命令放到其他
`djcraft:mace` family 物品上的附魔仍会被服务端读取。
完整 Schema、非法文件处理、优先级和回退规则见[数据包开发文档](data-pack.md#36-物品动作行为映射)。

trigger-family 物品还可提供
`data/<物品命名空间>/djcraft/ray_weapons/<物品路径>.json`，从而复用 DJCraft 的服务端权威
方块截断、默认横纵各 7% 且可覆盖的服务端软辅助瞄准、单目标/贯穿选择、节拍伤害倍率、
sequence 去重（DJ 三叉戟右键投掷伤害除外）和射线视觉广播。可选 `auto_charge_beats` 会把通过判定的动作从实际按下时间延迟指定的
连续虚拟节拍数，并持续
验证原手和来源槽；可选 `explosion` 对象提供不破坏地形的服务端范围伤害、墙体暴露衰减和权威
冲击波半径。扩展这些字段会改变同步 wire shape，当前网络协议为 `2.28.0`。该 Profile 不会注册
新行为，附属物品仍需通过继承、`item_behaviors` 或 `RegisterDJItemBehaviorsEvent` 进入 trigger
family。完整字段与回退见[数据包开发文档](data-pack.md#34-即时射线武器-profile)，客户端样式见
[资源包开发文档](resource-pack.md#35-射线效果-profile)。

内置 `djcraft:magic_crossbow` 是单目标示例：它以 `pierce_entities: false` 保留最近的权威命中，
并通过客户端样式的枪口/命中/终点爆发缩放实现弱弹道、强命中反馈；这些视觉字段不参与伤害判定。
内置 `djcraft:explosive_bow` 是 `auto_charge_beats: 1` 与 `explosion` 的组合示例；它通过
`item_behaviors` 显式进入 trigger family，因此非 DJ 时仍可保留 `BowItem` 的原版使用行为。

### 10.3 注册公共行为 API

注册事件在 common setup 的 enqueue work 中通过 NeoForge 游戏事件总线发布一次，返回后注册表
冻结。监听器必须 side-safe；不能从注册类引用 `Minecraft`、HUD 或其他 client-only 类型。

```java
private void registerDjBehaviors(RegisterDJItemBehaviorsEvent event) {
    event.register(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "charged_rifle"),
            DJItemBehavior.CHARGE,
            item -> item instanceof ChargedRifleItem);

    event.registerTrigger(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "resonance_burst"),
            item -> item instanceof ResonanceBurstItem,
            false,
            context -> {
                ResonanceBurstItem.fire(
                        context.player(), context.sourceStack(), context.judgment());
                return InteractionResultHolder.success(context.sourceStack());
            });

    event.registerAreaMelee(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "long_spear"),
            DJItemBehavior.TRIDENT,
            item -> item instanceof LongSpearItem,
            true,
            new DJAreaMeleeBehavior(6.0, 0.75));
}
```

注册入口：

```java
RegisterDJItemBehaviorsEvent.register(id, family, supports)
RegisterDJItemBehaviorsEvent.register(id, family, supports, disabledByCanAttack)
RegisterDJItemBehaviorsEvent.registerTrigger(id, supports, executor)
RegisterDJItemBehaviorsEvent.registerTrigger(id, supports, disabledByCanAttack, executor)
RegisterDJItemBehaviorsEvent.registerAreaMelee(id, family, supports, meleeBehavior)
RegisterDJItemBehaviorsEvent.registerAreaMelee(id, family, supports, disabledByCanAttack, meleeBehavior)
```

- `id` 必须是附属 Mod 自己命名空间下的稳定资源 ID；重复 ID 或冻结后注册会抛异常；
- `family` 复用 `DJItemBehavior` 的 `NONE`、`BOW`、`CROSSBOW`、`SHIELD`、`CHARGE`、
  `TRIGGER`、`TRIDENT` 或 `MACE` 输入、proof 与结算生命周期；
- `supports` 是物品兼容校验，不会自动把匹配物品分配给该行为；物品/标签分配仍写在
  `item_behaviors` 数据包 Profile 中；谓词抛出的运行时异常会记录错误并按不兼容处理；
- `disabledByCanAttack` 默认为 `true`：最近节拍的有效 definition 为 `can_attack: false` 时，本次
  行为只能判定 Miss。设为 `false` 可忽略该门；内置盾牌以及不可分配给物品的挖掘、进食、冲刺、
  二段跳、地面跳、下砸行为均为 `false`；内置且不可分配给物品的近战行为为 `true`。同一物品的不同动作
  分别解析对应行为，例如盾牌左键使用 `djcraft:melee`，右键使用 `djcraft:shield`；
- `registerTrigger` 仍复用通用 trigger payload。执行器只在服务端线程上、proof/来源/冷却/能量
  验证完成后运行；当 `djcraftOffBeatAttackDamagePercent` 大于 0 时，合法的 Miss 也会调用执行器，
  此时 `DJTriggerBehaviorContext.judgment().isHit()` 为 false，生成的伤害继承不卡拍百分比且不会产生
  连击或命中确认音；执行期间生成且 owner 为玩家的投射物会自动继承动作上下文；
- `DJTriggerBehaviorContext.sourceStack()` 是动作来源槽位中的实时栈。执行器可修改它，并通过
  返回的 `InteractionResultHolder<ItemStack>` 请求替换来源槽位；返回 null 或抛异常会记录错误、
  终止该动作并按 Miss 处理；
- 没有自定义执行器的 trigger-family 行为继续调用来源栈的标准 `ItemStack.use()`；charge-family
  行为继续调用原物品的标准长按/释放逻辑；其他 family 复用对应内置执行器；
- `DJAreaMeleeBehavior` 是可继承的 side-safe 范围近战描述符。近端固定为平面、远端为半球；
  `primaryItemEffectsOnly` 控制物品附加效果是否仅用于首次接触目标，
  `resetFallDistanceAfterContact` 用于重锤式下落攻击。Profile 只能改尺寸，不能改这两个语义标志。

`DJItemBehaviorRegistry.get(id)`/`require(id)` 查询规范定义；`resolve(ItemStack)` 和 `resolve(Item)`
查询服务端同步的数据包赋值或 Java 继承回退；`values()` 返回冻结快照，其中也包含不可分配给物品的
内置 `djcraft:melee`、`djcraft:mining`、`djcraft:eating`、`djcraft:dash`、`djcraft:ground_jump`、`djcraft:double_jump`、
`djcraft:ground_slam`。`freeze()` 是 DJCraft 生命周期
所有的方法，附属 Mod 不应调用。

注册项必须在服务端和客户端以相同 ID、family、`disabledByCanAttack` 和兼容语义存在。数据包同步
只传“物品 ID → 行为 ID”，不会发送 Java 执行器或谓词；缺少相应附属 Mod 的客户端会拒绝该行为
赋值而不是从网络反序列化代码。

### 10.4 动作来源、秒切与冷却语义

标准普通攻击、范围近战、蓄力释放、触发型右键、三叉戟投掷和盾牌请求都会携带动作开始时的
来源。普通攻击和右键来源在原始鼠标/键盘按下时捕获；这早于原版同一
`handleKeybinds()` 中的数字键快捷栏切换，因此同 tick 秒切仍以切换前物品为动作来源。滚轮会在
自己的回调中立即换槽，所以滚轮与攻击/使用输入按实际回调先后决定来源。

服务端用来源槽位、注册物品 ID、当前或最近选择记录验证来源。合法动作建立内部
`DJActionContext`，固定以下事务状态：

- session ID、action sequence、实际手和短 TTL；
- `DJActionSource` 槽位与物品注册 ID；
- 动作来源 ItemStack 快照、判定结果、伤害倍率和声音 Profile ID。

动作结算期间即使玩家已换物，伤害、属性修饰符、耐久/弹药、来源栈替换、投射物上下文、声音
身份和攻击冷却仍归切换前物品；结算后恢复玩家已经选择的新槽位。切换后物品只按自身
`switch_warmup` 进入前摇。客户端所有 DJ 预测冷却共用 Session 时间结束点；只有待施加前摇
严格长于该注册物品当前剩余冷却时才覆盖，相等或更短时保持原冷却。

底层仍使用原版按注册 `Item` 计的 `ItemCooldowns`，因此同一种注册物品的多个 ItemStack 共享
冷却显示和输入门。来源槽位能保证伤害与物品消耗归属，但不会把相同物品类型改造成逐栈冷却。

`DJActionSource`、`DJActionContext`、最近槽位历史和相关 payload 虽然部分类型为 public，仍是
DJCraft 内部网络事务模型，不是附属 Mod 应自行构造或持久化的稳定 API。需要新增动作类别时应在
DJCraft 增加窄执行器或正式事件，而不是伪造来源或延长内部 TTL。

### 10.5 不能自动保证的能力

- 兼容旧配置的 `bow`、`crossbow`、`shield` 仍要求对应原版基类；跨模组通用适配应使用
  `charge`、`trigger`、`trident` 或 `mace`；
- 即时射线已有数据驱动复用入口；自定义装填、实体弹药、附件、枪口火焰和 shell 仍没有通用公开武器 API；
- 动画可通过附属 namespace Profile 按物品 ID、item tag 或同步后的内置行为接入，但不能按数据组件动态选择；
- 服务器权威攻击必须经过 DJCraft proof、action sequence 和速率限制，不能只在客户端判定后直接造成伤害。

依赖私有客户端状态、专用 payload 或非标准伤害路径的新类别仍应在 DJCraft 本体增加窄执行器
或事件，并保持 `network/DJNetwork` 双侧安全；S2C 回调委托 client-only handler，禁止 common
网络类引用客户端代码。

## 11. 网络协议边界

DJCraft payload 当前使用 registrar 版本 `2.25.0`，但 payload 类属于内部协议，不是附属 Mod
消息总线。当前攻击、charge release、trigger、trident 和 shield 请求都包含服务端验证的动作来源；
物品时序、行为与射线武器 Profile 会由服务端在玩家加入和数据包重载后自动同步；射线权威效果
另由服务端广播。附属 Mod 不应自行发送、
复制或版本绑定该内部 payload。不要：

- 重复注册 DJCraft payload ID；
- 直接构造攻击、charge release、移动、盾牌或声音 intent 来绕过正常客户端处理；
- 依赖 payload 字段顺序作为长期 ABI；
- 从 common 网络注册代码调用 Minecraft 客户端类；
- 在附属 Mod 中复制下载或播放 ready/clock 同步协议。

附属 Mod 应使用自己的 namespace 注册自己的 payload，并通过本文列出的事件/查询接口与 DJCraft 协作。

### 11.1 DJ 组网边界

DJ 组网由服务端内部的 `DJNetworkGroupManager` 维护。组 ID、邀请、成员准备状态、房主、歌单索引、随机袋、播放 ID、`GroupPlaybackClock` 以及所有组网 payload 都是可变的实现细节，不属于稳定附属 Mod API。附属 Mod 不应直接构造组网控制、准备、状态或播放 payload，也不应绕过房主权限和服务端曲目/成员校验。

组内每名玩家拥有独立 `DJSession`；同一歌曲只共享服务端时间源。客户端 `DJSessionClient`、节拍判定、第一人称动画和音频播放仍由本机 OpenAL source 计时。首次绑定会用服务端位置、半 RTT 和缓冲耗时追赶，但之后不执行周期性 seek。自然结束只有服务端可以推进；房主仍保留手动点歌、切歌、模式和停止权限。成员恢复次数、隔离状态和相关 payload 都是内部实现。

当前没有稳定的组网创建、邀请、查询或控制 facade。只读需求应优先提出专用的服务端查询接口或事件，而不是反射内部管理器；需要启动或切歌时继续通过玩家现有 UI/命令完成。

## 12. 尚未公开的扩展点

以下能力当前没有正式 API：

1. 高层、安全、完整同步的会话启动/切歌服务；
2. 会话开始、停止、连击变化、能量变化的专用可取消/观察事件；
3. 注册超出当前 family 的全新客户端输入方式、proof 类型和非 trigger 服务端生命周期；
4. 自定义移动能力、能量消费和服务器 proof 类型；
5. 按数据组件、附件或工作模式动态选择第一人称动画 Profile；
6. 曲目 effect line 调度事件；
7. definition `particle`、`trigger`、`haptic_intensity` 的执行接口；
8. 唱片创建/刻录 facade；
9. 数据包 Codec、标签契约和 reload listener API；
10. 独立稳定 API artifact 与版本兼容策略。

附属 Mod 如果需要其中某项，优先在 DJCraft 增加最小正式接口并补测试，避免依赖 private 状态、Mixin 注入点或网络实现细节。

## 13. 最小兼容示例

一个只做“自定义武器声音 + 服务端节拍效果”的附属 Mod，推荐结构：

```text
src/main/java/exampleaddon/
├─ ExampleAddon.java                       # 注册 side-safe resolver 监听器
└─ ExampleAddonBeatEvents.java             # 服务端 OnBeatEvent 监听器

src/main/resources/
├─ META-INF/neoforge.mods.toml              # required DJCraft dependency
└─ assets/exampleaddon/
   ├─ animations/rifle.animation.json
   ├─ djcraft/animation_profiles/rifle.json
   ├─ sounds.json
   ├─ sounds/weapons/*.ogg
   └─ djcraft/weapon_sounds/rifle.json
```

这一方案不触碰 DJCraft 网络、Mixin、OpenAL 或会话内部写状态，版本升级风险最低。

## 14. 回归验证

- `./gradlew build` 同时验证附属 Mod 与当前 DJCraft jar 的二进制引用。
- 在 dedicated server 验证 common 类没有 client import。
- 在单人和多人分别确认 `OnBeatEvent` 的侧过滤，避免效果执行两次。
- 用普通/变体 ItemStack 验证 resolver 双端得到同一 Profile ID。
- F3+T 验证 Profile 重载、fallback 和缺失声音事件。
- 对 `item_behaviors` 精确物品与标签选择器分别执行 `/reload` 和新玩家加入同步测试；验证
  `charge`/`trigger` 使用原物品标准逻辑，`none` 能退出继承回退，非法 Profile 只拒绝自身。
- 对公共行为注册测试重复 ID、冻结时机、双端一致性和兼容谓词异常；自定义 trigger 执行器需在
  dedicated server 验证服务端线程、来源栈替换、投射物 owner、异常回退、冷却和能量只结算一次。
- 为 `disabledByCanAttack=true/false` 的自定义行为分别测试 `can_attack: false` 节拍；确认默认行为
  被禁用，而内置盾牌和显式 false 的专用行为仍按容差判定；另确认盾牌等普通左键读取
  `djcraft:melee`，`trident`/`mace` 范围左键读取物品实际注册行为。
- 对继承原版武器类的物品测试对应的专用动作和服务端 proof；三叉戟需额外验证范围近战、即时投掷、
  能量、碰撞返程、返程额外一次伤害、逐次伤害连击、忠诚和非 DJ 原版回退。
- 在集成服务器创建者、加入该局的其他玩家和独立服务器玩家上验证数字键秒切：旧武器伤害、
  属性、耐久/弹药和攻击冷却正常结算，新物品只获得自身切换前摇；再分别测试现有剩余冷却短于、
  等于和长于前摇的覆盖边界。
- 第一人称相关修改需测试主手、副手、F 键换手、相同物品双持、换物、低 FPS 和资源重载。
- 不要只靠编译判断 Mixin、渲染、声音和节拍同步正确；这些都需要进游戏烟雾测试。

## 15. 主要代码索引

- 事件：`event/OnBeatEvent.java`
- 音效扩展：`sound/RegisterDJWeaponSoundResolversEvent.java`、`DJWeaponSoundIdentityResolver.java`、`DJWeaponSoundIdentityRegistry.java`
- 动画资源：`client/animation/DJAnimationLibrary.java`、`DJFirstPersonAnimator.java`
- 曲目查询：`loader/TrackPackManager.java`
- 数据模型：`data/TrackPack.java`、`BeatEvent.java`、`BeatDefinition.java`、`DiscStatistics.java`
- 服务端会话：`session/DJModeManager.java`、`DJSession.java`
- 客户端会话：`session/DJModeManagerClient.java`、`DJSessionClient.java`
- 判定：`combat/BeatJudgmentEvaluator.java`、`combat/client/BeatJudgeFacade.java`
- 公共行为 API：`api/combat/RegisterDJItemBehaviorsEvent.java`、`DJItemBehaviorRegistry.java`、
  `DJItemBehaviorDefinition.java`、`DJTriggerBehaviorExecutor.java`、`DJTriggerBehaviorContext.java`
- 武器行为与时序：`combat/DJItemBehavior.java`、`DJItemBehaviorManager.java`、
  `DJItemCooldownManager.java`、`combat/client/DJClientItemCooldowns.java`
- 动作事务：`combat/DJActionSource.java`、`DJActionContext.java`、`DJActionSourceHistory.java`
- 组件与属性：`init/ModDataComponents.java`、`ModAttributes.java`
- 网络边界：`network/DJNetwork.java`、`client/DJClientNetworkRegistration.java`

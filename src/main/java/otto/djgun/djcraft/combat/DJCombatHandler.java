package otto.djgun.djcraft.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.session.DJModeManager;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 DJ 攻击事件处理器
 *
 * 架构："客户端判定，服务端信任"
 * - 客户端使用 OpenAL 精确时钟做节拍判定，并通过 DJAttackClientPayload 发送结果到服务端
 * - 服务端不重复判定，而是接收 pending 结果，只负责：
 * Miss=取消本次攻击、Hit=清零无敌时间并按 damageRate 修改伤害
 *
 * 注册方式：在 DJCraft.java 构造函数中：NeoForge.EVENT_BUS.register(DJCombatHandler.class)
 */
public class DJCombatHandler {

    /**
     * 客户端判定结果 pending map
     * key = 攻击玩家 UUID，value = 从客户端收到的 HitResult
     * 由 receivePendingJudgment() 写入，由 onLivingIncomingDamage() 读取并清除
     */
    private static final Map<UUID, HitResult> pendingJudgments = new ConcurrentHashMap<>();

    /**
     * 存储命中结果，供 LivingDamageEvent 读取以修改伤害量
     * key = 被攻击实体 UUID
     */
    private static final Map<UUID, HitResult> pendingHitResults = new ConcurrentHashMap<>();

    /**
     * 用于标记哪些实体本轮是 DJ 命中，供 Post 事件清零无敌时间
     */
    private static final Set<UUID> djHitEntities = ConcurrentHashMap.newKeySet();

    /**
     * 由网络层调用：将客户端发来的判定结果存入 pending map
     */
    public static void receivePendingJudgment(UUID playerUUID, HitResult result) {
        pendingJudgments.put(playerUUID, result);
    }

    /**
     * 第一步：在无敌时间判定前拦截。
     * 读取客户端判定结果（pendingJudgments），不再服务端重新判定。
     * - Miss → 取消事件（伤害直接归 0）
     * - Hit → 清零目标无敌时间，暂存判定结果供 Pre 事件使用
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        Player attacker = getPlayerAttacker(event);
        if (attacker == null)
            return;

        // 只处理 DJ 模式中的玩家
        var optSession = DJModeManager.getInstance().getSession(attacker);
        if (optSession.isEmpty() || !optSession.get().isPlaying())
            return;

        // 读取客户端判定结果（如果没有收到包，视为 Miss）
        HitResult result = pendingJudgments.remove(attacker.getUUID());
        if (result == null) {
            // 没有对应的客户端判定包到达（理论上不应发生，但防御性处理）
            DJCraft.LOGGER.debug("DJ: no pending judgment for {}, treating as MISS",
                    attacker.getName().getString());
            event.setCanceled(true);
            return;
        }

        if (!result.isHit()) {
            // Miss：取消此次攻击
            event.setCanceled(true);
            DJCraft.LOGGER.debug("DJ MISS: attack canceled for {}", attacker.getName().getString());
        } else {
            // Hit：清零无敌时间，暂存判定结果供伤害修改
            target.invulnerableTime = 0;
            pendingHitResults.put(target.getUUID(), result);
            djHitEntities.add(target.getUUID());
            DJCraft.LOGGER.debug("DJ HIT: damageRate={} for {}", result.damageModifier(),
                    attacker.getName().getString());
        }
    }

    /**
     * 第二步：在伤害实际计算时，乘以节拍的 damageRate
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        HitResult result = pendingHitResults.remove(target.getUUID());
        if (result == null)
            return; // 不是 DJ 攻击

        // 应用 damage_rate
        float modified = event.getNewDamage() * result.damageModifier();
        event.setNewDamage(modified);
    }

    /**
     * 第三步：伤害结算后清零无敌时间，使 DJ 攻击不会触发 0.5s 硬直
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        UUID targetUUID = event.getEntity().getUUID();
        if (djHitEntities.remove(targetUUID)) {
            // 清零无敌时间，使 DJ 攻击可以连续造成伤害
            event.getEntity().invulnerableTime = 0;
        }
    }

    /**
     * 从伤害来源中提取玩家攻击者
     */
    private static Player getPlayerAttacker(LivingIncomingDamageEvent event) {
        var source = event.getSource();
        if (source.getEntity() instanceof Player player) {
            return player;
        }
        return null;
    }
}

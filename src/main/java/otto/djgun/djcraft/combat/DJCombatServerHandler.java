package otto.djgun.djcraft.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.network.packet.DJAttackClientPayload;
import otto.djgun.djcraft.session.DJModeManager;

import java.util.List;

/**
 * 服务端 DJ 攻击处理
 * 接收客户端的判定结果，执行实际伤害
 */
public class DJCombatServerHandler {

    // 光线追踪攻击范围 (单位：方块)
    private static final double REACH = 4.5;

    public static void handleAttackPacket(ServerPlayer player, DJAttackClientPayload payload) {
        // 简单合法性校验：玩家必须处于活跃 DJ 会话中
        var optSession = DJModeManager.getInstance().getSession(player);
        if (optSession.isEmpty() || !optSession.get().isPlaying()) {
            DJCraft.LOGGER.warn("DJ attack packet from {} but no active session!", player.getName().getString());
            return;
        }

        if (payload.isHit()) {
            float damageMultiplier = payload.damageModifier();
            DJCraft.LOGGER.debug("DJ HIT from {}, damageMultiplier={}", player.getName().getString(), damageMultiplier);
            performAttack(player, damageMultiplier);
        } else {
            DJCraft.LOGGER.debug("DJ MISS from {}", player.getName().getString());
            // MISS: damageMultiplier = 0，依然执行 performAttack 但伤害为 0
            // 这样可以触发动画，但不产生伤害效果
            performAttack(player, 0f);
        }
    }

    /**
     * 在服务端执行光线追踪 + 攻击逻辑
     */
    private static void performAttack(ServerPlayer player, float damageMultiplier) {
        // 找到玩家视线方向上最近的可攻击实体
        LivingEntity target = findAttackTarget(player);
        if (target == null)
            return;

        // 获取玩家手持物品基础伤害
        float baseDamage = (float) player
                .getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);

        float finalDamage = baseDamage * damageMultiplier;

        // 如果伤害为 0（Miss），直接返回，不进行任何击中效果
        if (finalDamage <= 0) {
            DJCraft.LOGGER.debug("DJ MISS: no damage dealt to {}", target.getName().getString());
            return;
        }

        // 临时保存、清除目标无敌时间（"DJ 模式下不触发 0.5s 硬直无敌"）
        int savedInvulnerableTime = target.invulnerableTime;
        target.invulnerableTime = 0;

        // 使用玩家攻击伤害源
        DamageSource src = player.damageSources().playerAttack(player);
        boolean hurt = target.hurt(src, finalDamage);

        // 恢复：如果这次 DJ 伤害成功造成了伤害，把无敌时间设为 0，实现无限连击
        // 如果没有造成成功（hurt 为 false），恢复原值避免 bug
        if (hurt) {
            // DJ 模式命中后不添加无敌时间
            target.invulnerableTime = 0;
            DJCraft.LOGGER.debug("DJ HIT: dealt {} damage to {}", finalDamage, target.getName().getString());
        } else {
            target.invulnerableTime = savedInvulnerableTime;
        }
    }

    /**
     * 光线追踪寻找攻击目标
     */
    private static LivingEntity findAttackTarget(ServerPlayer player) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(REACH));

        // 在视线可能碰触到的 AABB 范围内查找所有生物
        AABB searchBox = new AABB(eyePos, endPos).inflate(1.0);
        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != player && e.isAlive() && e.isPickable());

        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (LivingEntity entity : entities) {
            // 用实体 AABB 检查视线是否穿过
            var hitResult = entity.getBoundingBox().inflate(0.3).clip(eyePos, endPos);
            if (hitResult.isPresent()) {
                double dist = eyePos.distanceTo(hitResult.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = entity;
                }
            }
        }
        return closest;
    }
}

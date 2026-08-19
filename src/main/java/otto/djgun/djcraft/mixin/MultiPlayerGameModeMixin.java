package otto.djgun.djcraft.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.combat.client.BeatJudgeFacade;
import otto.djgun.djcraft.combat.HitResult;
import otto.djgun.djcraft.combat.client.DJClientJudgmentProofs;
import otto.djgun.djcraft.combat.client.DJChargeReleaseState;
import otto.djgun.djcraft.network.packet.DJChargeReleasePayload;
import otto.djgun.djcraft.network.packet.DJJudgmentProof;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.session.DJSessionClient;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;
import otto.djgun.djcraft.combat.DJActionSource;

/**
 * 客户端 MultiPlayerGameMode Mixin。
 *
 * <p>
 * 注入 {@code releaseUsingItem} 的 HEAD——此时原版 {@code RELEASE_USE_ITEM} 数据包
 * 尚未发出。我们在这里抢先完成节拍判定并发送 {@link DJChargeReleasePayload}，
 * 确保服务端在处理 {@code RELEASE_USE_ITEM} 之前已拿到判定结果。
 *
 * <p>
 * 同时将结果存入 {@link DJChargeReleaseState}，供随后触发的
 * {@code BowItemMixin} 直接读取，无需二次判定。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "releaseUsingItem", at = @At("HEAD"), cancellable = true)
    private void djChargeWeaponPreRelease(Player player, CallbackInfo ci) {
        // 只处理蓄力型武器
        var useItem = player.getUseItem();
        if (useItem.isEmpty())
            return;
        if (!DJItemBehaviorManager.resolve(useItem).isCharge())
            return;

        if (otto.djgun.djcraft.client.DJNetworkGroupClient.getInstance().shouldSuppressVanillaCombat()) {
            DJChargeReleaseState.clear();
            ci.cancel();
            return;
        }

        // 只在 DJ 活跃会话中处理（合并 isInDJMode + session + isPlaying 三重判定）
        DJSessionClient session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (session == null)
            return;

        // 节拍判定 + 准星视觉反馈（统一门面）
        HitResult result = BeatJudgeFacade.judgeAttackAndNotify(session, useItem);

        // 将结构化结果存入客户端状态，供 BowItemMixin 读取
        // 【关键】先于原版 RELEASE_USE_ITEM 包发送判定结果到服务端
        // TCP 同一连接内的包严格按发送顺序到达，保证服务端处理 releaseUsing 时缓存已就绪
        DJJudgmentProof proof = DJClientJudgmentProofs.create(session, result);
        DJChargeReleaseState.store(result, proof.actionSequence(), player.getUsedItemHand(),
                player.level().getGameTime(), useItem.getItem());
        PacketDistributor.sendToServer(new DJChargeReleasePayload(proof, player.getUsedItemHand(),
                DJActionSource.capture(player, player.getUsedItemHand(), useItem)));
    }
}

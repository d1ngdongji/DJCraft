package otto.djgun.djcraft.event;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import otto.djgun.djcraft.data.BeatDefinition;
import otto.djgun.djcraft.data.BeatEvent;

/**
 * 节拍事件
 * 当播放经过一个节拍点时触发
 */
public class OnBeatEvent extends Event {

    private final Player player;
    private final BeatEvent beat;
    private final BeatDefinition definition;
    private final long exactTimeMs;
    private final int beatIndex;

    public OnBeatEvent(Player player, BeatEvent beat, BeatDefinition definition, long exactTimeMs, int beatIndex) {
        this.player = player;
        this.beat = beat;
        this.definition = definition;
        this.exactTimeMs = exactTimeMs;
        this.beatIndex = beatIndex;
    }

    /**
     * 获取触发事件的玩家
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * 获取节拍事件数据
     */
    public BeatEvent getBeat() {
        return beat;
    }

    /**
     * 获取节拍定义
     */
    public BeatDefinition getDefinition() {
        return definition;
    }

    /**
     * 获取事件触发时的精确时间（毫秒）
     */
    public long getExactTimeMs() {
        return exactTimeMs;
    }

    /**
     * 获取节拍在时间线中的索引
     */
    public int getBeatIndex() {
        return beatIndex;
    }

    /**
     * 节拍是否可以攻击
     */
    public boolean canAttack() {
        return definition != null && definition.canAttack();
    }

    /**
     * 获取伤害倍率
     */
    public float getDamageRate() {
        return definition != null ? definition.damageRate() : 1.0f;
    }
}

package otto.djgun.djcraft.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.loader.TrackPackManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户端曲目注册表
 * 存储服务端下发的哈希表，并与本地哈希做交集，得到双端共同拥有的曲目列表。
 * 只有在此列表中的曲目才允许播放。
 */
@OnlyIn(Dist.CLIENT)
public class ClientTrackRegistry {

    private static ClientTrackRegistry INSTANCE;

    /** 服务端的 packId -> hash */
    private Map<String, String> serverHashes = Collections.emptyMap();

    /** 已验证的 packId 集合（本地 hash == 服务端 hash） */
    private Set<String> verifiedPackIds = Collections.emptySet();

    private ClientTrackRegistry() {
    }

    public static ClientTrackRegistry getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ClientTrackRegistry();
        }
        return INSTANCE;
    }

    /**
     * 收到服务端哈希表时调用，重新计算验证列表。
     */
    public void onReceiveServerHashes(Map<String, String> serverHashes) {
        this.serverHashes = new HashMap<>(serverHashes);

        Map<String, String> localHashes = TrackPackManager.getInstance().getPackHashes();

        // 交集：packId 相同且 hash 相同
        this.verifiedPackIds = serverHashes.entrySet().stream()
                .filter(e -> e.getValue().equals(localHashes.get(e.getKey())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());

        DJCraft.LOGGER.info("TrackPack verification: server={} local={} verified={}",
                serverHashes.size(), localHashes.size(), verifiedPackIds.size());

        if (!verifiedPackIds.isEmpty()) {
            DJCraft.LOGGER.info("Verified packs: {}", verifiedPackIds);
        }

        // 找出服务端有但本地 hash 不匹配（或本地根本没有）的包，给出警告
        for (Map.Entry<String, String> entry : serverHashes.entrySet()) {
            if (!verifiedPackIds.contains(entry.getKey())) {
                String localHash = localHashes.get(entry.getKey());
                if (localHash == null) {
                    DJCraft.LOGGER.warn("TrackPack '{}' exists on server but not locally.", entry.getKey());
                } else {
                    DJCraft.LOGGER.warn("TrackPack '{}' hash mismatch: server={} local={}",
                            entry.getKey(), entry.getValue().substring(0, 8), localHash.substring(0, 8));
                }
            }
        }
    }

    /**
     * 当客户端重新加载本地曲目包后，重新计算验证列表。
     */
    public void revalidate() {
        if (!serverHashes.isEmpty()) {
            onReceiveServerHashes(serverHashes);
        }
    }

    /**
     * 判断指定曲目包是否已通过双端哈希校验
     */
    public boolean isVerified(String packId) {
        return verifiedPackIds.contains(packId);
    }

    /**
     * 获取所有已验证的曲目包ID
     */
    public Set<String> getVerifiedPackIds() {
        return verifiedPackIds;
    }

    /**
     * 清除状态（断开连接时调用）
     */
    public void clear() {
        serverHashes = Collections.emptyMap();
        verifiedPackIds = Collections.emptySet();
    }
}

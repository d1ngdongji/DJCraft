package otto.djgun.djcraft.util;

import otto.djgun.djcraft.data.BeatEvent;

import java.util.List;

/**
 * 节拍网格计算工具类
 */
public class BeatGridUtil {

    private static final long DEFAULT_BEAT_INTERVAL_MS = 500; // 120 BPM fallback

    /**
     * 计算经过指定节拍数后的目标时间戳
     * 
     * @param currentTimeMs 当前时间（相对于曲目开始）
     * @param beats         节拍列表（必须按时间排序）
     * @param beatsToWait   需要等待的节拍数（可以是小数）
     * @return 目标时间戳（相对于曲目开始）
     */
    public static long calculateTargetTime(long currentTimeMs, List<BeatEvent> beats, double beatsToWait) {
        if (beats == null || beats.isEmpty()) {
            return currentTimeMs + (long) (beatsToWait * DEFAULT_BEAT_INTERVAL_MS);
        }
        double currentVirtualBeat = getVirtualBeat(currentTimeMs, beats);
        return getTimeAtVirtualBeat(currentVirtualBeat + beatsToWait, beats);
    }

    /**
     * 获取当前时间对应的虚拟节拍索引（带小数部分，代表在两个节拍间的进度）
     */
    public static double getVirtualBeat(long currentTimeMs, List<BeatEvent> beats) {
        if (beats == null || beats.isEmpty()) {
            return (double) currentTimeMs / DEFAULT_BEAT_INTERVAL_MS;
        }

        // 1. 定位当前在哪个节拍区间
        // prevIndex 是满足 beats[i].t() <= currentTimeMs 的最大索引
        int prevIndex = -1;
        int low = 0;
        int high = beats.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = beats.get(mid).t();

            if (midVal < currentTimeMs) {
                prevIndex = mid;
                low = mid + 1;
            } else if (midVal > currentTimeMs) {
                high = mid - 1;
            } else {
                prevIndex = mid;
                break; // Exact match
            }
        }

        long interval;
        if (prevIndex < 0) {
            // 在第一个节拍之前
            long firstBeatTime = beats.get(0).t();
            if (beats.size() > 1) {
                interval = beats.get(1).t() - firstBeatTime;
            } else {
                interval = DEFAULT_BEAT_INTERVAL_MS;
            }
            if (interval <= 0)
                interval = DEFAULT_BEAT_INTERVAL_MS;
            return (double) (currentTimeMs - firstBeatTime) / interval;
        } else if (prevIndex >= beats.size() - 1) {
            // 在最后一个节拍之后 (或正好是最后一个)
            long lastBeatTime = beats.get(beats.size() - 1).t();
            if (beats.size() > 1) {
                interval = lastBeatTime - beats.get(beats.size() - 2).t();
            } else {
                interval = DEFAULT_BEAT_INTERVAL_MS;
            }
            if (interval <= 0)
                interval = DEFAULT_BEAT_INTERVAL_MS;
            return (beats.size() - 1) + (double) (currentTimeMs - lastBeatTime) / interval;
        } else {
            // 在两个节拍之间
            long prevTime = beats.get(prevIndex).t();
            long nextTime = beats.get(prevIndex + 1).t();
            interval = nextTime - prevTime;
            if (interval <= 0)
                interval = 1;
            return prevIndex + (double) (currentTimeMs - prevTime) / interval;
        }
    }

    /**
     * 将虚拟节拍索引转换回实际时间戳
     */
    public static long getTimeAtVirtualBeat(double virtualBeat, List<BeatEvent> beats) {
        if (beats == null || beats.isEmpty()) {
            return (long) (virtualBeat * DEFAULT_BEAT_INTERVAL_MS);
        }

        int targetIndex = (int) Math.floor(virtualBeat);
        double targetProgress = virtualBeat - targetIndex;
        long interval;

        if (targetIndex < 0) {
            // 目标在第一个节拍之前
            long firstBeatTime = beats.get(0).t();
            if (beats.size() > 1) {
                interval = beats.get(1).t() - firstBeatTime;
            } else {
                interval = DEFAULT_BEAT_INTERVAL_MS;
            }
            if (interval <= 0)
                interval = DEFAULT_BEAT_INTERVAL_MS;
            return firstBeatTime + (long) (virtualBeat * interval);
        } else if (targetIndex >= beats.size() - 1) {
            // 目标超出列表范围，外推
            long lastBeatTime = beats.get(beats.size() - 1).t();
            if (beats.size() > 1) {
                interval = lastBeatTime - beats.get(beats.size() - 2).t();
            } else {
                interval = DEFAULT_BEAT_INTERVAL_MS;
            }
            if (interval <= 0)
                interval = DEFAULT_BEAT_INTERVAL_MS;
            return lastBeatTime + (long) ((virtualBeat - (beats.size() - 1)) * interval);
        } else {
            // 目标在两个节拍之间
            long tPrevTime = beats.get(targetIndex).t();
            long tNextTime = beats.get(targetIndex + 1).t();
            interval = tNextTime - tPrevTime;
            if (interval <= 0)
                interval = 1;
            return tPrevTime + (long) (targetProgress * interval);
        }
    }
}

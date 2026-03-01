package otto.djgun.djcraft.loader;

import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.TrackPack;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

/**
 * 曲目包管理器
 * 负责扫描、加载和缓存所有曲目包
 */
public class TrackPackManager {

    // --- 音频来源描述（sealed 接口区分目录包 vs 压缩包） ---

    /** 音频来源：目录包，直接持有 ogg 文件路径 */
    public record DirAudioSource(Path oggPath) {
    }

    /** 音频来源：压缩包，持有 .djcraft 文件路径及包内 ogg 文件名 */
    public record ArchiveAudioSource(Path archivePath, String entryName) {
    }

    // -------------------------------------------------------

    private static TrackPackManager INSTANCE;

    private final Map<String, TrackPack> loadedPacks = new HashMap<>();
    /** packId -> 该 pack 的 track.json 的 SHA-256 十六进制 hash */
    private final Map<String, String> packHashes = new HashMap<>();
    /** packId -> 音频来源（DirAudioSource 或 ArchiveAudioSource） */
    private final Map<String, Object> audioSources = new HashMap<>();
    private Path trackpacksDir;

    private TrackPackManager() {
    }

    /**
     * 获取单例实例
     */
    public static TrackPackManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TrackPackManager();
        }
        return INSTANCE;
    }

    /**
     * 初始化管理器并加载所有曲目包
     * 
     * @param gameDir 游戏运行目录
     */
    public void initialize(Path gameDir) {
        this.trackpacksDir = gameDir.resolve("djcraft").resolve("trackpacks");

        // 确保目录存在
        try {
            Files.createDirectories(trackpacksDir);
            DJCraft.LOGGER.info("TrackPacks directory: {}", trackpacksDir.toAbsolutePath());
        } catch (IOException e) {
            DJCraft.LOGGER.error("Failed to create trackpacks directory", e);
            return;
        }

        // 加载所有曲目包
        loadAllPacks();
    }

    /**
     * 扫描并加载所有曲目包
     */
    public void loadAllPacks() {
        loadedPacks.clear();
        packHashes.clear();
        audioSources.clear();

        if (trackpacksDir == null || !Files.exists(trackpacksDir)) {
            DJCraft.LOGGER.warn("TrackPacks directory does not exist");
            return;
        }

        DJCraft.LOGGER.info("Scanning for TrackPacks in: {}", trackpacksDir);

        try (Stream<Path> paths = Files.list(trackpacksDir)) {
            paths.forEach(path -> {
                if (Files.isDirectory(path)) {
                    loadPackFromDirectory(path);
                } else if (isDjcraftFile(path)) {
                    loadPackFromArchive(path);
                }
            });
        } catch (IOException e) {
            DJCraft.LOGGER.error("Failed to scan trackpacks directory", e);
        }

        DJCraft.LOGGER.info("Loaded {} TrackPack(s)", loadedPacks.size());
    }

    /**
     * 判断路径是否为 .djcraft 文件（不区分大小写）
     */
    private static boolean isDjcraftFile(Path path) {
        if (!Files.isRegularFile(path))
            return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".djcraft");
    }

    /**
     * 从目录加载单个曲目包
     */
    private void loadPackFromDirectory(Path packDir) {
        String packId = packDir.getFileName().toString().toLowerCase(Locale.ROOT);
        Path jsonPath = packDir.resolve("track.json");

        if (!Files.exists(jsonPath)) {
            DJCraft.LOGGER.warn("TrackPack {} missing track.json", packId);
            return;
        }

        // 计算 JSON 文件的 SHA-256 哈希（用于双端校验）
        String hash = computeSha256(jsonPath);
        if (hash == null) {
            DJCraft.LOGGER.error("Failed to compute hash for TrackPack: {}", packId);
            return;
        }

        TrackPack pack = TrackPackLoader.loadFromFile(packId, jsonPath);
        if (pack != null) {
            loadedPacks.put(packId, pack);
            packHashes.put(packId, hash);
            // 记录音频来源：目录包，直接存 ogg 路径
            String soundFile = pack.meta().soundFile();
            if (soundFile == null || soundFile.isEmpty())
                soundFile = "track.ogg";
            audioSources.put(packId, new DirAudioSource(packDir.resolve(soundFile)));
            DJCraft.LOGGER.info("Loaded TrackPack: {} (BPM: {}, Beats: {}, Hash: {})",
                    packId, pack.getBpm(), pack.getCombatBeatCount(), hash.substring(0, 8));
        }
    }

    /**
     * 从 .djcraft 压缩包加载单个曲目包
     * 压缩包内须包含 track.json
     */
    private void loadPackFromArchive(Path archivePath) {
        String fileName = archivePath.getFileName().toString();
        // 去掉 .djcraft 后缀作为 packId
        String packId = fileName.substring(0, fileName.length() - ".djcraft".length()).toLowerCase(Locale.ROOT);

        URI zipUri = URI.create("jar:" + archivePath.toUri());
        try (FileSystem zipFs = FileSystems.newFileSystem(zipUri, Map.of())) {
            Path jsonPath = zipFs.getPath("track.json");

            if (!Files.exists(jsonPath)) {
                DJCraft.LOGGER.warn("TrackPack archive {} is missing track.json", fileName);
                return;
            }

            // 读取原始字节用于 hash
            byte[] jsonBytes = Files.readAllBytes(jsonPath);
            String hash = computeSha256(jsonBytes);
            if (hash == null) {
                DJCraft.LOGGER.error("Failed to compute hash for TrackPack archive: {}", packId);
                return;
            }

            // 解析曲目包
            try (Reader reader = new InputStreamReader(
                    Files.newInputStream(jsonPath), StandardCharsets.UTF_8)) {
                TrackPack pack = TrackPackLoader.loadFromReader(packId, reader);
                if (pack != null) {
                    loadedPacks.put(packId, pack);
                    packHashes.put(packId, hash);
                    // 记录音频来源：压缩包，存档案路径 + 内部 ogg 文件名
                    String soundFile = pack.meta().soundFile();
                    if (soundFile == null || soundFile.isEmpty())
                        soundFile = "track.ogg";
                    audioSources.put(packId, new ArchiveAudioSource(archivePath, soundFile));
                    DJCraft.LOGGER.info("Loaded TrackPack (archive): {} (BPM: {}, Beats: {}, Hash: {})",
                            packId, pack.getBpm(), pack.getCombatBeatCount(), hash.substring(0, 8));
                }
            }

        } catch (IOException e) {
            DJCraft.LOGGER.error("Failed to read TrackPack archive: {}", archivePath, e);
        }
    }

    /**
     * 计算文件的 SHA-256 十六进制哈希
     */
    private static String computeSha256(Path file) {
        try {
            return computeSha256(Files.readAllBytes(file));
        } catch (Exception e) {
            DJCraft.LOGGER.error("Failed to compute SHA-256 for {}", file, e);
            return null;
        }
    }

    /**
     * 计算字节数组的 SHA-256 十六进制哈希
     */
    private static String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            DJCraft.LOGGER.error("Failed to compute SHA-256", e);
            return null;
        }
    }

    /**
     * 重新加载所有曲目包
     */
    public void reloadAllPacks() {
        DJCraft.LOGGER.info("Reloading all TrackPacks...");
        loadAllPacks();
    }

    /**
     * 重新加载指定曲目包
     */
    public boolean reloadPack(String packId) {
        Path packDir = trackpacksDir.resolve(packId);
        if (!Files.exists(packDir)) {
            return false;
        }

        loadedPacks.remove(packId);
        loadPackFromDirectory(packDir);
        return loadedPacks.containsKey(packId);
    }

    /**
     * 获取指定曲目包
     */
    public Optional<TrackPack> getTrackPack(String id) {
        return Optional.ofNullable(loadedPacks.get(id));
    }

    /**
     * 打开指定曲目包的音频流
     * 自动处理目录包和压缩包两种情况
     *
     * @param packId 曲目包ID（小写）
     * @return 音频 InputStream，找不到时返回 null
     */
    public InputStream openAudioStream(String packId) throws IOException {
        Object source = audioSources.get(packId);
        if (source == null) {
            DJCraft.LOGGER.warn("No audio source registered for pack: {}", packId);
            return null;
        }

        if (source instanceof DirAudioSource dir) {
            if (!Files.exists(dir.oggPath())) {
                DJCraft.LOGGER.warn("Audio file not found for pack {}: {}", packId, dir.oggPath());
                return null;
            }
            return Files.newInputStream(dir.oggPath());
        }

        if (source instanceof ArchiveAudioSource arc) {
            URI zipUri = URI.create("jar:" + arc.archivePath().toUri());
            // ZipFileSystem 在流被关闭时不应关闭，因此我们读完整字节后返回 ByteArrayInputStream
            try (FileSystem zipFs = FileSystems.newFileSystem(zipUri, Map.of())) {
                Path entry = zipFs.getPath(arc.entryName());
                if (!Files.exists(entry)) {
                    DJCraft.LOGGER.warn("Audio entry '{}' not found in archive: {}",
                            arc.entryName(), arc.archivePath());
                    return null;
                }
                // 读入内存，避免 ZipFileSystem 关闭后流失效
                byte[] bytes = Files.readAllBytes(entry);
                return new java.io.ByteArrayInputStream(bytes);
            }
        }

        DJCraft.LOGGER.error("Unknown audio source type for pack: {}", packId);
        return null;
    }

    /**
     * 获取所有已加载的曲目包
     */
    public Collection<TrackPack> getLoadedPacks() {
        return Collections.unmodifiableCollection(loadedPacks.values());
    }

    /**
     * 获取所有已加载的曲目包ID
     */
    public Set<String> getLoadedPackIds() {
        return Collections.unmodifiableSet(loadedPacks.keySet());
    }

    /**
     * 检查曲目包是否已加载
     */
    public boolean isPackLoaded(String id) {
        return loadedPacks.containsKey(id);
    }

    /**
     * 获取已加载曲目包数量
     */
    public int getLoadedPackCount() {
        return loadedPacks.size();
    }

    /**
     * 获取指定曲目包的 SHA-256 哈希
     */
    public Optional<String> getPackHash(String id) {
        return Optional.ofNullable(packHashes.get(id));
    }

    /**
     * 获取全部 packId -> hash 映射（用于网络同步）
     */
    public Map<String, String> getPackHashes() {
        return Collections.unmodifiableMap(packHashes);
    }
}

package otto.djgun.djcraft.loader;

import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.Config;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.sound.TrackPackDiscModelIndex;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 曲目包管理器
 * 负责扫描、加载和缓存所有曲目包
 */
public class TrackPackManager {

    // --- 音频来源描述（sealed 接口区分目录包 vs 压缩包） ---

    /** 音频来源：目录包，直接持有 ogg 文件路径 */
    public record DirAudioSource(Path rootPath, Path oggPath) {
    }

    /** 音频来源：压缩包，持有 .djcraft 文件路径及包内 ogg 文件名 */
    public record ArchiveAudioSource(Path archivePath, String entryName) {
    }

    /** Read-only archive stored inside the mod JAR; it is never offered for download. */
    public record BundledArchiveAudioSource(Path archivePath, String entryName) {
    }

    // -------------------------------------------------------

    private static TrackPackManager INSTANCE;

    private final Map<String, TrackPack> loadedPacks = new ConcurrentHashMap<>();
    /** packId -> 该 pack 的 track.json 的 SHA-256 十六进制 hash */
    private final Map<String, String> packHashes = new ConcurrentHashMap<>();
    private final Map<String, String> contentHashes = new ConcurrentHashMap<>();
    private final Map<String, String> archiveHashes = new ConcurrentHashMap<>();
    /** packId -> 音频来源（DirAudioSource 或 ArchiveAudioSource） */
    private final Map<String, Object> audioSources = new ConcurrentHashMap<>();
    /** Immutable lookup used by item predicates; rebuilt only when the pack registry changes. */
    private volatile Map<String, Float> discModelIndexes = Map.of();
    private volatile Map<String, Integer> combatBeatCounts = Map.of();
    /** ZipFS caches file systems by URI, so an archive may only be opened by one thread at a time. */
    private final Object archiveFileSystemLock = new Object();
    private Path trackpacksDir;
    private Path bundledTrackpacksDir;

    TrackPackManager() {
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
        initialize(gameDir, null);
    }

    /**
     * Initializes the external TrackPack directory and an optional read-only
     * TrackPack directory bundled inside the mod file.
     */
    public void initialize(Path gameDir, Path bundledTrackpacksDir) {
        this.trackpacksDir = gameDir.resolve("djcraft").resolve("trackpacks");
        this.bundledTrackpacksDir = bundledTrackpacksDir;

        // 确保目录存在
        try {
            Files.createDirectories(trackpacksDir);
            DJCraft.LOGGER.info("TrackPacks directory: {}", trackpacksDir.toAbsolutePath());
        } catch (IOException e) {
            DJCraft.LOGGER.error("Failed to create trackpacks directory", e);
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
        contentHashes.clear();
        archiveHashes.clear();
        audioSources.clear();

        loadBundledPacks();
        if (trackpacksDir == null || !Files.exists(trackpacksDir)) {
            DJCraft.LOGGER.warn("TrackPacks directory does not exist");
            rebuildItemRenderMetadata();
            DJCraft.LOGGER.info("Loaded {} TrackPack(s)", loadedPacks.size());
            return;
        }

        DJCraft.LOGGER.info("Scanning for TrackPacks in: {}", trackpacksDir);

        try (Stream<Path> paths = Files.list(trackpacksDir)) {
            List<Path> allPaths = paths.sorted().toList();
            allPaths.stream().filter(TrackPackManager::isDjcraftFile).forEach(this::loadPackFromArchive);
            allPaths.stream().filter(Files::isDirectory)
                    .forEach(path -> loadPackFromDirectory(path, "external directory"));
        } catch (IOException e) {
            DJCraft.LOGGER.error("Failed to scan trackpacks directory", e);
        }

        rebuildItemRenderMetadata();
        DJCraft.LOGGER.info("Loaded {} TrackPack(s)", loadedPacks.size());
    }

    private void loadBundledPacks() {
        if (bundledTrackpacksDir == null || !Files.isDirectory(bundledTrackpacksDir)) {
            DJCraft.LOGGER.info("No bundled TrackPacks directory is present");
            return;
        }
        DJCraft.LOGGER.info("Scanning for bundled TrackPacks in: {}", bundledTrackpacksDir);
        try (Stream<Path> paths = Files.list(bundledTrackpacksDir)) {
            List<Path> allPaths = paths.sorted().toList();
            allPaths.stream().filter(TrackPackManager::isDjcraftFile)
                    .forEach(this::loadPackFromBundledArchive);
            allPaths.stream().filter(Files::isDirectory)
                    .forEach(path -> loadPackFromDirectory(path, "bundled directory"));
        } catch (IOException e) {
            DJCraft.LOGGER.error("Failed to scan bundled TrackPacks directory", e);
        }
    }

    private void loadPackFromBundledArchive(Path archivePath) {
        String fileName = archivePath.getFileName().toString();
        String packId = fileName.substring(0, fileName.length() - ".djcraft".length())
                .toLowerCase(Locale.ROOT);
        if (!TrackPackIdValidator.isValid(packId)) {
            DJCraft.LOGGER.error("Invalid bundled TrackPack ID derived from archive: {}", packId);
            return;
        }

        long maxBytes = Config.maxTrackPackBytes();
        try {
            if (!TrackPackArchiveValidator.validate(archivePath, maxBytes)) {
                DJCraft.LOGGER.error("Bundled TrackPack archive failed safety validation: {}", archivePath);
                return;
            }
            Map<String, byte[]> entries = readArchiveEntries(archivePath, maxBytes);
            byte[] jsonBytes = entries.get("track.json");
            TrackPack pack;
            try (Reader reader = new InputStreamReader(new ByteArrayInputStream(jsonBytes), StandardCharsets.UTF_8)) {
                pack = TrackPackLoader.loadFromReader(packId, reader);
            }
            if (pack == null) {
                return;
            }

            String soundFile = pack.meta().soundFile();
            if (soundFile == null || soundFile.isEmpty()) {
                soundFile = "track.ogg";
            }
            if (!isSafeRelativePath(soundFile)) {
                DJCraft.LOGGER.error("Bundled TrackPack {} contains an unsafe sound path: {}", packId, soundFile);
                return;
            }
            byte[] audio = entries.get(soundFile);
            if (audio == null || !TrackPackAudioValidator.isPlayableOggVorbis(new ByteArrayInputStream(audio))) {
                DJCraft.LOGGER.error("Bundled TrackPack {} audio is missing or not supported: {}", packId, soundFile);
                return;
            }

            String definitionHash = computeSha256(jsonBytes);
            String contentHash = computeCanonicalContentHash(entries);
            if (definitionHash == null || contentHash == null) {
                return;
            }
            loadedPacks.put(packId, pack);
            packHashes.put(packId, definitionHash);
            contentHashes.put(packId, contentHash);
            audioSources.put(packId, new BundledArchiveAudioSource(archivePath, soundFile));
            DJCraft.LOGGER.info("Loaded bundled TrackPack archive: {} (BPM: {}, Beats: {}, Hash: {})",
                    packId, pack.getBpm(), pack.getCombatBeatCount(), definitionHash.substring(0, 8));
        } catch (IOException | RuntimeException exception) {
            DJCraft.LOGGER.error("Failed to read bundled TrackPack archive: {}", archivePath, exception);
        }
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
    private void loadPackFromDirectory(Path packDir, String sourceDescription) {
        String packId = packDir.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!TrackPackIdValidator.isValid(packId)) {
            DJCraft.LOGGER.error("Invalid TrackPack directory ID: {}", packId);
            return;
        }
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
            String soundFile = pack.meta().soundFile();
            if (soundFile == null || soundFile.isEmpty())
                soundFile = "track.ogg";
            Path audioPath = packDir.resolve(soundFile).normalize();
            if (!audioPath.startsWith(packDir.normalize())) {
                DJCraft.LOGGER.error("TrackPack {} contains an unsafe sound path: {}", packId, soundFile);
                return;
            }
            if (!validateAudio(packId, audioPath)) {
                return;
            }
            String contentHash = computeCanonicalContentHash(packDir);
            if (contentHash == null) {
                DJCraft.LOGGER.error("Failed to compute content hash for TrackPack: {}", packId);
                return;
            }
            // 冲突检测：同 packId 被重复加载时给出明确日志
            if (loadedPacks.containsKey(packId)) {
                DJCraft.LOGGER.warn(
                        "TrackPack ID conflict: '{}' is already loaded and will be overwritten. " +
                        "Previous source will be replaced by {} pack at: {}",
                        packId, sourceDescription, packDir.toAbsolutePath());
            }
            loadedPacks.put(packId, pack);
            packHashes.put(packId, hash);
            contentHashes.put(packId, contentHash);
            // 记录音频来源：目录包，直接存 ogg 路径
            audioSources.put(packId, new DirAudioSource(packDir.normalize(), audioPath));
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

        if (!TrackPackIdValidator.isValid(packId)) {
            DJCraft.LOGGER.error("Invalid TrackPack ID derived from archive: {}", packId);
            return;
        }
        long maxBytes = Config.maxTrackPackBytes();
        try {
            if (!TrackPackArchiveValidator.validate(archivePath, maxBytes)) {
                DJCraft.LOGGER.error("TrackPack archive failed safety validation: {}", archivePath);
                return;
            }
        } catch (IOException e) {
            DJCraft.LOGGER.error("Failed to validate TrackPack archive: {}", archivePath, e);
            return;
        }

        URI zipUri = URI.create("jar:" + archivePath.toUri());
        synchronized (archiveFileSystemLock) {
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
                        String contentHash = computeCanonicalContentHash(zipFs.getPath("/"));
                        if (contentHash == null) {
                            DJCraft.LOGGER.error("Failed to compute content hash for TrackPack archive: {}", packId);
                            return;
                        }
                        // 冲突检测：同 packId 被重复加载时给出明确日志
                        if (loadedPacks.containsKey(packId)) {
                            DJCraft.LOGGER.warn(
                                    "TrackPack ID conflict: '{}' is already loaded and will be overwritten. " +
                                    "Previous source will be replaced by archive pack at: {}",
                                    packId, archivePath.toAbsolutePath());
                        }
                        loadedPacks.put(packId, pack);
                        packHashes.put(packId, hash);
                        contentHashes.put(packId, contentHash);
                        String archiveHash = computeSha256(archivePath);
                        if (archiveHash != null) {
                            archiveHashes.put(packId, archiveHash);
                        }
                        // 记录音频来源：压缩包，存档案路径 + 内部 ogg 文件名
                        String soundFile = pack.meta().soundFile();
                        if (soundFile == null || soundFile.isEmpty())
                            soundFile = "track.ogg";
                        if (!isSafeRelativePath(soundFile)) {
                            DJCraft.LOGGER.error("TrackPack {} contains an unsafe sound path: {}", packId, soundFile);
                            loadedPacks.remove(packId);
                            packHashes.remove(packId);
                            contentHashes.remove(packId);
                            archiveHashes.remove(packId);
                            return;
                        }
                        Path audioEntry = zipFs.getPath("/").resolve(soundFile).normalize();
                        if (!validateAudio(packId, audioEntry)) {
                            loadedPacks.remove(packId);
                            packHashes.remove(packId);
                            contentHashes.remove(packId);
                            archiveHashes.remove(packId);
                            return;
                        }
                        audioSources.put(packId, new ArchiveAudioSource(archivePath, soundFile));
                        DJCraft.LOGGER.info("Loaded TrackPack (archive): {} (BPM: {}, Beats: {}, Hash: {})",
                                packId, pack.getBpm(), pack.getCombatBeatCount(), hash.substring(0, 8));
                    }
                }

            } catch (IOException e) {
                DJCraft.LOGGER.error("Failed to read TrackPack archive: {}", archivePath, e);
            }
        }
    }

    /**
     * 计算文件的 SHA-256 十六进制哈希
     */
    private static String computeSha256(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
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
            return toHex(digest.digest(bytes));
        } catch (Exception e) {
            DJCraft.LOGGER.error("Failed to compute SHA-256", e);
            return null;
        }
    }

    private static String toHex(byte[] hashBytes) {
        StringBuilder sb = new StringBuilder(hashBytes.length * 2);
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
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
        return installPreparedPack(prepareReloadPack(packId));
    }

    /** Performs archive validation, hashing and parsing without touching shared registry state. */
    public PreparedPackReload prepareReloadPack(String packId) {
        if (!TrackPackIdValidator.isValid(packId) || trackpacksDir == null) {
            return PreparedPackReload.invalid(packId);
        }

        TrackPackManager staged = new TrackPackManager();
        staged.trackpacksDir = trackpacksDir;
        staged.bundledTrackpacksDir = bundledTrackpacksDir;
        Path bundledArchive = staged.findArchive(bundledTrackpacksDir, packId).orElse(null);
        if (bundledArchive != null) {
            staged.loadPackFromBundledArchive(bundledArchive);
        }
        Path bundledPackDir = bundledTrackpacksDir == null
                ? null : bundledTrackpacksDir.resolve(packId).normalize();
        if (bundledPackDir != null && bundledPackDir.startsWith(bundledTrackpacksDir.normalize())
                && Files.isDirectory(bundledPackDir)) {
            staged.loadPackFromDirectory(bundledPackDir, "bundled directory");
        }
        Path archive = staged.findArchive(packId).orElse(null);
        if (archive != null) {
            staged.loadPackFromArchive(archive);
        }
        Path packDir = trackpacksDir.resolve(packId).normalize();
        if (packDir.startsWith(trackpacksDir.normalize()) && Files.isDirectory(packDir)) {
            staged.loadPackFromDirectory(packDir, "external directory");
        }
        return new PreparedPackReload(packId, staged.loadedPacks.get(packId), staged.packHashes.get(packId),
                staged.contentHashes.get(packId), staged.archiveHashes.get(packId),
                staged.audioSources.get(packId), true);
    }

    /** Installs a fully parsed pack as one short registry operation on the owning thread. */
    public synchronized boolean installPreparedPack(PreparedPackReload prepared) {
        if (prepared == null || !prepared.valid || !TrackPackIdValidator.isValid(prepared.packId)) {
            return false;
        }

        if (prepared.trackPack == null || prepared.definitionHash == null || prepared.audioSource == null) {
            loadedPacks.remove(prepared.packId);
            packHashes.remove(prepared.packId);
            contentHashes.remove(prepared.packId);
            archiveHashes.remove(prepared.packId);
            audioSources.remove(prepared.packId);
            rebuildItemRenderMetadata();
            return false;
        }

        loadedPacks.put(prepared.packId, prepared.trackPack);
        packHashes.put(prepared.packId, prepared.definitionHash);
        if (prepared.contentHash == null) {
            contentHashes.remove(prepared.packId);
        } else {
            contentHashes.put(prepared.packId, prepared.contentHash);
        }
        audioSources.put(prepared.packId, prepared.audioSource);
        if (prepared.archiveHash == null) {
            archiveHashes.remove(prepared.packId);
        } else {
            archiveHashes.put(prepared.packId, prepared.archiveHash);
        }
        rebuildItemRenderMetadata();
        return true;
    }

    private void rebuildItemRenderMetadata() {
        Set<String> packIds = Set.copyOf(loadedPacks.keySet());
        discModelIndexes = TrackPackDiscModelIndex.build(packIds,
                id -> hasFile(id, "disc.png") || hasFile(id, "perfect_disc.png"));

        Map<String, Integer> beatCounts = new HashMap<>();
        for (Map.Entry<String, TrackPack> entry : loadedPacks.entrySet()) {
            beatCounts.put(entry.getKey(), entry.getValue().getCombatBeatCount());
        }
        combatBeatCounts = Map.copyOf(beatCounts);
    }

    /** Constant-time model predicate lookup; never touches the filesystem. */
    public float getDiscModelIndex(String packId) {
        return TrackPackDiscModelIndex.resolve(packId, discModelIndexes);
    }

    /** Constant-time gilded predicate lookup; never walks a TrackPack timeline. */
    public int getCombatBeatCount(String packId) {
        return packId == null ? 0 : combatBeatCounts.getOrDefault(packId, 0);
    }

    private Optional<Path> findArchive(String packId) {
        return findArchive(trackpacksDir, packId);
    }

    private Optional<Path> findArchive(Path root, String packId) {
        if (root == null || !Files.isDirectory(root)) {
            return Optional.empty();
        }
        try (Stream<Path> paths = Files.list(root)) {
            return paths.filter(TrackPackManager::isDjcraftFile)
                    .filter(path -> path.getFileName().toString()
                            .equalsIgnoreCase(packId + ".djcraft"))
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * 获取指定曲目包
     */
    public synchronized Optional<TrackPack> getTrackPack(String id) {
        return Optional.ofNullable(loadedPacks.get(id));
    }

    /**
     * 获取指定曲目包的压缩文件路径 (仅用于下载 .djcraft)
     */
    public synchronized Optional<Path> getArchiveFilePath(String packId) {
        Object source = audioSources.get(packId);
        if (source instanceof ArchiveAudioSource arc) {
            return Optional.of(arc.archivePath());
        }

        return Optional.empty();
    }

    public synchronized Optional<ArchiveDescriptor> getArchiveDescriptor(String packId) {
        Optional<Path> path = getArchiveFilePath(packId);
        String hash = archiveHashes.get(packId);
        if (path.isEmpty() || hash == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ArchiveDescriptor(path.get(), Files.size(path.get()), hash));
        } catch (IOException e) {
            return Optional.empty();
        }
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
            synchronized (archiveFileSystemLock) {
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
        }

        if (source instanceof BundledArchiveAudioSource arc) {
            byte[] bytes = readArchiveEntry(arc.archivePath(), arc.entryName());
            if (bytes == null) {
                DJCraft.LOGGER.warn("Audio entry '{}' not found in bundled archive: {}",
                        arc.entryName(), arc.archivePath());
                return null;
            }
            return new ByteArrayInputStream(bytes);
        }

        DJCraft.LOGGER.error("Unknown audio source type for pack: {}", packId);
        return null;
    }

    /**
     * 检查曲目包是否存在指定文件
     */
    public boolean hasFile(String packId, String fileName) {
        if (!isSafeRelativePath(fileName))
            return false;
        Object source = audioSources.get(packId);
        if (source == null)
            return false;

        if (source instanceof DirAudioSource dir) {
            Path root = directoryPackRoot(dir);
            if (root == null)
                return false;
            Path file = root.resolve(fileName).normalize();
            if (!file.startsWith(root))
                return false;
            return Files.exists(file);
        }

        if (source instanceof ArchiveAudioSource arc) {
            URI zipUri = URI.create("jar:" + arc.archivePath().toUri());
            synchronized (archiveFileSystemLock) {
                try (FileSystem zipFs = FileSystems.newFileSystem(zipUri, Map.of())) {
                    Path entry = zipFs.getPath(fileName);
                    return Files.exists(entry);
                } catch (IOException e) {
                    return false;
                }
            }
        }
        if (source instanceof BundledArchiveAudioSource arc) {
            try {
                return readArchiveEntry(arc.archivePath(), fileName) != null;
            } catch (IOException exception) {
                return false;
            }
        }
        return false;
    }

    /**
     * 打开指定曲目包内的任意文件流
     */
    public InputStream openFileStream(String packId, String fileName) throws IOException {
        if (!isSafeRelativePath(fileName))
            return null;
        Object source = audioSources.get(packId);
        if (source == null)
            return null;

        if (source instanceof DirAudioSource dir) {
            Path root = directoryPackRoot(dir);
            if (root == null)
                return null;
            Path file = root.resolve(fileName).normalize();
            if (!file.startsWith(root))
                return null;
            if (!Files.exists(file))
                return null;
            return Files.newInputStream(file);
        }

        if (source instanceof ArchiveAudioSource arc) {
            URI zipUri = URI.create("jar:" + arc.archivePath().toUri());
            // 必须使用 try-with-resources ，确保 ZipFileSystem 读完后立刻关闭。
            synchronized (archiveFileSystemLock) {
                try (FileSystem zipFs = FileSystems.newFileSystem(zipUri, Map.of())) {
                    Path entry = zipFs.getPath(fileName);
                    if (!Files.exists(entry))
                        return null;
                    byte[] bytes = Files.readAllBytes(entry);
                    return new java.io.ByteArrayInputStream(bytes);
                } catch (IOException e) {
                    return null;
                }
            }
        }
        if (source instanceof BundledArchiveAudioSource arc) {
            byte[] bytes = readArchiveEntry(arc.archivePath(), fileName);
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        }
        return null;
    }

    private static boolean validateAudio(String packId, Path audioPath) {
        if (!Files.isRegularFile(audioPath)) {
            DJCraft.LOGGER.error("TrackPack {} audio file is missing: {}", packId, audioPath);
            return false;
        }
        try (InputStream input = Files.newInputStream(audioPath)) {
            if (!TrackPackAudioValidator.isPlayableOggVorbis(input)) {
                DJCraft.LOGGER.error("TrackPack {} audio is not a supported Ogg Vorbis stream: {}",
                        packId, audioPath);
                return false;
            }
            return true;
        } catch (IOException exception) {
            DJCraft.LOGGER.error("Failed to preflight TrackPack {} audio: {}", packId, audioPath, exception);
            return false;
        }
    }

    /**
     * Lists regular files below a safe relative prefix. Returned paths always use
     * forward slashes and remain relative to the TrackPack root.
     */
    public Set<String> listFiles(String packId, String prefix) {
        if (!isSafeRelativePath(prefix))
            return Set.of();
        Object source = audioSources.get(packId);
        if (source == null)
            return Set.of();

        try {
            if (source instanceof DirAudioSource dir) {
                Path root = directoryPackRoot(dir);
                return root == null ? Set.of() : listDirectoryFiles(root, prefix);
            }
            if (source instanceof ArchiveAudioSource arc) {
                synchronized (archiveFileSystemLock) {
                    return listArchiveFiles(arc.archivePath(), prefix);
                }
            }
            if (source instanceof BundledArchiveAudioSource arc) {
                return listStreamedArchiveFiles(arc.archivePath(), prefix);
            }
        } catch (IOException | RuntimeException exception) {
            DJCraft.LOGGER.error("Failed to list TrackPack files for {} below {}", packId, prefix, exception);
        }
        return Set.of();
    }

    private Path directoryPackRoot(DirAudioSource source) {
        if (source.rootPath() == null || source.oggPath() == null) {
            return null;
        }
        Path root = source.rootPath().normalize();
        return source.oggPath().normalize().startsWith(root) ? root : null;
    }

    static Set<String> listDirectoryFiles(Path root, String prefix) throws IOException {
        Path normalizedRoot = root.normalize();
        Path start = normalizedRoot.resolve(prefix).normalize();
        if (!start.startsWith(normalizedRoot) || !Files.isDirectory(start)) {
            return Set.of();
        }
        Set<String> files = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(start)) {
            paths.filter(path -> !Files.isSymbolicLink(path))
                    .filter(Files::isRegularFile)
                    .forEach(path -> files.add(toPortableRelativePath(normalizedRoot, path)));
        }
        return Set.copyOf(files);
    }

    static Set<String> listArchiveFiles(Path archive, String prefix) throws IOException {
        URI zipUri = URI.create("jar:" + archive.toUri());
        try (FileSystem zipFs = FileSystems.newFileSystem(zipUri, Map.of())) {
            Path root = zipFs.getPath("/");
            Path start = root.resolve(prefix).normalize();
            if (!start.startsWith(root) || !Files.isDirectory(start)) {
                return Set.of();
            }
            Set<String> files = new TreeSet<>();
            try (Stream<Path> paths = Files.walk(start)) {
                paths.filter(Files::isRegularFile)
                        .forEach(path -> files.add(toPortableRelativePath(root, path)));
            }
            return Set.copyOf(files);
        }
    }

    static String computeCanonicalContentHash(Path root) {
        try {
            Path normalizedRoot = root.normalize();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<Path> files;
            try (Stream<Path> paths = Files.walk(normalizedRoot)) {
                files = paths.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(path -> normalizedRoot.relativize(path)
                                .toString().replace('\\', '/')))
                        .toList();
            }
            byte[] buffer = new byte[8192];
            for (Path file : files) {
                String relative = normalizedRoot.relativize(file).toString().replace('\\', '/');
                byte[] name = relative.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (name.length >>> 24));
                digest.update((byte) (name.length >>> 16));
                digest.update((byte) (name.length >>> 8));
                digest.update((byte) name.length);
                digest.update(name);
                long size = Files.size(file);
                for (int shift = 56; shift >= 0; shift -= 8) {
                    digest.update((byte) (size >>> shift));
                }
                try (InputStream input = Files.newInputStream(file)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return toHex(digest.digest());
        } catch (Exception exception) {
            DJCraft.LOGGER.error("Failed to compute canonical TrackPack hash for {}", root, exception);
            return null;
        }
    }

    private static Map<String, byte[]> readArchiveEntries(Path archive, long maxBytes) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        long totalBytes = 0;
        try (InputStream input = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    byte[] bytes = zip.readAllBytes();
                    totalBytes += bytes.length;
                    if (totalBytes > maxBytes || entries.putIfAbsent(entry.getName(), bytes) != null) {
                        throw new IOException("Bundled TrackPack archive exceeds its limit or has duplicate entries");
                    }
                }
                zip.closeEntry();
            }
        }
        return entries;
    }

    private static byte[] readArchiveEntry(Path archive, String entryName) throws IOException {
        try (InputStream input = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entryName.equals(entry.getName())) {
                    return zip.readAllBytes();
                }
                zip.closeEntry();
            }
        }
        return null;
    }

    private static Set<String> listStreamedArchiveFiles(Path archive, String prefix) throws IOException {
        Set<String> files = new TreeSet<>();
        try (InputStream input = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().startsWith(prefix)) {
                    files.add(entry.getName());
                }
                zip.closeEntry();
            }
        }
        return Set.copyOf(files);
    }

    private static String computeCanonicalContentHash(Map<String, byte[]> entries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                byte[] name = entry.getKey().getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (name.length >>> 24));
                digest.update((byte) (name.length >>> 16));
                digest.update((byte) (name.length >>> 8));
                digest.update((byte) name.length);
                digest.update(name);
                long size = entry.getValue().length;
                for (int shift = 56; shift >= 0; shift -= 8) {
                    digest.update((byte) (size >>> shift));
                }
                digest.update(entry.getValue());
            }
            return toHex(digest.digest());
        } catch (Exception exception) {
            DJCraft.LOGGER.error("Failed to compute canonical bundled TrackPack hash", exception);
            return null;
        }
    }

    private static String toPortableRelativePath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static boolean isSafeRelativePath(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.indexOf('\\') >= 0 || fileName.indexOf(':') >= 0) {
            return false;
        }
        Path path = Path.of(fileName).normalize();
        return !path.isAbsolute() && path.getNameCount() > 0 && !path.startsWith("..");
    }

    /**
     * 获取所有已加载的曲目包
     */
    public synchronized Collection<TrackPack> getLoadedPacks() {
        return List.copyOf(loadedPacks.values());
    }

    /**
     * 获取所有已加载的曲目包ID
     */
    public synchronized Set<String> getLoadedPackIds() {
        return Set.copyOf(loadedPacks.keySet());
    }

    /**
     * 检查曲目包是否已加载
     */
    public synchronized boolean isPackLoaded(String id) {
        return loadedPacks.containsKey(id);
    }

    /**
     * 获取已加载曲目包数量
     */
    public synchronized int getLoadedPackCount() {
        return loadedPacks.size();
    }

    /**
     * 获取指定曲目包的 SHA-256 哈希
     */
    public synchronized Optional<String> getPackHash(String id) {
        return Optional.ofNullable(packHashes.get(id));
    }

    /**
     * 获取全部 packId -> hash 映射（用于网络同步）
     */
    public synchronized Map<String, String> getPackHashes() {
        return Map.copyOf(packHashes);
    }

    public synchronized Optional<String> getContentHash(String id) {
        return Optional.ofNullable(contentHashes.get(id));
    }

    public synchronized Map<String, String> getContentHashes() {
        return Map.copyOf(contentHashes);
    }

    public record ArchiveDescriptor(Path path, long size, String sha256) {
    }

    public record PreparedPackReload(String packId, TrackPack trackPack, String definitionHash, String contentHash,
            String archiveHash, Object audioSource, boolean valid) {
        private static PreparedPackReload invalid(String packId) {
            return new PreparedPackReload(packId, null, null, null, null, null, false);
        }
    }
}

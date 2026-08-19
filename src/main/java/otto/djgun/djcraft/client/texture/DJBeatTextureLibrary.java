package otto.djgun.djcraft.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.BeatDefinition;
import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.sound.TrackPackResources;

/** Reload-safe, predecoded PNG/GIF library for falling beat markers. */
@OnlyIn(Dist.CLIENT)
public final class DJBeatTextureLibrary
        extends SimplePreparableReloadListener<DJBeatTextureLibrary.PreparedSnapshot> {
    public static final long MAX_DECODED_PIXELS_PER_SNAPSHOT = 67_108_864L;
    private static final ResourceLocation FALLBACK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            DJCraft.MODID, "textures/gui/beats/blue_beat.png");
    private static final DJBeatTextureLibrary INSTANCE = new DJBeatTextureLibrary();
    private volatile Snapshot snapshot = Snapshot.empty();

    private DJBeatTextureLibrary() {
    }

    public static DJBeatTextureLibrary getInstance() {
        return INSTANCE;
    }

    public AnimatedTexture resolve(String packId, String textureReference) {
        if (packId == null || textureReference == null || textureReference.isBlank()) {
            return AnimatedTexture.fallback();
        }
        return snapshot.animations().getOrDefault(new TextureKey(packId, textureReference),
                AnimatedTexture.fallback());
    }

    @Override
    protected PreparedSnapshot prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<TextureKey, BeatImageDecoder.DecodedAnimation> decoded = new LinkedHashMap<>();
        long decodedPixels = 0L;

        for (TrackPack pack : TrackPackManager.getInstance().getLoadedPacks()) {
            for (String reference : collectReferences(pack)) {
                TextureKey key = new TextureKey(pack.id(), reference);
                Optional<ResourceLocation> location = resolveSource(pack.id(), reference);
                if (location.isEmpty()) {
                    DJCraft.LOGGER.warn("Rejected invalid beat texture reference in TrackPack {}: {}",
                            pack.id(), reference);
                    continue;
                }
                try (InputStream input = resourceManager.open(location.get())) {
                    BeatImageDecoder.DecodedAnimation animation = BeatImageDecoder.decode(input, reference);
                    if (decodedPixels + animation.decodedPixels() > MAX_DECODED_PIXELS_PER_SNAPSHOT) {
                        DJCraft.LOGGER.warn(
                                "Rejected beat texture {} in TrackPack {}: snapshot exceeds {} decoded pixels",
                                reference, pack.id(), MAX_DECODED_PIXELS_PER_SNAPSHOT);
                        continue;
                    }
                    decoded.put(key, animation);
                    decodedPixels += animation.decodedPixels();
                } catch (IOException | RuntimeException exception) {
                    DJCraft.LOGGER.warn("Could not load beat texture {} in TrackPack {}; using fallback",
                            reference, pack.id(), exception);
                }
            }
        }
        return new PreparedSnapshot(Map.copyOf(decoded), decodedPixels);
    }

    @Override
    protected void apply(PreparedSnapshot prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        Snapshot previous = snapshot;
        previous.registeredTextures().forEach(textureManager::release);

        Map<TextureKey, AnimatedTexture> animations = new LinkedHashMap<>();
        Set<ResourceLocation> registered = new LinkedHashSet<>();
        for (Map.Entry<TextureKey, BeatImageDecoder.DecodedAnimation> entry : prepared.decoded().entrySet()) {
            TextureKey key = entry.getKey();
            BeatImageDecoder.DecodedAnimation decoded = entry.getValue();
            List<ResourceLocation> frames = new ArrayList<>(decoded.frames().size());
            for (int frameIndex = 0; frameIndex < decoded.frames().size(); frameIndex++) {
                ResourceLocation location = dynamicFrameLocation(key, frameIndex);
                NativeImage image = toNativeImage(decoded.width(), decoded.height(),
                        decoded.frames().get(frameIndex).argbPixels());
                textureManager.register(location, new DynamicTexture(image));
                frames.add(location);
                registered.add(location);
            }
            List<Long> durations = decoded.frames().stream()
                    .map(BeatImageDecoder.DecodedFrame::durationMs)
                    .toList();
            animations.put(key, new AnimatedTexture(
                    decoded.width(), decoded.height(), List.copyOf(frames), durations, decoded.loopCount()));
        }

        snapshot = new Snapshot(Map.copyOf(animations), Set.copyOf(registered));
        DJCraft.LOGGER.info("Loaded {} falling-beat texture(s), {} decoded pixels",
                animations.size(), prepared.decodedPixels());
    }

    private static Set<String> collectReferences(TrackPack pack) {
        Set<String> references = new LinkedHashSet<>();
        pack.definitions().values().stream()
                .map(BeatDefinition::texture)
                .filter(reference -> reference != null && !reference.isBlank())
                .forEach(references::add);
        for (BeatEvent event : pack.timeline().combatLine()) {
            Object override = event.props().get("texture");
            if (override instanceof String reference && !reference.isBlank()) {
                references.add(reference);
            }
        }
        return references;
    }

    private static Optional<ResourceLocation> resolveSource(String packId, String reference) {
        String lower = reference.toLowerCase(java.util.Locale.ROOT);
        if (!reference.equals(lower) || (!lower.endsWith(".png") && !lower.endsWith(".gif"))) {
            return Optional.empty();
        }
        if (reference.indexOf(':') >= 0) {
            return Optional.ofNullable(ResourceLocation.tryParse(reference));
        }
        return TrackPackResources.beatTextureLocation(packId, reference);
    }

    private static ResourceLocation dynamicFrameLocation(TextureKey key, int frameIndex) {
        String packKey = UUID.nameUUIDFromBytes(key.packId().getBytes(StandardCharsets.UTF_8)).toString();
        String assetKey = UUID.nameUUIDFromBytes(key.reference().getBytes(StandardCharsets.UTF_8)).toString();
        return ResourceLocation.fromNamespaceAndPath(DJCraft.MODID,
                "dynamic/falling_beats/" + packKey + "/" + assetKey + "/" + frameIndex);
    }

    private static NativeImage toNativeImage(int width, int height, int[] argbPixels) {
        NativeImage image = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int argb = argbPixels[rowOffset + x];
                int abgr = (argb & 0xFF00FF00)
                        | ((argb & 0x00FF0000) >>> 16)
                        | ((argb & 0x000000FF) << 16);
                image.setPixelRGBA(x, y, abgr);
            }
        }
        return image;
    }

    private record TextureKey(String packId, String reference) {
    }

    protected record PreparedSnapshot(Map<TextureKey, BeatImageDecoder.DecodedAnimation> decoded,
            long decodedPixels) {
    }

    private record Snapshot(Map<TextureKey, AnimatedTexture> animations,
            Set<ResourceLocation> registeredTextures) {
        private static Snapshot empty() {
            return new Snapshot(Map.of(), Set.of());
        }
    }

    public record AnimatedTexture(int width, int height, List<ResourceLocation> frames,
            List<Long> frameDurationsMs, int loopCount) {
        public AnimatedTexture {
            frames = List.copyOf(frames);
            frameDurationsMs = List.copyOf(frameDurationsMs);
        }

        private static AnimatedTexture fallback() {
            return new AnimatedTexture(32, 16, List.of(FALLBACK_TEXTURE), List.of(0L), 0);
        }

        public ResourceLocation frameAt(long elapsedMs) {
            if (frames.size() <= 1) {
                return frames.getFirst();
            }
            long cycleDuration = frameDurationsMs.stream().mapToLong(Long::longValue).sum();
            if (cycleDuration <= 0L) {
                return frames.getFirst();
            }
            long position;
            if (loopCount == 0) {
                position = Math.floorMod(elapsedMs, cycleDuration);
            } else {
                long totalDuration;
                try {
                    totalDuration = Math.multiplyExact(cycleDuration, Math.max(1, loopCount));
                } catch (ArithmeticException ignored) {
                    totalDuration = Long.MAX_VALUE;
                }
                if (elapsedMs >= totalDuration) {
                    return frames.getLast();
                }
                position = Math.floorMod(elapsedMs, cycleDuration);
            }
            long cursor = 0L;
            for (int index = 0; index < frames.size(); index++) {
                cursor += frameDurationsMs.get(index);
                if (position < cursor) {
                    return frames.get(index);
                }
            }
            return frames.getLast();
        }
    }
}

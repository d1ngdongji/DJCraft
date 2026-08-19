package otto.djgun.djcraft.network.packet;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record CyberGrindPresetListPayload(List<Preset> presets) implements CustomPacketPayload {
    private static final int MAX_PRESETS = 256;
    public record Preset(String id, String displayName, String description) {
    }

    public static final Type<CyberGrindPresetListPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "cyber_grind_presets"));
    public static final StreamCodec<FriendlyByteBuf, CyberGrindPresetListPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                if (value.presets().size() > MAX_PRESETS) {
                    throw new IllegalArgumentException("Too many Cyber Grind presets");
                }
                buf.writeVarInt(value.presets().size());
                for (Preset preset : value.presets()) {
                    buf.writeUtf(preset.id(), 256);
                    buf.writeUtf(preset.displayName(), 256);
                    buf.writeUtf(preset.description(), 1024);
                }
            },
            buf -> {
                int size = buf.readVarInt();
                if (size < 0 || size > MAX_PRESETS) {
                    throw new IllegalArgumentException("Invalid Cyber Grind preset count");
                }
                List<Preset> presets = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    presets.add(new Preset(buf.readUtf(256), buf.readUtf(256), buf.readUtf(1024)));
                }
                return new CyberGrindPresetListPayload(presets);
            });

    public CyberGrindPresetListPayload {
        presets = List.copyOf(presets);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

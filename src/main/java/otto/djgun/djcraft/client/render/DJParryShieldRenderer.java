package otto.djgun.djcraft.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import otto.djgun.djcraft.DJCraft;

import java.io.IOException;

public final class DJParryShieldRenderer {
    private static ShaderInstance shader;
    private static final RenderType GLOW = RenderType.create(
            "djcraft_parry_shield_glow",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> shader))
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            ModelBakery.NO_PATTERN_SHIELD.atlasLocation(), false, false))
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private DJParryShieldRenderer() {
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            ShaderInstance instance = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "parry_shield_glow"),
                    DefaultVertexFormat.NEW_ENTITY);
            event.registerShader(instance, loaded -> shader = loaded);
        } catch (IOException exception) {
            shader = null;
            DJCraft.LOGGER.error("Failed to load parry shield glow shader", exception);
        }
    }

    public static boolean isReady() {
        return shader != null;
    }

    public static void renderShield(ShieldModel model, ItemStack stack, PoseStack poseStack,
            MultiBufferSource source) {
        BannerPatternLayers patterns = stack.getOrDefault(
                DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        DyeColor baseColor = stack.get(DataComponents.BASE_COLOR);
        Material material = !patterns.layers().isEmpty() || baseColor != null
                ? ModelBakery.SHIELD_BASE
                : ModelBakery.NO_PATTERN_SHIELD;
        var glowBuffer = material.sprite().wrap(source.getBuffer(GLOW));

        // Vanilla's shield is a special entity model, so reproduce its local transform and
        // submit the handle and plate directly instead of attempting to redraw a BakedModel.
        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F);
        model.handle().render(poseStack, glowBuffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        model.plate().render(poseStack, glowBuffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}

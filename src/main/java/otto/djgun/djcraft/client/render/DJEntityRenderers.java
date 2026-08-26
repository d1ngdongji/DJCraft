package otto.djgun.djcraft.client.render;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import otto.djgun.djcraft.init.ModEntities;

public final class DJEntityRenderers {
    private DJEntityRenderers() {
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.THROWN_MACE.get(), DJThrownMaceRenderer::new);
    }
}

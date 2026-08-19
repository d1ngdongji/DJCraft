package otto.djgun.djcraft.cybergrind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

class CyberGrindSavedDataTest {
    @Test
    void scoresOnlyIncreaseAndRoundTripWithReturnPoint() {
        CyberGrindSavedData data = new CyberGrindSavedData();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertEquals(12, data.updatePersonal("djcraft:default", first, 12));
        assertEquals(12, data.updatePersonal("djcraft:default", first, 4));
        assertEquals(18, data.updateGroup("djcraft:default", List.of(second, first), 18));
        assertEquals(18, data.updateGroup("djcraft:default", List.of(first, second), 8));
        data.putReturn(first, new CyberGrindSavedData.ReturnPoint(
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                1.5, 64.0, -2.5, 90.0F, 10.0F));

        CompoundTag tag = data.save(new CompoundTag(), null);
        CyberGrindSavedData loaded = CyberGrindSavedData.load(tag, null);
        assertTrue(loaded.returnPoint(first).isPresent());
        assertEquals(12, loaded.updatePersonal("djcraft:default", first, 0));
        assertEquals(18, loaded.updateGroup("djcraft:default", List.of(first, second), 0));
    }
}

package otto.djgun.djcraft.combat;

import net.minecraft.world.item.ItemStack;
import otto.djgun.djcraft.data.BeatCategory;
import otto.djgun.djcraft.data.BeatDefinition;
import otto.djgun.djcraft.init.ModItemTags;

/** Shared category/tag matching plus server-side damage multipliers. */
public final class DJBeatDamageRules {
    public static final float WEAKBEAT_DAMAGE_MULTIPLIER = 0.5F;
    public static final float DOWNBEAT_SMASH_DAMAGE_MULTIPLIER = 1.5F;

    private DJBeatDamageRules() {
    }

    public static float multiplier(BeatDefinition definition, ItemStack weapon) {
        BeatCategory category = definition == null ? BeatCategory.NORMAL : definition.category();
        return multiplier(category, weapon.is(ModItemTags.SWIFT), weapon.is(ModItemTags.SMASH));
    }

    public static boolean isCategoryMatched(BeatDefinition definition, ItemStack weapon) {
        if (definition == null || weapon == null) {
            return false;
        }
        return isCategoryMatched(
                definition.category(), weapon.is(ModItemTags.SWIFT), weapon.is(ModItemTags.SMASH));
    }

    static boolean isCategoryMatched(BeatCategory category, boolean swift, boolean smash) {
        return switch (category) {
            case WEAKBEAT -> swift;
            case DOWNBEAT -> smash;
            case NORMAL -> false;
        };
    }

    static float multiplier(BeatCategory category, boolean swift, boolean smash) {
        return switch (category) {
            case WEAKBEAT -> swift ? 1.0F : WEAKBEAT_DAMAGE_MULTIPLIER;
            case DOWNBEAT -> smash ? DOWNBEAT_SMASH_DAMAGE_MULTIPLIER : 1.0F;
            case NORMAL -> 1.0F;
        };
    }
}

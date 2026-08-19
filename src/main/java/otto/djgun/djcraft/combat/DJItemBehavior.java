package otto.djgun.djcraft.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.WindChargeItem;
import otto.djgun.djcraft.DJCraft;

/** Built-in DJ use behavior assigned to an item. */
public enum DJItemBehavior {
    NONE("none"),
    BOW("bow"),
    CROSSBOW("crossbow"),
    SHIELD("shield"),
    CHARGE("charge"),
    TRIGGER("trigger"),
    TRIDENT("trident"),
    MACE("mace");

    private final ResourceLocation id;

    DJItemBehavior(String path) {
        this.id = ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, path);
    }

    public ResourceLocation id() {
        return id;
    }

    public boolean supports(Item item) {
        return switch (this) {
            case NONE -> true;
            case BOW -> item instanceof BowItem;
            case CROSSBOW -> item instanceof CrossbowItem;
            case SHIELD -> item instanceof ShieldItem;
            case CHARGE, TRIGGER, TRIDENT, MACE -> true;
        };
    }

    public boolean isCharge() {
        return this == BOW || this == CHARGE;
    }

    public boolean isTrigger() {
        return this == CROSSBOW || this == TRIGGER;
    }

    public static DJItemBehavior inherited(Item item) {
        if (item instanceof CrossbowItem) {
            return CROSSBOW;
        }
        if (item instanceof BowItem) {
            return BOW;
        }
        if (item instanceof ShieldItem) {
            return SHIELD;
        }
        if (item instanceof TridentItem) {
            return TRIDENT;
        }
        if (item instanceof MaceItem) {
            return MACE;
        }
        if (item instanceof WindChargeItem) {
            return TRIGGER;
        }
        return NONE;
    }

    public static DJItemBehavior parse(ResourceLocation id) {
        for (DJItemBehavior behavior : values()) {
            if (behavior.id.equals(id)) {
                return behavior;
            }
        }
        throw new IllegalArgumentException("Unknown DJ item behavior: " + id);
    }
}

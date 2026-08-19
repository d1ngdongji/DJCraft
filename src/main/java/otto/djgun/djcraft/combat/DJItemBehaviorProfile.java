package otto.djgun.djcraft.combat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;

import java.util.LinkedHashSet;
import java.util.Set;
import otto.djgun.djcraft.api.combat.DJItemBehaviorDefinition;
import otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry;
import otto.djgun.djcraft.api.combat.DJMeleeBehavior;

public record DJItemBehaviorProfile(
        ResourceLocation id,
        int priority,
        Set<ResourceLocation> items,
        Set<TagKey<Item>> tags,
        DJItemBehaviorDefinition behavior,
        DJMeleeBehavior meleeBehavior) {
    private static final Set<String> ROOT_FIELDS = Set.of("priority", "selectors", "behavior", "melee");
    private static final Set<String> SELECTOR_FIELDS = Set.of("items", "tags");

    public DJItemBehaviorProfile {
        if (id == null || priority < 0 || priority > 1_000 || items == null || tags == null
                || behavior == null || meleeBehavior == null) {
            throw new IllegalArgumentException("Invalid item behavior profile");
        }
        if (items.isEmpty() && tags.isEmpty()) {
            throw new IllegalArgumentException("At least one item behavior selector is required");
        }
        items = Set.copyOf(items);
        tags = Set.copyOf(tags);
    }

    public static DJItemBehaviorProfile parse(ResourceLocation id, JsonElement element) {
        JsonObject root = GsonHelper.convertToJsonObject(element, "item behavior profile");
        requireOnly(root, ROOT_FIELDS, "item behavior profile");
        int priority = GsonHelper.getAsInt(root, "priority", 0);
        if (priority < 0 || priority > 1_000) {
            throw new IllegalArgumentException("priority must be between 0 and 1000");
        }

        JsonObject selectors = GsonHelper.getAsJsonObject(root, "selectors");
        requireOnly(selectors, SELECTOR_FIELDS, "item behavior selectors");
        Set<ResourceLocation> items = readIds(selectors, "items");
        Set<TagKey<Item>> tags = new LinkedHashSet<>();
        for (ResourceLocation tagId : readIds(selectors, "tags")) {
            tags.add(TagKey.create(Registries.ITEM, tagId));
        }
        DJItemBehaviorDefinition behavior = DJItemBehaviorRegistry.require(
                ResourceLocation.parse(GsonHelper.getAsString(root, "behavior")));
        DJMeleeBehavior meleeBehavior = behavior.meleeBehavior()
                .orElseGet(() -> DJItemBehaviorRegistry.MELEE.meleeBehavior().orElseThrow());
        if (root.has("melee")) {
            meleeBehavior = DJMeleeBehaviorOverride.parse(
                    GsonHelper.getAsJsonObject(root, "melee"), meleeBehavior);
        }
        return new DJItemBehaviorProfile(id, priority, items, tags, behavior, meleeBehavior);
    }

    private static Set<ResourceLocation> readIds(JsonObject object, String field) {
        if (!object.has(field)) {
            return Set.of();
        }
        JsonArray array = GsonHelper.getAsJsonArray(object, field);
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (JsonElement element : array) {
            ResourceLocation id = ResourceLocation.parse(
                    GsonHelper.convertToString(element, field + " selector"));
            if (!result.add(id)) {
                throw new IllegalArgumentException("Duplicate " + field + " selector: " + id);
            }
        }
        return Set.copyOf(result);
    }

    private static void requireOnly(JsonObject object, Set<String> allowed, String description) {
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("Unknown " + description + " field: " + field);
            }
        }
    }
}

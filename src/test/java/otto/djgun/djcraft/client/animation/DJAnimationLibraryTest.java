package otto.djgun.djcraft.client.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import otto.djgun.djcraft.sound.DJActionOutcome;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.api.combat.RegisterDJItemBehaviorsEvent;

class DJAnimationLibraryTest {
    private static final Object ITEM = new Object();
    private static final ResourceLocation DIAMOND_SWORD =
            ResourceLocation.parse("minecraft:diamond_sword");

    @AfterEach
    void clearSnapshot() {
        DJAnimationLibrary.getInstance().installForTests(Map.of(), List.of());
    }

    @Test
    void parsesSelectorsSemanticAndFixedBeatDuration() {
        DJAnimationCurve curve = curve(1.0f);
        DJAnimationLibrary.LoadedProfile profile = DJAnimationLibrary.parseProfile(
                ResourceLocation.parse("example:diamond_sword"),
                JsonParser.parseString("""
                        {
                          "priority": 250,
                          "selectors": {
                            "items": ["minecraft:diamond_sword"],
                            "tags": ["minecraft:swords"]
                          },
                          "animations": {
                            "melee_strike": {
                              "clip": "animation.example.strike",
                              "duration_beats": 1.5
                            }
                          }
                        }
                        """),
                Map.of("animation.example.strike", curve));

        assertEquals(250, profile.priority());
        assertEquals(Set.of(DIAMOND_SWORD), profile.items());
        assertEquals(1.5,
                profile.animations().get(DJAnimationSemantic.MELEE_STRIKE).durationBeats());
        assertSame(curve, profile.animations().get(DJAnimationSemantic.MELEE_STRIKE).curve());
    }

    @Test
    void parsesSingleUseSemantic() {
        DJAnimationCurve curve = curve(1.0f);
        DJAnimationLibrary.LoadedProfile profile = DJAnimationLibrary.parseProfile(
                ResourceLocation.parse("example:use"),
                JsonParser.parseString("""
                        {
                          "selectors": {"items": ["minecraft:apple"]},
                          "animations": {
                            "use": {
                              "clip": "animation.example.use",
                              "duration_beats": 1.0
                            }
                          }
                        }
                        """),
                Map.of("animation.example.use", curve));

        assertEquals(1.0, profile.animations().get(DJAnimationSemantic.USE).durationBeats());
        assertSame(curve, profile.animations().get(DJAnimationSemantic.USE).curve());
    }

    @Test
    void behaviorSelectorSuppliesCrossbowAnimationBelowExactSelectors() {
        DJAnimationCurve behaviorCurve = curve(1.0f);
        DJAnimationCurve exactCurve = curve(2.0f);
        ResourceLocation addonCrossbow = ResourceLocation.parse("example:crossbow");
        DJAnimationLibrary.LoadedProfile behavior = new DJAnimationLibrary.LoadedProfile(
                ResourceLocation.parse("example:crossbow_behavior"), 100,
                Set.of(), Set.of(), Set.of(DJItemBehavior.CROSSBOW.id()),
                Map.of(DJAnimationSemantic.TRIGGER_IMPACT,
                        new DJAnimationLibrary.Binding(behaviorCurve, 1.0)));
        DJAnimationLibrary.LoadedProfile exact = new DJAnimationLibrary.LoadedProfile(
                ResourceLocation.parse("example:crossbow_exact"), 0,
                Set.of(addonCrossbow), Set.of(), Set.of(),
                Map.of(DJAnimationSemantic.TRIGGER_IMPACT,
                        new DJAnimationLibrary.Binding(exactCurve, 2.0)));
        DJAnimationLibrary.getInstance().installForTests(Map.of(), List.of(behavior, exact));
        DJAnimationEvent event = new DJAnimationEvent(1, 1, DJAnimationHand.MAIN, ITEM,
                addonCrossbow.toString(), DJAnimationSemantic.TRIGGER_IMPACT,
                1_000L, 4.0, 0.5, DJActionOutcome.NOT_JUDGED);

        DJAnimationSelection selection = DJAnimationLibrary.getInstance().resolve(
                event, DJItemBehavior.CROSSBOW);

        assertSame(exactCurve, selection.profile().curve());
        assertEquals(2.0, selection.durationBeats(event));
    }

    @Test
    void parsesRegisteredAddonBehaviorSelector() {
        ResourceLocation behaviorId = ResourceLocation.parse("example:animated_trigger");
        new RegisterDJItemBehaviorsEvent().registerTrigger(
                behaviorId, item -> true, context ->
                        net.minecraft.world.InteractionResultHolder.success(context.sourceStack()));
        DJAnimationCurve curve = curve(1.0f);

        DJAnimationLibrary.LoadedProfile profile = DJAnimationLibrary.parseProfile(
                ResourceLocation.parse("example:animated_trigger_profile"),
                JsonParser.parseString("""
                        {
                          "selectors": {"behaviors": ["example:animated_trigger"]},
                          "animations": {
                            "trigger_impact": {
                              "clip": "animation.example.trigger",
                              "duration_beats": 1.0
                            }
                          }
                        }
                        """),
                Map.of("animation.example.trigger", curve));

        assertEquals(Set.of(behaviorId), profile.behaviors());
    }

    @Test
    void parsesIndependentHandAndItemCenterTracks() {
        DJAnimationCurve curve = DJAnimationLibrary.parseClip(JsonParser.parseString("""
                {
                  "animation_length": 1.0,
                  "bones": {
                    "first_person_hand": {
                      "position": {
                        "0.0": {"vector":[0,0,0]},
                        "1.0": {"vector":[16,8,-4]}
                      },
                      "rotation": {
                        "0.0": {"vector":[0,0,0]},
                        "1.0": {"vector":[10,20,30]}
                      }
                    },
                    "first_person_item": {
                      "position": {
                        "0.0": {"vector":[0,0,0]},
                        "1.0": {"vector":[4,-8,16]}
                      },
                      "rotation": {
                        "0.0": {"vector":[0,0,0]},
                        "1.0": {"vector":[-5,15,-25]}
                      }
                    }
                  }
                }
                """));

        DJAnimationPose end = curve.sample(1.0f);

        assertEquals(-1.0f, end.handSpace().translationXBlocks(), 0.0001f);
        assertEquals(0.5f, end.handSpace().translationYBlocks(), 0.0001f);
        assertEquals(-10.0f, end.handSpace().rotationXDegrees(), 0.0001f);
        assertEquals(-0.25f, end.itemCenterSpace().translationXBlocks(), 0.0001f);
        assertEquals(-0.5f, end.itemCenterSpace().translationYBlocks(), 0.0001f);
        assertEquals(5.0f, end.itemCenterSpace().rotationXDegrees(), 0.0001f);
    }

    @Test
    void legacyItemBoneRemainsItemCenterSpace() {
        DJAnimationCurve curve = DJAnimationLibrary.parseClip(JsonParser.parseString("""
                {
                  "animation_length": 1.0,
                  "bones": {
                    "first_person_item": {
                      "position": {
                        "0.0": {"vector":[0,0,0]},
                        "1.0": {"vector":[0,16,0]}
                      },
                      "rotation": {
                        "0.0": {"vector":[0,0,0]},
                        "1.0": {"vector":[0,0,10]}
                      }
                    }
                  }
                }
                """));

        DJAnimationPose end = curve.sample(1.0f);

        assertEquals(DJAnimationTransform.IDENTITY, end.handSpace());
        assertEquals(1.0f, end.itemCenterSpace().translationYBlocks(), 0.0001f);
        assertEquals(10.0f, end.itemCenterSpace().rotationZDegrees(), 0.0001f);
    }

    @Test
    void clipDocumentIgnoresEntityAnimationsAndForeignFirstPersonIds() {
        Map<String, DJAnimationCurve> curves = new HashMap<>();
        Map<String, ResourceLocation> sources = new HashMap<>();

        DJAnimationLibrary.parseClipDocument(
                ResourceLocation.parse("example:entity/mixed.animation"),
                JsonParser.parseString("""
                        {
                          "animations": {
                            "animation.example.entity.walk": {
                              "bones": {"body": {}}
                            },
                            "animation.foreign.first_person.attack": {
                              "animation_length": 1.0,
                              "bones": {}
                            },
                            "animation.example.first_person.attack": {
                              "animation_length": 1.0,
                              "bones": {
                                "first_person_hand": {
                                  "position": {
                                    "0.0": {"vector":[0,0,0]},
                                    "1.0": {"vector":[0,0,0]}
                                  },
                                  "rotation": {
                                    "0.0": {"vector":[0,0,0]},
                                    "1.0": {"vector":[0,0,0]}
                                  }
                                }
                              }
                            }
                          }
                        }
                        """),
                curves, sources);

        assertEquals(Set.of("animation.example.first_person.attack"), curves.keySet());
        assertEquals(ResourceLocation.parse("example:entity/mixed.animation"),
                sources.get("animation.example.first_person.attack"));
    }

    @Test
    void bundledClipsUseHandSpaceExceptTridentItemCenterRotation() throws Exception {
        int clipCount = 0;
        for (String resource : List.of(
                "/assets/djcraft/animations/first_person.animation.json",
                "/assets/djcraft/animations/parry.animation.json")) {
            var stream = DJAnimationLibraryTest.class.getResourceAsStream(resource);
            assertNotNull(stream, resource);
            JsonObject animations;
            try (var reader = new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                animations = JsonParser.parseReader(reader).getAsJsonObject()
                        .getAsJsonObject("animations");
            }
            for (var entry : animations.entrySet()) {
                DJAnimationCurve curve = DJAnimationLibrary.parseClip(entry.getValue());
                if (entry.getKey().equals("animation.djcraft.first_person.trident_thrust")) {
                    DJAnimationPose impact = curve.sample(0.0f);
                    assertEquals(-1.5f, impact.handSpace().translationZBlocks(), 0.0001f);
                    assertEquals(0.0f, impact.handSpace().rotationZDegrees(), 0.0001f);
                    assertEquals(0.0f, impact.itemCenterSpace().translationZBlocks(), 0.0001f);
                    assertEquals(45.0f, impact.itemCenterSpace().rotationZDegrees(), 0.0001f);
                    for (DJAnimationCurve.Keyframe keyframe : curve.keyframes()) {
                        assertEquals(0.0f,
                                keyframe.pose().handSpace().rotationZDegrees(), 0.0001f);
                        assertEquals(0.0f,
                                keyframe.pose().itemCenterSpace().translationZBlocks(), 0.0001f);
                    }
                } else {
                    for (DJAnimationCurve.Keyframe keyframe : curve.keyframes()) {
                        assertEquals(DJAnimationTransform.IDENTITY,
                                keyframe.pose().itemCenterSpace(), entry.getKey());
                    }
                }
                clipCount++;
            }
        }
        assertEquals(11, clipCount);
    }

    @Test
    void parsesAndResolvesRegisteredAddonSemantic() {
        DJAnimationSemantic spin = new RegisterDJAnimationSemanticsEvent().register(
                ResourceLocation.parse("example:spin"),
                DJAnimationProfile.Channel.IMPULSE, 75, 0.5);
        DJAnimationCurve curve = curve(2.0f);
        DJAnimationLibrary.LoadedProfile profile = DJAnimationLibrary.parseProfile(
                ResourceLocation.parse("example:spin_profile"),
                JsonParser.parseString("""
                        {
                          "selectors": {"items": ["minecraft:diamond_sword"]},
                          "animations": {
                            "example:spin": {
                              "clip": "animation.example.spin"
                            }
                          }
                        }
                        """),
                Map.of("animation.example.spin", curve));
        DJAnimationLibrary.getInstance().installForTests(Map.of(), List.of(profile));
        DJAnimationEvent event = new DJAnimationEvent(1, 1, DJAnimationHand.MAIN, ITEM,
                DIAMOND_SWORD.toString(), spin, 1_000L, 4.0, 0.0, DJActionOutcome.NOT_JUDGED);

        DJAnimationSelection selection = DJAnimationLibrary.getInstance().resolve(event);

        assertSame(curve, selection.profile().curve());
        assertEquals(DJAnimationProfile.Channel.IMPULSE, selection.profile().channel());
        assertEquals(0.5, selection.durationBeats(event));
    }

    @Test
    void rejectsUnknownSemanticsInvalidDurationAndSelectorlessNonGenericProfiles() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"selectors":{"items":["minecraft:stick"]},"animations":{"dance":{"clip":"clip"}}}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"selectors":{"items":["minecraft:stick"]},"animations":{
                  "melee_strike":{"clip":"clip","duration_beats":0}
                }}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"animations":{"melee_strike":{"clip":"clip"}}}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"selectors":{"items":["minecraft:stick"]},"animations":{"idle":{"clip":"clip"}}}
                """));
    }

    @Test
    void resolvesEachSemanticThroughExactTagGenericAndJavaFallbackLayers() {
        DJAnimationCurve exactCurve = curve(1.0f);
        DJAnimationCurve tagCurve = curve(2.0f);
        DJAnimationCurve genericCurve = curve(3.0f);
        DJAnimationLibrary.LoadedProfile exact = profile("example:exact", 0,
                Set.of(DIAMOND_SWORD), Set.of(),
                DJAnimationSemantic.MELEE_STRIKE, exactCurve, 2.0);
        DJAnimationLibrary.LoadedProfile tag = profile("example:tag", 1_000,
                Set.of(), Set.of(TagKey.create(Registries.ITEM, ResourceLocation.parse("minecraft:swords"))),
                DJAnimationSemantic.MELEE_STRIKE, tagCurve, 3.0);
        DJAnimationLibrary.LoadedProfile generic = profile("djcraft:generic", 0,
                Set.of(), Set.of(),
                DJAnimationSemantic.MELEE_THRUST, genericCurve, 4.0);
        DJAnimationLibrary.getInstance().installForTests(Map.of(), List.of(tag, generic, exact));

        DJAnimationSelection exactSelection = DJAnimationLibrary.getInstance().resolve(
                event(DJAnimationEvent.Kind.MELEE_STRIKE));
        DJAnimationSelection genericSelection = DJAnimationLibrary.getInstance().resolve(
                event(DJAnimationEvent.Kind.MELEE_THRUST));
        DJAnimationSelection fallbackSelection = DJAnimationLibrary.getInstance().resolve(
                event(DJAnimationEvent.Kind.MELEE_SWEEP));

        assertSame(exactCurve, exactSelection.profile().curve());
        assertEquals(2.0, exactSelection.durationBeats(event(DJAnimationEvent.Kind.MELEE_STRIKE)));
        assertSame(genericCurve, genericSelection.profile().curve());
        assertEquals(4.0, genericSelection.durationBeats(event(DJAnimationEvent.Kind.MELEE_THRUST)));
        assertEquals(0.65, fallbackSelection.durationBeats(
                eventWithDuration(DJAnimationEvent.Kind.MELEE_SWEEP, 0.0)));
    }

    @Test
    void higherPriorityExactProfileWinsAndResourceDurationOverridesEventDuration() {
        DJAnimationCurve lowCurve = curve(1.0f);
        DJAnimationCurve highCurve = curve(2.0f);
        DJAnimationLibrary.LoadedProfile low = profile("example:a_low", 10,
                Set.of(DIAMOND_SWORD), Set.of(),
                DJAnimationSemantic.MELEE_STRIKE, lowCurve, 1.0);
        DJAnimationLibrary.LoadedProfile high = profile("example:z_high", 20,
                Set.of(DIAMOND_SWORD), Set.of(),
                DJAnimationSemantic.MELEE_STRIKE, highCurve, 2.0);
        DJAnimationLibrary.getInstance().installForTests(Map.of(), List.of(low, high));

        DJAnimationEvent event = eventWithDuration(DJAnimationEvent.Kind.MELEE_STRIKE, 0.25);
        DJAnimationSelection selection = DJAnimationLibrary.getInstance().resolve(event);

        assertSame(highCurve, selection.profile().curve());
        assertEquals(2.0, selection.durationBeats(event));
    }

    @Test
    void idleBindingLoopsOnItsDeclaredVirtualBeatLength() {
        DJAnimationCurve idleCurve = new DJAnimationCurve(
                key(0.0f, 0.0f), key(0.5f, 1.0f), key(1.0f, 0.0f));
        DJAnimationLibrary.LoadedProfile generic = profile("djcraft:generic", 0,
                Set.of(), Set.of(), DJAnimationSemantic.IDLE, idleCurve, 2.0);
        DJAnimationLibrary.getInstance().installForTests(Map.of(), List.of(generic));
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();

        DJAnimationPose firstPeak = animator.sample(DJAnimationHand.MAIN, ITEM,
                DIAMOND_SWORD.toString(), snapshot(1.0));
        DJAnimationPose loopedPeak = animator.sample(DJAnimationHand.MAIN, ITEM,
                DIAMOND_SWORD.toString(), snapshot(3.0));

        assertEquals(1.0f, firstPeak.translationYBlocks(), 0.0001f);
        assertEquals(firstPeak, loopedPeak);
    }

    private static DJAnimationLibrary.LoadedProfile parse(String json) {
        return DJAnimationLibrary.parseProfile(ResourceLocation.parse("example:test"),
                JsonParser.parseString(json), Map.of("clip", curve(1.0f)));
    }

    private static DJAnimationLibrary.LoadedProfile profile(String id, int priority,
            Set<ResourceLocation> items, Set<TagKey<Item>> tags,
            DJAnimationSemantic semantic, DJAnimationCurve curve, double duration) {
        return new DJAnimationLibrary.LoadedProfile(ResourceLocation.parse(id), priority, items, tags,
                Map.of(semantic, new DJAnimationLibrary.Binding(curve, duration)));
    }

    private static DJAnimationEvent event(DJAnimationEvent.Kind kind) {
        return eventWithDuration(kind, 0.5);
    }

    private static DJAnimationEvent eventWithDuration(DJAnimationEvent.Kind kind, double duration) {
        return new DJAnimationEvent(1, 1, DJAnimationHand.MAIN, ITEM,
                DIAMOND_SWORD.toString(), kind, 1_000L, 4.0, duration, DJActionOutcome.NOT_JUDGED);
    }

    private static DJAnimationCurve curve(float peak) {
        return new DJAnimationCurve(key(0.0f, peak), key(1.0f, 0.0f));
    }

    private static DJAnimationCurve.Keyframe key(float phase, float y) {
        return new DJAnimationCurve.Keyframe(phase,
                new DJAnimationPose(0.0f, y, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f));
    }

    private static DJAnimationClock.ClockSnapshot snapshot(double virtualBeat) {
        return new DJAnimationClock.ClockSnapshot(1_000L, virtualBeat,
                virtualBeat - Math.floor(virtualBeat), true, false, 1, true, List.of());
    }
}

package otto.djgun.djcraft.client.animation;

import java.util.ArrayDeque;
import java.util.EnumMap;

/** Per-hand IDLE/ACTION/IMPULSE state machine driven exclusively by session virtual beat. */
public final class DJFirstPersonAnimator {
    private static final int MAX_PENDING_TRANSITIONS_PER_HAND = 4;
    private static final float IDLE_AMPLITUDE_BLOCKS = 0.035f;

    private final EnumMap<DJAnimationHand, HandAnimationState> hands = new EnumMap<>(DJAnimationHand.class);
    private long generation;

    public DJFirstPersonAnimator() {
        hands.put(DJAnimationHand.MAIN, new HandAnimationState());
        hands.put(DJAnimationHand.OFF, new HandAnimationState());
    }

    public void accept(DJAnimationEvent event) {
        if (generation != event.timelineGeneration()) {
            reset(event.timelineGeneration());
        }
        HandAnimationState state = hands.get(event.hand());
        if (event.sequence() <= state.lastSequence) {
            return;
        }
        state.lastSequence = event.sequence();
        DJAnimationSelection selection = DJAnimationLibrary.getInstance().resolve(event);

        DJAnimationSemantic semantic = event.semantic();
        if (semantic == DJAnimationSemantic.CANCEL) {
            state.clearChannels();
            if (selection.profile().curve() != null) {
                state.action = new ActiveEvent(event, selection);
            }
            return;
        }
        if (semantic == DJAnimationSemantic.READY) {
            state.action = null;
            if (selection.profile().curve() != null) {
                state.action = new ActiveEvent(event, selection);
            }
            return;
        }

        switch (selection.profile().channel()) {
            case IMPULSE -> {
                if (semantic == DJAnimationSemantic.CHARGE_RELEASE
                        || semantic == DJAnimationSemantic.USE_RELEASE) {
                    state.action = null;
                }
                state.impulses.clear();
                state.impulses.addLast(new ImpulseState(event, selection));
            }
            case TRANSITION -> {
                if (semantic == DJAnimationSemantic.UNEQUIP_START) {
                    DJAnimationPose carryPose =
                            transitionPoseAt(state, event.renderIdentity(), event.virtualBeat());
                    state.transitions.clear();
                    state.transitions.addLast(new TransitionState(event, selection, carryPose));
                } else {
                    state.transitions.removeIf(transition ->
                            transition.event.semantic() != DJAnimationSemantic.UNEQUIP_START);
                    if (state.transitions.size() == MAX_PENDING_TRANSITIONS_PER_HAND) {
                        state.transitions.removeFirst();
                    }
                    state.transitions.addLast(
                            new TransitionState(event, selection, DJAnimationPose.IDENTITY));
                }
            }
            case ACTION -> {
                DJAnimationProfile next = selection.profile();
                if (state.action == null
                        || next.priority() >= state.action.selection().profile().priority()) {
                    state.action = new ActiveEvent(event, selection);
                }
            }
        }
    }

    public DJAnimationPose sample(DJAnimationHand hand, Object renderedIdentity,
            DJAnimationClock.ClockSnapshot clock) {
        String itemIdentity = renderedIdentity instanceof net.minecraft.world.item.Item item
                ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString() : "djcraft:unknown";
        return sample(hand, renderedIdentity, itemIdentity, clock);
    }

    public DJAnimationPose sample(DJAnimationHand hand, Object renderedIdentity, String itemIdentity,
            DJAnimationClock.ClockSnapshot clock) {
        if (generation != clock.timelineGeneration()) {
            reset(clock.timelineGeneration());
        }

        HandAnimationState state = hands.get(hand);
        float handSign = hand == DJAnimationHand.MAIN ? 1.0f : -1.0f;
        DJAnimationPose result = sampleIdle(state, renderedIdentity, itemIdentity, clock).mirrored(handSign);

        ActiveEvent activeAction = state.action;
        DJAnimationEvent action = activeAction == null ? null : activeAction.event();
        if (action != null && action.renderIdentity() != renderedIdentity) {
            state.action = null;
            activeAction = null;
            action = null;
        }
        if (action != null && action.renderIdentity() == renderedIdentity) {
            DJAnimationProfile profile = activeAction.selection().profile();
            double durationBeats = activeAction.selection().durationBeats(action);
            double elapsedBeats = clock.virtualBeat() - action.virtualBeat();
            float phase01 = durationBeats <= 0.0 ? 1.0f : clamp01(elapsedBeats / durationBeats);

            if (profile.curve() != null) {
                DJAnimationPose curvePose = profile.curve().sample(phase01);
                result = result.plus(curvePose.mirrored(handSign));
                if (phase01 >= 1.0f) {
                    state.action = null;
                }
            } else if (action.semantic() == DJAnimationSemantic.RELOAD_START
                    || action.semantic() == DJAnimationSemantic.INSPECT_START) {
                float arc = (float) Math.sin(phase01 * Math.PI);
                result = result.plus(handPose(
                        0.0f, profile.translationYBlocks() * arc, profile.translationZBlocks() * arc,
                        profile.rotationXDegrees() * arc, 0.0f,
                        profile.rotationZDegrees() * arc * handSign));
                if (phase01 >= 1.0f) {
                    state.action = null;
                }
            } else {
                float held = smoothstep(phase01);
                result = result.plus(handPose(
                        0.0f, profile.translationYBlocks() * held, profile.translationZBlocks() * held,
                        profile.rotationXDegrees() * held, 0.0f,
                        profile.rotationZDegrees() * held * handSign));
                if (phase01 >= 1.0f
                        && action.semantic() != DJAnimationSemantic.CHARGE_START
                        && action.semantic() != DJAnimationSemantic.USE_START) {
                    state.action = null;
                }
            }
        }

        var transitionIterator = state.transitions.iterator();
        while (transitionIterator.hasNext()) {
            TransitionState transitionState = transitionIterator.next();
            DJAnimationEvent transition = transitionState.event;
            DJAnimationProfile profile = transitionState.selection().profile();
            double durationBeats = transitionState.selection().durationBeats(transition);
            double elapsedBeats = clock.virtualBeat() - transition.virtualBeat();
            boolean awaitingStart = elapsedBeats < 0.0;
            float lifecyclePhase = awaitingStart
                    ? 0.0f
                    : durationBeats <= 0.0 ? 1.0f : clamp01(elapsedBeats / durationBeats);
            float curvePhase = transition.semantic() == DJAnimationSemantic.UNEQUIP_START
                    ? clamp01(lifecyclePhase * 2.0f)
                    : lifecyclePhase;

            // If vanilla swaps its cached item early, keep the incoming item below the frame
            // until the scheduled 1:1 hand-off point instead of flashing it at the idle pose.
            boolean shouldRender = transition.renderIdentity() == renderedIdentity
                    && (!awaitingStart || transition.semantic() != DJAnimationSemantic.UNEQUIP_START);
            if (shouldRender && profile.curve() != null) {
                DJAnimationPose curvePose = withCarry(
                        profile.curve().sample(curvePhase), transitionState.carryPose, curvePhase);
                result = result.plus(curvePose.mirrored(handSign));
            }
            if (!awaitingStart && lifecyclePhase >= 1.0f) {
                transitionIterator.remove();
            }
        }

        var iterator = state.impulses.iterator();
        while (iterator.hasNext()) {
            ImpulseState impulse = iterator.next();
            DJAnimationEvent event = impulse.event;
            if (impulse.playbackIdentity != null && impulse.playbackIdentity != renderedIdentity) {
                iterator.remove();
                continue;
            }
            boolean bridgesActiveSwitch = event.renderIdentity() != renderedIdentity
                    && bridgesActiveSwitch(state, event.renderIdentity(), renderedIdentity, clock);
            if (event.renderIdentity() != renderedIdentity && !bridgesActiveSwitch) {
                double mismatchedDuration = impulse.selection.durationBeats(event);
                boolean awaitingMatchingTransition = state.transitions.stream().anyMatch(transition ->
                        transition.event.renderIdentity() == event.renderIdentity()
                                && clock.virtualBeat() < transition.event.virtualBeat()
                                        + transition.selection().durationBeats(transition.event));
                if (!impulse.sampled && awaitingMatchingTransition) {
                    impulse.deferredForIdentity = true;
                } else if (clock.virtualBeat() - event.virtualBeat() >= mismatchedDuration) {
                    iterator.remove();
                }
                continue;
            }
            DJAnimationProfile profile = impulse.selection.profile();
            double durationBeats = impulse.selection.durationBeats(event);
            if (impulse.deferredForIdentity && !Double.isFinite(impulse.playbackStartVirtualBeat)) {
                impulse.playbackStartVirtualBeat = clock.virtualBeat();
            }
            double playbackStart = Double.isFinite(impulse.playbackStartVirtualBeat)
                    ? impulse.playbackStartVirtualBeat
                    : event.virtualBeat();
            double elapsedBeats = Math.max(0.0, clock.virtualBeat() - playbackStart);
            float phase01 = durationBeats <= 0.0 ? 1.0f : clamp01(elapsedBeats / durationBeats);
            if (!impulse.sampled) {
                // Preserve one visible impact frame when a low frame rate skipped the entire short impulse.
                phase01 = 0.0f;
                impulse.sampled = true;
                impulse.playbackIdentity = renderedIdentity;
            }
            if (profile.curve() != null) {
                DJAnimationPose curvePose = profile.curve().sample(phase01);
                result = result.plus(curvePose.mirrored(handSign));
            } else {
                float recovery = 1.0f - smoothstep(phase01);
                result = result.plus(handPose(
                        0.0f, profile.translationYBlocks() * recovery,
                        profile.translationZBlocks() * recovery,
                        profile.rotationXDegrees() * recovery, 0.0f,
                        profile.rotationZDegrees() * recovery * handSign));
            }
            if (phase01 >= 1.0f) {
                iterator.remove();
            }
        }

        return result;
    }

    public void reset(long timelineGeneration) {
        generation = timelineGeneration;
        for (HandAnimationState state : hands.values()) {
            state.lastSequence = 0L;
            state.clearChannels();
        }
    }

    private static DJAnimationPose sampleIdle(HandAnimationState state, Object renderedIdentity,
            String itemIdentity, DJAnimationClock.ClockSnapshot clock) {
        if (state.idleIdentity != renderedIdentity || !itemIdentity.equals(state.idleItemIdentity)) {
            state.idleIdentity = renderedIdentity;
            state.idleItemIdentity = itemIdentity;
            state.idle = DJAnimationLibrary.getInstance().resolveIdle(itemIdentity, renderedIdentity);
        }
        if (state.idle == null) {
            return new DJAnimationPose(0.0f,
                    -IDLE_AMPLITUDE_BLOCKS * (float) Math.cos(clock.beatFraction() * Math.PI * 2.0),
                    0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        }
        double cycles = clock.virtualBeat() / state.idle.durationBeats();
        float phase = (float) (cycles - Math.floor(cycles));
        return state.idle.curve().sample(phase);
    }

    private static boolean bridgesActiveSwitch(HandAnimationState state, Object eventIdentity,
            Object renderedIdentity, DJAnimationClock.ClockSnapshot clock) {
        boolean eventBelongsToSwitch = state.transitions.stream().anyMatch(transition ->
                transition.event.renderIdentity() == eventIdentity
                        && clock.virtualBeat() < transition.event.virtualBeat()
                                + transition.selection().durationBeats(transition.event));
        if (!eventBelongsToSwitch) {
            return false;
        }

        boolean equipStarted = state.transitions.stream().anyMatch(transition ->
                transition.event.semantic() != DJAnimationSemantic.UNEQUIP_START
                        && clock.virtualBeat() >= transition.event.virtualBeat());
        return state.transitions.stream().anyMatch(transition -> {
            DJAnimationEvent transitionEvent = transition.event;
            if (transitionEvent.renderIdentity() != renderedIdentity
                    || clock.virtualBeat() < transitionEvent.virtualBeat()
                    || clock.virtualBeat() >= transitionEvent.virtualBeat()
                            + transition.selection().durationBeats(transitionEvent)) {
                return false;
            }
            return transitionEvent.semantic() != DJAnimationSemantic.UNEQUIP_START
                    || (transitionEvent.semantic() == DJAnimationSemantic.UNEQUIP_START && !equipStarted);
        });
    }

    private static DJAnimationPose transitionPoseAt(HandAnimationState state, Object renderedIdentity,
            double virtualBeat) {
        DJAnimationPose result = DJAnimationPose.IDENTITY;
        for (TransitionState transitionState : state.transitions) {
            DJAnimationEvent event = transitionState.event;
            if (event.renderIdentity() != renderedIdentity) {
                continue;
            }
            DJAnimationProfile profile = transitionState.selection().profile();
            double duration = transitionState.selection().durationBeats(event);
            double elapsed = virtualBeat - event.virtualBeat();
            if (elapsed < 0.0 || elapsed >= duration || profile.curve() == null) {
                continue;
            }
            float lifecyclePhase = duration <= 0.0 ? 1.0f : clamp01(elapsed / duration);
            float curvePhase = event.semantic() == DJAnimationSemantic.UNEQUIP_START
                    ? clamp01(lifecyclePhase * 2.0f)
                    : lifecyclePhase;
            result = addPoses(result, withCarry(
                    profile.curve().sample(curvePhase), transitionState.carryPose, curvePhase));
        }
        return result;
    }

    private static DJAnimationPose withCarry(DJAnimationPose curvePose, DJAnimationPose carryPose,
            float curvePhase) {
        if (carryPose.equals(DJAnimationPose.IDENTITY)) {
            return curvePose;
        }
        float curveWeight = smoothstep(curvePhase);
        return carryPose.interpolate(curvePose, curveWeight);
    }

    private static DJAnimationPose addPoses(DJAnimationPose first, DJAnimationPose second) {
        return first.plus(second);
    }

    private static DJAnimationPose handPose(float tx, float ty, float tz, float rx, float ry, float rz) {
        return new DJAnimationPose(tx, ty, tz, rx, ry, rz, 1.0f);
    }

    static float clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0f;
        }
        return (float) Math.clamp(value, 0.0, 1.0);
    }

    static float smoothstep(float phase01) {
        float phase = Math.clamp(phase01, 0.0f, 1.0f);
        return phase * phase * (3.0f - 2.0f * phase);
    }

    private static final class HandAnimationState {
        private long lastSequence;
        private ActiveEvent action;
        private final ArrayDeque<TransitionState> transitions = new ArrayDeque<>();
        private final ArrayDeque<ImpulseState> impulses = new ArrayDeque<>();
        private Object idleIdentity;
        private String idleItemIdentity;
        private DJAnimationLibrary.IdleSelection idle;

        private void clearChannels() {
            action = null;
            transitions.clear();
            impulses.clear();
            idleIdentity = null;
            idleItemIdentity = null;
            idle = null;
        }
    }

    private record ActiveEvent(DJAnimationEvent event, DJAnimationSelection selection) {
    }

    private record TransitionState(DJAnimationEvent event, DJAnimationSelection selection,
            DJAnimationPose carryPose) {
    }

    private static final class ImpulseState {
        private final DJAnimationEvent event;
        private final DJAnimationSelection selection;
        private boolean sampled;
        private boolean deferredForIdentity;
        private double playbackStartVirtualBeat = Double.NaN;
        private Object playbackIdentity;

        private ImpulseState(DJAnimationEvent event, DJAnimationSelection selection) {
            this.event = event;
            this.selection = selection;
        }
    }
}

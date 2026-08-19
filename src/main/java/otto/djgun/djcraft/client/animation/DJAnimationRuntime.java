package otto.djgun.djcraft.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import otto.djgun.djcraft.session.DJSessionClient;
import otto.djgun.djcraft.sound.DJActionOutcome;
import otto.djgun.djcraft.client.sound.DJWeaponSoundRuntime;

import java.util.EnumMap;

/** Client-only facade that creates semantic events and renders the unified first-person pose. */
public final class DJAnimationRuntime {
    private static final DJAnimationRuntime INSTANCE = new DJAnimationRuntime();
    private static final Object CANCEL_IDENTITY = new Object();

    private final DJAnimationClock clock = new DJAnimationClock();
    private final DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
    private final EnumMap<DJAnimationHand, SwitchWindow> switchWindows = new EnumMap<>(DJAnimationHand.class);
    private final EnumMap<DJAnimationHand, Item> renderedItems = new EnumMap<>(DJAnimationHand.class);
    private final EnumMap<DJAnimationHand, PendingRenderPose> pendingRenderPoses =
            new EnumMap<>(DJAnimationHand.class);
    private long sequence;
    private long sequenceGeneration;
    private DJAnimationEvent lastEvent;
    private DJAnimationClock.ClockSnapshot lastSnapshot;

    private DJAnimationRuntime() {
    }

    public static DJAnimationRuntime getInstance() {
        return INSTANCE;
    }

    public void emit(DJAnimationSemantic semantic, InteractionHand hand, ItemStack stack,
            DJSessionClient session, double durationBeats, DJActionOutcome outcome) {
        emit(semantic, hand, stack, session, durationBeats, outcome, 0L, 0L, -1);
    }

    public void emit(DJAnimationSemantic semantic, InteractionHand hand, ItemStack stack,
            DJSessionClient session, double durationBeats, DJActionOutcome outcome,
            long actionSequence, long judgedAtMs, int beatIndex) {
        if (semantic == null || stack.isEmpty() || hand == null
                || !Double.isFinite(durationBeats) || durationBeats < 0.0) {
            return;
        }
        DJAnimationClock.ClockSnapshot snapshot = clock.sample(session);
        emitAt(semantic, hand, stack, snapshot, snapshot.virtualBeat(), durationBeats, outcome,
                actionSequence, judgedAtMs, beatIndex);
    }

    /** @deprecated Use a registered {@link DJAnimationSemantic}. */
    @Deprecated(forRemoval = false)
    public void emit(DJAnimationEvent.Kind kind, InteractionHand hand, ItemStack stack,
            DJSessionClient session, double durationBeats, DJActionOutcome outcome) {
        emit(kind.semantic(), hand, stack, session, durationBeats, outcome);
    }

    /** @deprecated Use a registered {@link DJAnimationSemantic}. */
    @Deprecated(forRemoval = false)
    public void emit(DJAnimationEvent.Kind kind, InteractionHand hand, ItemStack stack,
            DJSessionClient session, double durationBeats, DJActionOutcome outcome,
            long actionSequence, long judgedAtMs, int beatIndex) {
        emit(kind.semantic(), hand, stack, session, durationBeats, outcome,
                actionSequence, judgedAtMs, beatIndex);
    }

    /** Emits a server-confirmed visual event whose sound is delivered by a separate broadcast payload. */
    public void emitVisualOnly(DJAnimationSemantic semantic, InteractionHand hand, ItemStack stack,
            DJSessionClient session, double durationBeats, DJActionOutcome outcome) {
        if (semantic == null || stack.isEmpty() || hand == null
                || !Double.isFinite(durationBeats) || durationBeats < 0.0) {
            return;
        }
        DJAnimationClock.ClockSnapshot snapshot = clock.sample(session);
        prepareSequence(snapshot);
        DJAnimationHand animationHand = toAnimationHand(hand);
        lastEvent = new DJAnimationEvent(++sequence, snapshot.timelineGeneration(), animationHand, stack.getItem(),
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), semantic, snapshot.sessionTimeMs(),
                snapshot.virtualBeat(), durationBeats, outcome);
        animator.accept(lastEvent);
    }

    /** @deprecated Use a registered {@link DJAnimationSemantic}. */
    @Deprecated(forRemoval = false)
    public void emitVisualOnly(DJAnimationEvent.Kind kind, InteractionHand hand, ItemStack stack,
            DJSessionClient session, double durationBeats, DJActionOutcome outcome) {
        emitVisualOnly(kind.semantic(), hand, stack, session, durationBeats, outcome);
    }

    public void scheduleSwitch(InteractionHand hand, ItemStack outgoingStack, ItemStack incomingStack,
            DJSessionClient session, double totalDurationBeats) {
        if (hand == null || outgoingStack == null || incomingStack == null
                || !Double.isFinite(totalDurationBeats) || totalDurationBeats <= 0.0
                || (outgoingStack.isEmpty() && incomingStack.isEmpty())) {
            return;
        }
        DJAnimationClock.ClockSnapshot snapshot = clock.sample(session);
        lastSnapshot = snapshot;
        DJAnimationHand animationHand = toAnimationHand(hand);
        Item renderedItem = renderedItems.get(animationHand);
        ItemStack effectiveOutgoingStack = renderedItem == null
                ? outgoingStack
                : new ItemStack(renderedItem);
        SwitchWindow currentWindow = switchWindows.get(animationHand);
        Item effectiveOutgoingItem = itemOrNull(effectiveOutgoingStack);
        Item incomingItem = itemOrNull(incomingStack);
        if (effectiveOutgoingItem == incomingItem) {
            return;
        }
        if (currentWindow != null
                && currentWindow.alreadyTargets(snapshot, effectiveOutgoingItem, incomingItem)) {
            return;
        }
        if (currentWindow != null && currentWindow.canRetarget(snapshot, itemOrNull(effectiveOutgoingStack))) {
            double equipDuration = totalDurationBeats * 0.5;
            DJAnimationEvent equipEvent = incomingStack.isEmpty()
                    ? null
                    : emitAt(DJAnimationSemantic.EQUIP_START, hand, incomingStack, snapshot,
                            currentWindow.handoffBeat(), equipDuration, DJActionOutcome.NOT_JUDGED);
            double redirectedEndBeat = incomingStack.isEmpty()
                    ? currentWindow.handoffBeat()
                    : currentWindow.handoffBeat() + equipDuration;
            switchWindows.put(animationHand, new SwitchWindow(snapshot.timelineGeneration(),
                    itemOrNull(effectiveOutgoingStack), itemOrNull(incomingStack), currentWindow.startBeat(),
                    currentWindow.handoffBeat(), redirectedEndBeat, currentWindow.unequipEvent(), equipEvent, false));
            return;
        }
        DJAnimationEvent unequipEvent = null;
        DJAnimationEvent equipEvent = null;
        double startBeat = snapshot.virtualBeat();
        double handoffBeat;
        double endBeat = startBeat + totalDurationBeats;
        if (!effectiveOutgoingStack.isEmpty() && !incomingStack.isEmpty()) {
            double halfDuration = totalDurationBeats * 0.5;
            handoffBeat = startBeat + halfDuration;
            unequipEvent = emitAt(DJAnimationSemantic.UNEQUIP_START, hand, effectiveOutgoingStack, snapshot,
                    startBeat, totalDurationBeats, DJActionOutcome.NOT_JUDGED);
            equipEvent = emitAt(DJAnimationSemantic.EQUIP_START, hand, incomingStack, snapshot,
                    handoffBeat, halfDuration, DJActionOutcome.NOT_JUDGED);
        } else if (!effectiveOutgoingStack.isEmpty()) {
            handoffBeat = endBeat;
            unequipEvent = emitAt(DJAnimationSemantic.UNEQUIP_START, hand, effectiveOutgoingStack, snapshot,
                    startBeat, totalDurationBeats, DJActionOutcome.NOT_JUDGED);
        } else {
            handoffBeat = startBeat;
            equipEvent = emitAt(DJAnimationSemantic.EQUIP_START, hand, incomingStack, snapshot,
                    startBeat, totalDurationBeats, DJActionOutcome.NOT_JUDGED);
        }
        switchWindows.put(animationHand, new SwitchWindow(snapshot.timelineGeneration(),
                itemOrNull(effectiveOutgoingStack), itemOrNull(incomingStack), startBeat, handoffBeat, endBeat,
                unequipEvent, equipEvent, false));
    }

    public void cancel(InteractionHand hand, DJSessionClient session) {
        if (hand == null) {
            return;
        }
        DJAnimationClock.ClockSnapshot snapshot = clock.sample(session);
        lastSnapshot = snapshot;
        if (sequenceGeneration != snapshot.timelineGeneration()) {
            sequenceGeneration = snapshot.timelineGeneration();
            sequence = 0L;
        }
        DJAnimationHand animationHand = hand == InteractionHand.MAIN_HAND ? DJAnimationHand.MAIN : DJAnimationHand.OFF;
        switchWindows.remove(animationHand);
        lastEvent = new DJAnimationEvent(++sequence, snapshot.timelineGeneration(), animationHand,
                CANCEL_IDENTITY, "djcraft:cancel", DJAnimationSemantic.CANCEL,
                snapshot.sessionTimeMs(), snapshot.virtualBeat(), 0.0, DJActionOutcome.NOT_JUDGED);
        animator.accept(lastEvent);
        DJWeaponSoundRuntime.getInstance().onAnimationEvent(lastEvent, hand, ItemStack.EMPTY, 0L, 0L, -1);
    }

    public void beginFirstPersonRender(InteractionHand renderedHand, ItemStack renderedStack, PoseStack poseStack,
            DJSessionClient session) {
        DJAnimationHand hand = animationHand(renderedHand);
        if (renderedStack.isEmpty()) {
            pendingRenderPoses.remove(hand);
            return;
        }
        DJAnimationPose pose = samplePose(renderedHand, renderedStack, session);
        pendingRenderPoses.put(hand, new PendingRenderPose(renderedStack.getItem(), pose));
        DJFirstPersonPoseApplier.applyHandSpace(pose, poseStack);
    }

    public void applyItemCenterPose(InteractionHand renderedHand, ItemStack renderedStack, PoseStack poseStack,
            DJSessionClient session) {
        DJAnimationHand hand = animationHand(renderedHand);
        PendingRenderPose pending = pendingRenderPoses.remove(hand);
        DJAnimationPose pose = pending != null && pending.item() == renderedStack.getItem()
                ? pending.pose()
                : samplePose(renderedHand, renderedStack, session);
        DJFirstPersonPoseApplier.applyItemCenterSpace(pose, poseStack);
    }

    public DJAnimationPose samplePose(InteractionHand renderedHand, ItemStack renderedStack,
            DJSessionClient session) {
        DJAnimationClock.ClockSnapshot snapshot = clock.sample(session);
        lastSnapshot = snapshot;
        DJAnimationHand hand = animationHand(renderedHand);
        if (renderedStack.isEmpty()) {
            renderedItems.remove(hand);
        } else {
            renderedItems.put(hand, renderedStack.getItem());
        }
        return animator.sample(hand, renderedStack.getItem(),
                BuiltInRegistries.ITEM.getKey(renderedStack.getItem()).toString(), snapshot);
    }

    private static DJAnimationHand animationHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? DJAnimationHand.MAIN : DJAnimationHand.OFF;
    }

    public DJAnimationClock.ClockSnapshot snapshot(DJSessionClient session) {
        lastSnapshot = clock.sample(session);
        return lastSnapshot;
    }

    public DJAnimationEvent lastEvent() {
        return lastEvent;
    }

    private record PendingRenderPose(Item item, DJAnimationPose pose) {
    }

    /** Returns the transition stage that should currently be visible, rather than the last queued stage. */
    public DJAnimationEvent activeTransition() {
        DJAnimationClock.ClockSnapshot snapshot = lastSnapshot;
        if (snapshot == null) {
            return null;
        }
        DJAnimationEvent newest = null;
        for (SwitchWindow window : switchWindows.values()) {
            DJAnimationEvent active = window.activeEvent(snapshot);
            if (active != null && (newest == null || active.sequence() > newest.sequence())) {
                newest = active;
            }
        }
        return newest;
    }

    /** Keeps vanilla's cached outgoing item alive until the midpoint handoff to the incoming item. */
    public boolean shouldHoldOutgoing(InteractionHand hand, ItemStack cachedStack, ItemStack nextStack,
            DJSessionClient session) {
        if (hand == null || cachedStack == null) {
            return false;
        }
        observeRenderedStack(hand, cachedStack);
        if (cachedStack.isEmpty()) {
            return false;
        }
        DJAnimationClock.ClockSnapshot snapshot = clock.sample(session);
        lastSnapshot = snapshot;
        SwitchWindow window = switchWindows.get(toAnimationHand(hand));
        return window != null && window.shouldHold(cachedStack, nextStack, snapshot);
    }

    public void observeRenderedStack(InteractionHand hand, ItemStack stack) {
        if (hand == null || stack == null) {
            return;
        }
        DJAnimationHand animationHand = toAnimationHand(hand);
        if (stack.isEmpty()) {
            renderedItems.remove(animationHand);
        } else {
            renderedItems.put(animationHand, stack.getItem());
        }
    }

    public boolean shouldCommitHandoff(InteractionHand hand, ItemStack cachedStack, ItemStack selectedStack,
            DJSessionClient session) {
        if (hand == null || cachedStack == null || selectedStack == null) {
            return false;
        }
        DJAnimationClock.ClockSnapshot snapshot = clock.sample(session);
        lastSnapshot = snapshot;
        SwitchWindow window = switchWindows.get(toAnimationHand(hand));
        return window != null && window.shouldCommit(cachedStack, selectedStack, snapshot);
    }

    /** Marks the exact frame/tick where the renderer actually starts using the incoming item. */
    public void onCachedItemAssigned(InteractionHand hand, ItemStack assignedStack, DJSessionClient session) {
        if (hand == null || assignedStack == null) {
            return;
        }
        observeRenderedStack(hand, assignedStack);
        DJAnimationHand animationHand = toAnimationHand(hand);
        SwitchWindow window = switchWindows.get(animationHand);
        if (window == null || window.committed()) {
            return;
        }
        DJAnimationClock.ClockSnapshot snapshot = clock.sample(session);
        lastSnapshot = snapshot;
        if (!window.matchesIncoming(assignedStack, snapshot)) {
            return;
        }

        DJAnimationEvent equipEvent = window.equipEvent();
        double endBeat = snapshot.virtualBeat();
        if (equipEvent != null && !assignedStack.isEmpty()) {
            double equipDuration = equipEvent.durationBeats();
            equipEvent = emitAt(DJAnimationSemantic.EQUIP_START, hand, assignedStack, snapshot,
                    snapshot.virtualBeat(), equipDuration, DJActionOutcome.NOT_JUDGED);
            endBeat += equipDuration;
        }
        switchWindows.put(animationHand, new SwitchWindow(window.generation(), window.outgoingItem(),
                window.incomingItem(), window.startBeat(), snapshot.virtualBeat(), endBeat,
                window.unequipEvent(), equipEvent, true));
    }

    public DJAnimationClock.ClockSnapshot lastSnapshot() {
        return lastSnapshot;
    }

    public void reset() {
        clock.invalidate();
        sequence = 0L;
        sequenceGeneration = 0L;
        lastEvent = null;
        lastSnapshot = null;
        switchWindows.clear();
        renderedItems.clear();
        pendingRenderPoses.clear();
    }

    /** Clears visual-only playback state after an atomic animation resource snapshot swap. */
    void onAnimationResourcesReloaded() {
        animator.reset(lastSnapshot == null ? 0L : lastSnapshot.timelineGeneration());
        switchWindows.clear();
        renderedItems.clear();
        pendingRenderPoses.clear();
    }

    private DJAnimationEvent emitAt(DJAnimationSemantic semantic, InteractionHand hand, ItemStack stack,
            DJAnimationClock.ClockSnapshot snapshot, double startVirtualBeat,
            double durationBeats, DJActionOutcome outcome) {
        return emitAt(semantic, hand, stack, snapshot, startVirtualBeat, durationBeats, outcome, 0L, 0L, -1);
    }

    private DJAnimationEvent emitAt(DJAnimationSemantic semantic, InteractionHand hand, ItemStack stack,
            DJAnimationClock.ClockSnapshot snapshot, double startVirtualBeat,
            double durationBeats, DJActionOutcome outcome, long actionSequence, long judgedAtMs, int beatIndex) {
        prepareSequence(snapshot);
        DJAnimationHand animationHand = toAnimationHand(hand);
        lastEvent = new DJAnimationEvent(++sequence, snapshot.timelineGeneration(), animationHand, stack.getItem(),
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), semantic, snapshot.sessionTimeMs(),
                startVirtualBeat, durationBeats, outcome);
        animator.accept(lastEvent);
        // Switch scheduling may enqueue EQUIP ahead of its audible handoff. It is emitted again when
        // the renderer actually commits the incoming stack, so only dispatch contemporaneous events here.
        if (startVirtualBeat <= snapshot.virtualBeat() + 1.0e-9) {
            DJWeaponSoundRuntime.getInstance().onAnimationEvent(lastEvent, hand, stack,
                    actionSequence, judgedAtMs, beatIndex);
        }
        return lastEvent;
    }

    private void prepareSequence(DJAnimationClock.ClockSnapshot snapshot) {
        if (sequenceGeneration != snapshot.timelineGeneration()) {
            sequenceGeneration = snapshot.timelineGeneration();
            sequence = 0L;
        }
    }

    private static DJAnimationHand toAnimationHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? DJAnimationHand.MAIN : DJAnimationHand.OFF;
    }

    private static Item itemOrNull(ItemStack stack) {
        return stack.isEmpty() ? null : stack.getItem();
    }

    private record SwitchWindow(long generation, Item outgoingItem, Item incomingItem,
            double startBeat, double handoffBeat, double endBeat,
            DJAnimationEvent unequipEvent, DJAnimationEvent equipEvent, boolean committed) {
        private boolean canRetarget(DJAnimationClock.ClockSnapshot snapshot, Item renderedItem) {
            return snapshot.timelineGeneration() == generation
                    && snapshot.virtualBeat() >= startBeat
                    && snapshot.virtualBeat() < handoffBeat
                    && outgoingItem != null
                    && outgoingItem == renderedItem;
        }

        private boolean alreadyTargets(DJAnimationClock.ClockSnapshot snapshot, Item renderedItem,
                Item targetItem) {
            return snapshot.timelineGeneration() == generation
                    && snapshot.virtualBeat() >= startBeat
                    // An empty-hand handoff happens at endBeat. Keep that uncommitted
                    // target idempotent until the renderer can commit it later in the tick.
                    && (!committed || snapshot.virtualBeat() < endBeat)
                    && outgoingItem == renderedItem
                    && incomingItem == targetItem;
        }

        private DJAnimationEvent activeEvent(DJAnimationClock.ClockSnapshot snapshot) {
            if (snapshot.timelineGeneration() != generation || snapshot.virtualBeat() < startBeat
                    || snapshot.virtualBeat() >= endBeat) {
                return null;
            }
            return snapshot.virtualBeat() < handoffBeat ? unequipEvent : equipEvent;
        }

        private boolean shouldHold(ItemStack cachedStack, ItemStack nextStack,
                DJAnimationClock.ClockSnapshot snapshot) {
            if (snapshot.timelineGeneration() != generation || snapshot.virtualBeat() >= handoffBeat
                    || outgoingItem == null || cachedStack.getItem() != outgoingItem) {
                return false;
            }
            Item nextItem = itemOrNull(nextStack);
            return nextItem == incomingItem;
        }

        private boolean shouldCommit(ItemStack cachedStack, ItemStack selectedStack,
                DJAnimationClock.ClockSnapshot snapshot) {
            if (committed || snapshot.timelineGeneration() != generation
                    || snapshot.virtualBeat() < handoffBeat) {
                return false;
            }
            Item selectedItem = itemOrNull(selectedStack);
            Item cachedItem = itemOrNull(cachedStack);
            return selectedItem == incomingItem && cachedItem != incomingItem;
        }

        private boolean matchesIncoming(ItemStack assignedStack, DJAnimationClock.ClockSnapshot snapshot) {
            return snapshot.timelineGeneration() == generation
                    && snapshot.virtualBeat() >= handoffBeat
                    && itemOrNull(assignedStack) == incomingItem;
        }
    }
}

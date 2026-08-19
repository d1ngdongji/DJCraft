package otto.djgun.djcraft.client.animation;

/** Immutable animation binding captured when a semantic event enters a playback channel. */
record DJAnimationSelection(DJAnimationProfile profile, double resourceDurationBeats) {
    DJAnimationSelection {
        if (profile == null || (!Double.isNaN(resourceDurationBeats)
                && (!Double.isFinite(resourceDurationBeats) || resourceDurationBeats <= 0.0))) {
            throw new IllegalArgumentException("Invalid animation selection");
        }
    }

    double durationBeats(DJAnimationEvent event) {
        if (!Double.isNaN(resourceDurationBeats)) {
            return resourceDurationBeats;
        }
        return event.durationBeats() > 0.0 ? event.durationBeats() : profile.defaultDurationBeats();
    }
}

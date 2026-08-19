package otto.djgun.djcraft.client.ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import icyllis.modernui.animation.BezierInterpolator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.BitmapFactory;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.ImageShader;
import icyllis.modernui.graphics.Matrix;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.graphics.Shader;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.client.playback.DJPlaybackController;
import otto.djgun.djcraft.client.ClientTrackRegistry;
import otto.djgun.djcraft.playback.DJPlaybackMode;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.sound.DJSoundManager;
import otto.djgun.djcraft.data.DiscStatistics;

public final class DJPlayerFragment extends Fragment {
    private final List<DiscPlayerEntry> allowedEntries;
    private List<DiscPlayerEntry> discEntries = List.of();
    private List<TrackPack> trackPacks = List.of();
    private CarouselLayout carousel;
    private TextView titleView;
    private TextView metaView;
    private TextView artistView;
    private TextView positionView;
    private TextView statisticsView;
    private Button actionButton;
    private Button previousButton;
    private Button nextButton;
    private final Map<DJPlaybackMode, Button> modeButtons = new EnumMap<>(DJPlaybackMode.class);
    private int currentIndex;
    private int discSizePx;
    private float scrollOffset;
    private ValueAnimator scrollAnimator;
    private Image emptyDiscImage;
    private Image perfectDiscImage;
    private final Runnable statisticsRefresh = new Runnable() {
        @Override
        public void run() {
            if (statisticsView != null) {
                updateStatistics();
                statisticsView.postDelayed(this, 1_000L);
            }
        }
    };

    public DJPlayerFragment() {
        this(null);
    }

    public DJPlayerFragment(List<DiscPlayerEntry> allowedEntries) {
        this.allowedEntries = allowedEntries == null ? null : List.copyOf(allowedEntries);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Collection<TrackPack> loaded = TrackPackManager.getInstance().getLoadedPacks();
        ArrayList<TrackPack> visible = new ArrayList<>();
        ArrayList<DiscPlayerEntry> visibleEntries = new ArrayList<>();
        if (allowedEntries == null) {
            loaded.stream()
                    .filter(pack -> ClientTrackRegistry.getInstance().isVerified(pack.id()))
                    .forEach(visible::add);
            visible.sort(Comparator.comparing(TrackPack::id, String.CASE_INSENSITIVE_ORDER));
        } else {
            Map<String, TrackPack> byId = new HashMap<>();
            for (TrackPack pack : loaded) {
                byId.put(pack.id(), pack);
            }
            for (DiscPlayerEntry entry : allowedEntries) {
                TrackPack pack = byId.get(entry.reference().trackId());
                if (pack != null && ClientTrackRegistry.getInstance().isVerified(pack.id())) {
                    visible.add(pack);
                    visibleEntries.add(entry);
                }
            }
        }
        trackPacks = List.copyOf(visible);
        discEntries = List.copyOf(visibleEntries);
        List<otto.djgun.djcraft.data.DiscPlaybackReference> visibleRefs = discEntries.stream()
                .map(DiscPlayerEntry::reference).toList();
        DJPlaybackController playback = DJPlaybackController.getInstance();
        var groupState = otto.djgun.djcraft.client.DJNetworkGroupClient.getInstance().state();
        if (groupState.present() && groupState.currentIndex() >= 0
                && groupState.currentIndex() < visibleRefs.size()) {
            currentIndex = groupState.currentIndex();
        } else if (visibleRefs.equals(playback.getPlaylist())
                && playback.getCurrentIndex() >= 0 && playback.getCurrentIndex() < visibleRefs.size()) {
            currentIndex = playback.getCurrentIndex();
        } else {
            java.util.UUID playingDisc = DJModeManagerClient.getInstance().getCurrentDiscId();
            if (playingDisc != null) {
                int index = -1;
                for (int i = 0; i < visibleRefs.size(); i++) {
                    if (playingDisc.equals(visibleRefs.get(i).discId())) {
                        index = i;
                        break;
                    }
                }
                currentIndex = Math.max(0, index);
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable DataSet savedInstanceState) {
        Context context = requireContext();
        int dp8 = DJUiTheme.dp(context, 8);
        int dp16 = DJUiTheme.dp(context, 16);
        int dp20 = DJUiTheme.dp(context, 20);

        FrameLayout root = new FrameLayout(context);
        root.setBackground(DJUiTheme.fill(DJUiTheme.BACKGROUND));

        FrameLayout header = new FrameLayout(context);
        header.setPadding(dp20, 0, dp16, 0);
        header.setBackground(DJUiTheme.fill(DJUiTheme.SURFACE));
        TextView brand = text(context, DJUiTheme.text("ui.djcraft.player.title"), 20,
                DJUiTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(brand, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
        Button close = iconButton(context, "\u00d7", DJUiTheme.text("ui.djcraft.close"));
        close.setOnClickListener(view -> ClientScreenBridge.closeScreen());
        header.addView(close, new FrameLayout.LayoutParams(
                DJUiTheme.dp(context, 42), DJUiTheme.dp(context, 42), Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        Button group = headerButton(context, DJUiTheme.text("ui.djcraft.group.title"));
        group.setOnClickListener(view -> {
            int slot = discEntries.isEmpty() ? -1 : discEntries.get(0).reference().jukeboxInventorySlot();
            ClientScreenBridge.openScreen(new DJNetworkFragment(slot));
        });
        FrameLayout.LayoutParams groupParams = new FrameLayout.LayoutParams(
                DJUiTheme.dp(context, 76), DJUiTheme.dp(context, 36), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        groupParams.rightMargin = DJUiTheme.dp(context, 216);
        header.addView(group, groupParams);
        if (otto.djgun.djcraft.client.CyberGrindClientState.getInstance().isActive()) {
            Button exitArena = headerButton(context, DJUiTheme.text("ui.djcraft.cyber_grind.exit"));
            exitArena.setOnClickListener(view -> ClientScreenBridge.openScreen(
                    new CyberGrindExitConfirmFragment()));
            FrameLayout.LayoutParams exitParams = new FrameLayout.LayoutParams(
                    DJUiTheme.dp(context, 96), DJUiTheme.dp(context, 36),
                    Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            exitParams.rightMargin = DJUiTheme.dp(context, 400);
            header.addView(exitArena, exitParams);
        }
        Button downloads = headerButton(context, DJUiTheme.text("ui.djcraft.download.title"));
        downloads.setOnClickListener(view -> ClientScreenBridge.openScreen(new DJDownloadFragment()));
        FrameLayout.LayoutParams downloadParams = new FrameLayout.LayoutParams(
                DJUiTheme.dp(context, 84), DJUiTheme.dp(context, 36),
                Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        downloadParams.rightMargin = DJUiTheme.dp(context, 304);
        header.addView(downloads, downloadParams);
        Button reloadTrackPacks = headerButton(context, DJUiTheme.text("ui.djcraft.trackpacks.reload"));
        reloadTrackPacks.setOnClickListener(view -> DJTrackPackUiActions.reloadTrackPacks(
                () -> ClientScreenBridge.openScreen(new DJPlayerFragment(allowedEntries))));
        FrameLayout.LayoutParams reloadParams = new FrameLayout.LayoutParams(
                DJUiTheme.dp(context, 72), DJUiTheme.dp(context, 36), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        reloadParams.rightMargin = DJUiTheme.dp(context, 48);
        header.addView(reloadTrackPacks, reloadParams);
        Button openTrackPackDirectory = headerButton(context, DJUiTheme.text("ui.djcraft.trackpacks.open"));
        openTrackPackDirectory.setOnClickListener(view -> DJTrackPackUiActions.openTrackPackDirectory());
        FrameLayout.LayoutParams openDirectoryParams = new FrameLayout.LayoutParams(
                DJUiTheme.dp(context, 92), DJUiTheme.dp(context, 36), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        openDirectoryParams.rightMargin = DJUiTheme.dp(context, 124);
        header.addView(openTrackPackDirectory, openDirectoryParams);
        root.addView(header, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, DJUiTheme.dp(context, 60), Gravity.TOP));

        LinearLayout modeControl = createModeControl(context);
        FrameLayout.LayoutParams modeParams = new FrameLayout.LayoutParams(
                DJUiTheme.contentWidth(context, 430, 24), DJUiTheme.dp(context, 38),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        modeParams.topMargin = DJUiTheme.dp(context, 70);
        root.addView(modeControl, modeParams);

        actionButton = new Button(context);
        actionButton.setTextSize(15);
        actionButton.setTextColor(DJUiTheme.TEXT_PRIMARY);
        actionButton.setBackground(DJUiTheme.panel(DJUiTheme.ACCENT_DARK, DJUiTheme.ACCENT,
                DJUiTheme.dp(context, 6), DJUiTheme.dp(context, 1)));
        actionButton.setOnClickListener(view -> togglePlayback());
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                DJUiTheme.dp(context, 152), DJUiTheme.dp(context, 42), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        actionParams.topMargin = DJUiTheme.dp(context, 116);
        root.addView(actionButton, actionParams);

        carousel = new CarouselLayout(context);
        FrameLayout.LayoutParams carouselParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        carouselParams.topMargin = DJUiTheme.dp(context, 164);
        carouselParams.bottomMargin = DJUiTheme.dp(context, 126);
        root.addView(carousel, carouselParams);

        previousButton = iconButton(context, "\u2039", DJUiTheme.text("ui.djcraft.previous"));
        previousButton.setTextSize(34);
        previousButton.setOnClickListener(view -> scrollToIndex(currentIndex - 1));
        FrameLayout.LayoutParams previousParams = new FrameLayout.LayoutParams(
                DJUiTheme.dp(context, 48), DJUiTheme.dp(context, 56), Gravity.LEFT | Gravity.CENTER_VERTICAL);
        previousParams.leftMargin = dp16;
        root.addView(previousButton, previousParams);

        nextButton = iconButton(context, "\u203a", DJUiTheme.text("ui.djcraft.next"));
        nextButton.setTextSize(34);
        nextButton.setOnClickListener(view -> scrollToIndex(currentIndex + 1));
        FrameLayout.LayoutParams nextParams = new FrameLayout.LayoutParams(
                DJUiTheme.dp(context, 48), DJUiTheme.dp(context, 56), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        nextParams.rightMargin = dp16;
        root.addView(nextButton, nextParams);

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER);
        info.setPadding(dp16, dp8, dp16, dp8);
        titleView = text(context, "", 22, DJUiTheme.TEXT_PRIMARY, Gravity.CENTER);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        artistView = text(context, "", 14, DJUiTheme.WARM, Gravity.CENTER);
        artistView.setSingleLine(true);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        metaView = text(context, "", 12, DJUiTheme.TEXT_SECONDARY, Gravity.CENTER);
        metaView.setSingleLine(true);
        metaView.setEllipsize(TextUtils.TruncateAt.END);
        positionView = text(context, "", 12, DJUiTheme.ACCENT, Gravity.CENTER);
        statisticsView = text(context, "", 12, DJUiTheme.WARM, Gravity.CENTER);
        info.addView(titleView);
        info.addView(artistView);
        info.addView(metaView);
        info.addView(statisticsView);
        LinearLayout.LayoutParams positionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        positionParams.topMargin = DJUiTheme.dp(context, 4);
        info.addView(positionView, positionParams);
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
                DJUiTheme.contentWidth(context, 520, 24), DJUiTheme.dp(context, 128),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        infoParams.bottomMargin = DJUiTheme.dp(context, 8);
        root.addView(info, infoParams);

        populateCarousel(context);
        statisticsView.postDelayed(statisticsRefresh, 1_000L);
        return root;
    }

    @Override
    public void onDetach() {
        if (statisticsView != null) {
            statisticsView.removeCallbacks(statisticsRefresh);
        }
        super.onDetach();
    }

    private void populateCarousel(Context context) {
        carousel.removeAllViews();
        int width = context.getResources().getDisplayMetrics().widthPixels;
        int height = context.getResources().getDisplayMetrics().heightPixels;
        int available = Math.min(width - DJUiTheme.dp(context, 112), height - DJUiTheme.dp(context, 298));
        discSizePx = Math.max(DJUiTheme.dp(context, 116), Math.min(DJUiTheme.dp(context, 210), available));
        for (int index = 0; index < trackPacks.size(); index++) {
            TrackPack pack = trackPacks.get(index);
            DiscPlayerEntry entry = index < discEntries.size() ? discEntries.get(index) : null;
            View disc = createDiscView(context, pack, entry);
            carousel.addView(disc, new FrameLayout.LayoutParams(discSizePx, discSizePx));
        }
        scrollOffset = currentIndex;
        updateSelection();
        carousel.requestLayout();
    }

    private void scrollToIndex(int index) {
        if (index < 0 || index >= trackPacks.size() || index == currentIndex) {
            return;
        }
        currentIndex = index;
        updateSelection();
        if (scrollAnimator != null && scrollAnimator.isRunning()) {
            scrollAnimator.cancel();
        }
        scrollAnimator = ValueAnimator.ofFloat(scrollOffset, currentIndex);
        scrollAnimator.setDuration(260);
        scrollAnimator.setInterpolator(new BezierInterpolator(0.22f, 0.7f, 0.22f, 1f));
        scrollAnimator.addUpdateListener(animation -> {
            scrollOffset = (float) animation.getAnimatedValue();
            carousel.requestLayout();
        });
        scrollAnimator.start();
    }

    private void updateSelection() {
        boolean empty = trackPacks.isEmpty();
        previousButton.setEnabled(!empty && currentIndex > 0);
        nextButton.setEnabled(!empty && currentIndex < trackPacks.size() - 1);
        previousButton.setAlpha(previousButton.isEnabled() ? 1f : 0.3f);
        nextButton.setAlpha(nextButton.isEnabled() ? 1f : 0.3f);
        actionButton.setEnabled(!empty);
        if (otto.djgun.djcraft.client.CyberGrindClientState.getInstance().isActive()) {
            actionButton.setEnabled(false);
        }
        if (otto.djgun.djcraft.client.DJNetworkGroupClient.getInstance().isInGroup()
                && !otto.djgun.djcraft.client.DJNetworkGroupClient.getInstance().isOwner()) {
            actionButton.setEnabled(false);
        }
        actionButton.setAlpha(actionButton.isEnabled() ? 1f : 0.4f);
        updateActionLabel();

        if (empty) {
            titleView.setText(DJUiTheme.text("ui.djcraft.empty.title"));
            artistView.setText(DJUiTheme.text("ui.djcraft.player.empty"));
            metaView.setText("");
            positionView.setText("");
            return;
        }

        TrackPack pack = trackPacks.get(currentIndex);
        titleView.setText(TextColorHelper.parse(displayName(pack)));
        String author = pack.meta().author();
        artistView.setText(TextColorHelper.parse(author == null || author.isBlank()
                ? DJUiTheme.text("ui.djcraft.unknown_author") : author));
        metaView.setText(DJUiTheme.text("ui.djcraft.player.meta", pack.getBpm(), pack.getCombatBeatCount()));
        positionView.setText(DJUiTheme.text("ui.djcraft.position", currentIndex + 1, trackPacks.size()));
        updateStatistics();
    }

    private void updateStatistics() {
        if (statisticsView == null || discEntries.isEmpty() || currentIndex >= discEntries.size()) {
            if (statisticsView != null) statisticsView.setText("");
            return;
        }
        DiscPlayerEntry entry = discEntries.get(currentIndex);
        DiscStatistics stored = entry.statistics();
        int maxCombo = stored.maxCombo();
        long totalMs = stored.totalPlayTimeMs();
        var manager = DJModeManagerClient.getInstance();
        if (entry.reference().discId() != null
                && entry.reference().discId().equals(manager.getCurrentDiscId())) {
            var session = manager.getActiveSession().orElse(null);
            if (session != null) {
                maxCombo = Math.max(maxCombo, session.getCurrentTrackMaxCombo());
                long delta = Math.max(0L, session.getPlaybackTimeMs() - entry.snapshotPlaybackMs());
                totalMs = saturatedAdd(totalMs, delta);
            }
        }
        statisticsView.setText(DJUiTheme.text("ui.djcraft.player.statistics", maxCombo, formatDuration(totalMs)));
    }

    private void updateActionLabel() {
        boolean playing = DJSoundManager.getInstance().isPlaying();
        actionButton.setText(playing
                ? "\u25a0  " + DJUiTheme.text("ui.djcraft.stop")
                : "\u25b6  " + DJUiTheme.text("ui.djcraft.play"));
    }

    private void togglePlayback() {
        if (trackPacks.isEmpty()) {
            return;
        }
        if (DJSoundManager.getInstance().isPlaying()) {
            DJModeManagerClient.getInstance().getActiveSession().ifPresent(session ->
                    DJPlaybackController.getInstance().stopCurrent(session.getSessionId()));
            actionButton.setText("\u25b6  " + DJUiTheme.text("ui.djcraft.play"));
            return;
        }
        List<otto.djgun.djcraft.data.DiscPlaybackReference> playlist = discEntries.stream()
                .map(DiscPlayerEntry::reference).toList();
        DJPlaybackController.getInstance().requestPlay(playlist, currentIndex);
        ClientScreenBridge.closeScreen();
    }

    private LinearLayout createModeControl(Context context) {
        LinearLayout control = new LinearLayout(context);
        control.setOrientation(LinearLayout.HORIZONTAL);
        control.setGravity(Gravity.CENTER);
        addModeButton(context, control, DJPlaybackMode.SEQUENTIAL, "ui.djcraft.mode.sequential");
        addModeButton(context, control, DJPlaybackMode.REPEAT_ONE, "ui.djcraft.mode.repeat_one");
        addModeButton(context, control, DJPlaybackMode.SHUFFLE, "ui.djcraft.mode.shuffle");
        refreshModeButtons();
        return control;
    }

    private void addModeButton(Context context, LinearLayout control, DJPlaybackMode mode, String key) {
        Button button = new Button(context);
        button.setText(DJUiTheme.text(key));
        button.setTextSize(13);
        button.setTextColor(DJUiTheme.TEXT_SECONDARY);
        button.setContentDescription(DJUiTheme.text(key));
        button.setOnClickListener(view -> {
            if (otto.djgun.djcraft.client.CyberGrindClientState.getInstance().isActive()) {
                return;
            }
            var groupClient = otto.djgun.djcraft.client.DJNetworkGroupClient.getInstance();
            if (groupClient.isInGroup() && !groupClient.isOwner()) {
                return;
            }
            DJPlaybackController.getInstance().setMode(mode);
            refreshModeButtons();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        int gap = DJUiTheme.dp(context, 3);
        params.setMargins(gap, 0, gap, 0);
        control.addView(button, params);
        modeButtons.put(mode, button);
    }

    private void refreshModeButtons() {
        DJPlaybackMode selected = DJPlaybackController.getInstance().getMode();
        var groupState = otto.djgun.djcraft.client.DJNetworkGroupClient.getInstance().state();
        if (groupState.present() && groupState.mode() >= 0
                && groupState.mode() < DJPlaybackMode.values().length) {
            selected = DJPlaybackMode.values()[groupState.mode()];
        }
        for (Map.Entry<DJPlaybackMode, Button> entry : modeButtons.entrySet()) {
            boolean active = entry.getKey() == selected;
            Button button = entry.getValue();
            button.setEnabled(!otto.djgun.djcraft.client.CyberGrindClientState.getInstance().isActive());
            button.setTextColor(active ? DJUiTheme.TEXT_PRIMARY : DJUiTheme.TEXT_SECONDARY);
            button.setBackground(DJUiTheme.panel(
                    active ? DJUiTheme.ACCENT_DARK : DJUiTheme.SURFACE,
                    active ? DJUiTheme.ACCENT : DJUiTheme.BORDER,
                    DJUiTheme.dp(requireContext(), 6), DJUiTheme.dp(requireContext(), 1)));
        }
    }

    private View createDiscView(Context context, TrackPack pack, DiscPlayerEntry entry) {
        FrameLayout disc = new FrameLayout(context);
        InputStream stream = null;
        int maxCombo = entry == null ? 0 : entry.statistics().maxCombo();
        if (entry != null && entry.reference().discId() != null
                && entry.reference().discId().equals(DJModeManagerClient.getInstance().getCurrentDiscId())) {
            maxCombo = Math.max(maxCombo, DJModeManagerClient.getInstance().getActiveSession()
                    .map(otto.djgun.djcraft.session.DJSessionClient::getCurrentTrackMaxCombo).orElse(0));
        }
        boolean gilded = new DiscStatistics(maxCombo, 0L).isGilded(pack.getCombatBeatCount());
        try {
            stream = TrackPackManager.getInstance().openFileStream(pack.id(), gilded ? "perfect_disc.png" : "disc.png");
            if (stream == null) {
                stream = gilded ? null : TrackPackManager.getInstance().openFileStream(pack.id(), "disc.jpg");
            }
        } catch (IOException error) {
            DJCraft.LOGGER.warn("Failed to load disc image for pack {}", pack.id(), error);
        }

        Image image = null;
        if (stream != null) {
            try (InputStream input = stream; var bitmap = BitmapFactory.decodeStream(input)) {
                image = Image.createTextureFromBitmap(bitmap);
            } catch (Exception error) {
                DJCraft.LOGGER.warn("Failed to decode disc image for pack {}", pack.id(), error);
            }
        }
        if (image == null) {
            image = gilded ? getPerfectDiscImage() : getEmptyDiscImage();
        }
        if (image != null) {
            disc.setBackground(new DiscImageDrawable(image));
        } else {
            disc.setBackground(new FallbackDiscDrawable());
        }
        disc.setOnClickListener(view -> togglePlayback());
        return disc;
    }

    @Nullable
    private Image getPerfectDiscImage() {
        if (perfectDiscImage != null) {
            return perfectDiscImage;
        }
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                DJCraft.MODID, "textures/item/perfect_disc.png");
        try (InputStream input = Minecraft.getInstance().getResourceManager().open(texture);
                var bitmap = BitmapFactory.decodeStream(input)) {
            perfectDiscImage = Image.createTextureFromBitmap(bitmap);
        } catch (Exception error) {
            DJCraft.LOGGER.warn("Failed to load the gilded disc fallback texture", error);
        }
        return perfectDiscImage;
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private static String formatDuration(long totalMs) {
        long totalSeconds = Math.max(0L, totalMs) / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        return hours > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    @Nullable
    private Image getEmptyDiscImage() {
        if (emptyDiscImage != null) {
            return emptyDiscImage;
        }
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                DJCraft.MODID, "textures/item/empty_disc.png");
        try (InputStream input = Minecraft.getInstance().getResourceManager().open(texture);
                var bitmap = BitmapFactory.decodeStream(input)) {
            emptyDiscImage = Image.createTextureFromBitmap(bitmap);
        } catch (Exception error) {
            DJCraft.LOGGER.warn("Failed to load the empty disc fallback texture", error);
        }
        return emptyDiscImage;
    }

    private final class CarouselLayout extends FrameLayout {
        private CarouselLayout(Context context) {
            super(context);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            float centerX = (right - left) / 2f;
            float centerY = (bottom - top) / 2f;
            float spacing = discSizePx * 0.68f;
            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                float delta = index - scrollOffset;
                float distance = Math.abs(delta);
                int childLeft = (int) (centerX + delta * spacing - child.getMeasuredWidth() / 2f);
                int childTop = (int) (centerY + distance * discSizePx * 0.08f - child.getMeasuredHeight() / 2f);
                child.layout(childLeft, childTop, childLeft + child.getMeasuredWidth(),
                        childTop + child.getMeasuredHeight());
                child.setScaleX(Math.max(0.5f, 1f - distance * 0.28f));
                child.setScaleY(Math.max(0.5f, 1f - distance * 0.28f));
                child.setAlpha(Math.max(0f, 1f - distance * 0.58f));
                child.setZ(100f - distance * 10f);
                child.setVisibility(distance > 2.5f ? View.INVISIBLE : View.VISIBLE);
            }
        }
    }

    private static final class DiscImageDrawable extends Drawable {
        private final Image image;
        private final Paint paint = new Paint();
        private final Matrix matrix = new Matrix();
        private boolean dirty = true;

        private DiscImageDrawable(Image image) {
            this.image = image;
            paint.setAntiAlias(true);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            dirty = true;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            if (dirty) {
                float scale = Math.max((float) bounds.width() / image.getWidth(),
                        (float) bounds.height() / image.getHeight());
                matrix.setScale(scale, scale);
                matrix.postTranslate(bounds.centerX() - image.getWidth() * scale / 2f,
                        bounds.centerY() - image.getHeight() * scale / 2f);
                paint.setShader(new ImageShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP,
                        ImageShader.FILTER_MODE_NEAREST, matrix));
                dirty = false;
            }
            canvas.drawCircle(bounds.centerX(), bounds.centerY(),
                    Math.min(bounds.width(), bounds.height()) / 2f, paint);
        }
    }

    private static final class FallbackDiscDrawable extends Drawable {
        private final Paint body = new Paint();
        private final Paint groove = new Paint();
        private final Paint label = new Paint();

        private FallbackDiscDrawable() {
            body.setColor(0xFF20262B);
            body.setAntiAlias(true);
            groove.setColor(DJUiTheme.BORDER);
            groove.setStyle(Paint.Style.STROKE);
            groove.setStrokeWidth(2f);
            groove.setAntiAlias(true);
            label.setColor(DJUiTheme.ACCENT_DARK);
            label.setAntiAlias(true);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            float centerX = bounds.centerX();
            float centerY = bounds.centerY();
            float radius = Math.min(bounds.width(), bounds.height()) / 2f;
            canvas.drawCircle(centerX, centerY, radius, body);
            canvas.drawCircle(centerX, centerY, radius * 0.78f, groove);
            canvas.drawCircle(centerX, centerY, radius * 0.58f, groove);
            canvas.drawCircle(centerX, centerY, radius * 0.31f, label);
        }
    }

    private static TextView text(Context context, CharSequence value, int size, int color, int gravity) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(gravity);
        return view;
    }

    private static Button iconButton(Context context, String symbol, String description) {
        Button button = new Button(context);
        button.setText(symbol);
        button.setTextSize(22);
        button.setTextColor(DJUiTheme.TEXT_PRIMARY);
        button.setBackground(DJUiTheme.panel(DJUiTheme.SURFACE, DJUiTheme.BORDER,
                DJUiTheme.dp(context, 6), DJUiTheme.dp(context, 1)));
        button.setContentDescription(description);
        return button;
    }

    private static Button headerButton(Context context, String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(DJUiTheme.TEXT_PRIMARY);
        button.setContentDescription(label);
        button.setBackground(DJUiTheme.panel(DJUiTheme.SURFACE, DJUiTheme.BORDER,
                DJUiTheme.dp(context, 6), DJUiTheme.dp(context, 1)));
        return button;
    }

    private static String displayName(TrackPack pack) {
        String name = pack.meta().displayName();
        return name == null || name.isBlank() ? pack.id() : name;
    }

    private static String abbreviate(String value) {
        return value.length() <= 14 ? value : value.substring(0, 13) + "\u2026";
    }
}

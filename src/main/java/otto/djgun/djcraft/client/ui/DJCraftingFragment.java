package otto.djgun.djcraft.client.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.client.ClientTrackRegistry;
import otto.djgun.djcraft.network.packet.DJCraftingSelectTrackPayload;

public final class DJCraftingFragment extends Fragment {
    private final InteractionHand hand;
    private final BlockPos tablePos;
    private List<TrackPack> trackPacks = List.of();

    public DJCraftingFragment(InteractionHand hand, BlockPos tablePos) {
        this.hand = hand;
        this.tablePos = tablePos;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        ArrayList<TrackPack> packs = TrackPackManager.getInstance().getLoadedPacks().stream()
                .filter(pack -> ClientTrackRegistry.getInstance().isVerified(pack.id()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        packs.sort(Comparator.comparing(TrackPack::id, String.CASE_INSENSITIVE_ORDER));
        trackPacks = List.copyOf(packs);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable DataSet savedInstanceState) {
        Context context = requireContext();
        int dp8 = DJUiTheme.dp(context, 8);
        int dp12 = DJUiTheme.dp(context, 12);
        int dp16 = DJUiTheme.dp(context, 16);
        int dp24 = DJUiTheme.dp(context, 24);

        FrameLayout root = new FrameLayout(context);
        root.setBackground(DJUiTheme.fill(DJUiTheme.BACKGROUND));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp24, 0, dp16);

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                DJUiTheme.contentWidth(context, 560, 16), ViewGroup.LayoutParams.MATCH_PARENT);
        contentParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(content, contentParams);

        FrameLayout header = new FrameLayout(context);
        TextView title = text(context, DJUiTheme.text("ui.djcraft.crafting.title"), 24,
                DJUiTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(title, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, DJUiTheme.dp(context, 44), Gravity.LEFT));

        Button close = iconButton(context, "\u00d7", DJUiTheme.text("ui.djcraft.close"));
        close.setOnClickListener(view -> ClientScreenBridge.closeScreen());
        header.addView(close, new FrameLayout.LayoutParams(
                DJUiTheme.dp(context, 44), DJUiTheme.dp(context, 44), Gravity.RIGHT));
        Button downloads = headerButton(context, DJUiTheme.text("ui.djcraft.download.title"));
        downloads.setOnClickListener(view -> ClientScreenBridge.openScreen(new DJDownloadFragment()));
        FrameLayout.LayoutParams downloadParams = new FrameLayout.LayoutParams(
                DJUiTheme.dp(context, 88), DJUiTheme.dp(context, 36),
                Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        downloadParams.rightMargin = DJUiTheme.dp(context, 220);
        header.addView(downloads, downloadParams);
        Button reloadTrackPacks = headerButton(context, DJUiTheme.text("ui.djcraft.trackpacks.reload"));
        reloadTrackPacks.setOnClickListener(view -> DJTrackPackUiActions.reloadTrackPacks(
                () -> DJCraftingUIHelper.openCraftingUI(hand, tablePos)));
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
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, DJUiTheme.dp(context, 44)));

        Button cyberGrind = headerButton(context, DJUiTheme.text("ui.djcraft.cyber_grind.title"));
        cyberGrind.setOnClickListener(view -> ClientScreenBridge.openScreen(
                new CyberGrindFragment(hand, tablePos)));
        LinearLayout.LayoutParams cyberParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, DJUiTheme.dp(context, 42));
        cyberParams.topMargin = dp8;
        content.addView(cyberGrind, cyberParams);

        TextView subtitle = text(context, DJUiTheme.text("ui.djcraft.crafting.subtitle"), 14,
                DJUiTheme.TEXT_SECONDARY, Gravity.LEFT);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp8;
        content.addView(subtitle, subtitleParams);

        TextView count = text(context,
                DJUiTheme.text("ui.djcraft.track_count", trackPacks.size()), 12,
                DJUiTheme.ACCENT, Gravity.LEFT);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.topMargin = dp12;
        countParams.bottomMargin = dp12;
        content.addView(count, countParams);

        if (trackPacks.isEmpty()) {
            content.addView(createEmptyState(context), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }

        ScrollView scroll = new ScrollView(context);
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, 0, 0, dp8);
        for (TrackPack pack : trackPacks) {
            View row = createTrackRow(context, pack);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, DJUiTheme.dp(context, 76));
            rowParams.bottomMargin = dp8;
            rows.addView(row, rowParams);
        }
        scroll.addView(rows, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private View createTrackRow(Context context, TrackPack pack) {
        int dp12 = DJUiTheme.dp(context, 12);
        FrameLayout row = new FrameLayout(context);
        row.setPadding(dp12, DJUiTheme.dp(context, 10), dp12, DJUiTheme.dp(context, 10));
        row.setBackground(DJUiTheme.panel(DJUiTheme.SURFACE, DJUiTheme.BORDER,
                DJUiTheme.dp(context, 6), DJUiTheme.dp(context, 1)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(context, displayName(pack), 16, DJUiTheme.TEXT_PRIMARY, Gravity.LEFT);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String author = pack.meta().author();
        if (author == null || author.isBlank()) {
            author = DJUiTheme.text("ui.djcraft.unknown_author");
        }
        TextView meta = text(context,
                DJUiTheme.text("ui.djcraft.track_meta", author, pack.getBpm(), pack.getCombatBeatCount()),
                12, DJUiTheme.TEXT_SECONDARY, Gravity.LEFT);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = DJUiTheme.dp(context, 4);
        labels.addView(meta, metaParams);

        FrameLayout.LayoutParams labelsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        labelsParams.rightMargin = DJUiTheme.dp(context, 36);
        row.addView(labels, labelsParams);

        TextView action = text(context, "\u203a", 28, DJUiTheme.WARM, Gravity.CENTER);
        row.addView(action, new FrameLayout.LayoutParams(
                DJUiTheme.dp(context, 28), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT));
        row.setOnClickListener(view -> {
            PacketDistributor.sendToServer(new DJCraftingSelectTrackPayload(pack.id(), hand, tablePos));
            ClientScreenBridge.closeScreen();
        });
        return row;
    }

    private View createEmptyState(Context context) {
        LinearLayout empty = new LinearLayout(context);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        TextView title = text(context, DJUiTheme.text("ui.djcraft.empty.title"), 18,
                DJUiTheme.TEXT_PRIMARY, Gravity.CENTER);
        TextView body = text(context, DJUiTheme.text("ui.djcraft.crafting.empty"), 13,
                DJUiTheme.TEXT_SECONDARY, Gravity.CENTER);
        empty.addView(title);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = DJUiTheme.dp(context, 8);
        empty.addView(body, bodyParams);
        return empty;
    }

    private static TextView text(Context context, CharSequence value, int size, int color, int gravity) {
        TextView view = new TextView(context);
        view.setText(value instanceof String string ? TextColorHelper.parse(string) : value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(gravity);
        return view;
    }

    private static Button iconButton(Context context, String symbol, String description) {
        Button button = new Button(context);
        button.setText(symbol);
        button.setTextSize(24);
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
}

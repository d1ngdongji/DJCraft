package otto.djgun.djcraft.client.ui;

import java.util.ArrayList;
import java.util.List;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
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
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.client.CyberGrindClientState;
import otto.djgun.djcraft.client.ClientTrackRegistry;
import otto.djgun.djcraft.init.ModDataComponents;
import otto.djgun.djcraft.init.ModItems;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.network.packet.CyberGrindStartPayload;
import otto.djgun.djcraft.playback.DJPlaybackMode;

public final class CyberGrindFragment extends Fragment {
    private final InteractionHand hand;
    private final BlockPos tablePos;
    private final List<JukeboxChoice> jukeboxes = new ArrayList<>();
    private int jukeboxIndex;
    private int trackIndex;
    private int modeIndex;
    private Button jukeboxButton;
    private Button trackButton;
    private Button modeButton;

    public CyberGrindFragment(InteractionHand hand, BlockPos tablePos) {
        this.hand = hand;
        this.tablePos = tablePos;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        var serverPackIds = ClientTrackRegistry.getInstance().getServerPackIds();
        TrackPackManager trackPacks = TrackPackManager.getInstance();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(ModItems.PORTABLE_JUKEBOX.get())) {
                continue;
            }
            NonNullList<ItemStack> items = NonNullList.withSize(54, ItemStack.EMPTY);
            stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
            List<TrackChoice> tracks = new ArrayList<>();
            for (int discSlot = 0; discSlot < items.size(); discSlot++) {
                ItemStack disc = items.get(discSlot);
                String trackId = disc.get(ModDataComponents.TRACK_PACK_ID.get());
                if (!disc.is(ModItems.EMPTY_DISC.get()) || trackId == null || !serverPackIds.contains(trackId)) {
                    continue;
                }
                var pack = trackPacks.getTrackPack(trackId).orElse(null);
                if (pack != null) {
                    String displayName = pack.meta().displayName();
                    tracks.add(new TrackChoice(discSlot,
                            displayName == null || displayName.isBlank() ? trackId : displayName));
                }
            }
            if (!tracks.isEmpty()) {
                jukeboxes.add(new JukeboxChoice(slot, List.copyOf(tracks)));
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable DataSet savedInstanceState) {
        Context context = requireContext();
        FrameLayout root = new FrameLayout(context);
        root.setBackground(DJUiTheme.fill(DJUiTheme.BACKGROUND));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(DJUiTheme.dp(context, 20), DJUiTheme.dp(context, 20),
                DJUiTheme.dp(context, 20), DJUiTheme.dp(context, 20));
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                DJUiTheme.contentWidth(context, 620, 16), ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_HORIZONTAL);
        root.addView(content, contentParams);

        FrameLayout header = new FrameLayout(context);
        TextView title = text(context, DJUiTheme.text("ui.djcraft.cyber_grind.title"), 24,
                DJUiTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(title, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, DJUiTheme.dp(context, 44), Gravity.LEFT));
        Button back = button(context, DJUiTheme.text("ui.djcraft.cyber_grind.back"));
        back.setOnClickListener(view -> ClientScreenBridge.openScreen(new DJCraftingFragment(hand, tablePos)));
        header.addView(back, new FrameLayout.LayoutParams(
                DJUiTheme.dp(context, 96), DJUiTheme.dp(context, 40), Gravity.RIGHT));
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, DJUiTheme.dp(context, 48)));

        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        jukeboxButton = button(context, "");
        jukeboxButton.setOnClickListener(view -> {
            if (!jukeboxes.isEmpty()) {
                jukeboxIndex = (jukeboxIndex + 1) % jukeboxes.size();
                trackIndex = 0;
                refreshControls();
            }
        });
        modeButton = button(context, "");
        modeButton.setOnClickListener(view -> {
            modeIndex = (modeIndex + 1) % DJPlaybackMode.values().length;
            refreshControls();
        });
        trackButton = button(context, "");
        trackButton.setOnClickListener(view -> {
            if (!jukeboxes.isEmpty()) {
                List<TrackChoice> tracks = jukeboxes.get(jukeboxIndex).tracks;
                trackIndex = (trackIndex + 1) % tracks.size();
                refreshControls();
            }
        });
        controls.addView(jukeboxButton, new LinearLayout.LayoutParams(0, DJUiTheme.dp(context, 44), 1f));
        controls.addView(modeButton, new LinearLayout.LayoutParams(0, DJUiTheme.dp(context, 44), 1f));
        controls.addView(trackButton, new LinearLayout.LayoutParams(0, DJUiTheme.dp(context, 44), 1f));
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, DJUiTheme.dp(context, 44));
        controlsParams.topMargin = DJUiTheme.dp(context, 12);
        controlsParams.bottomMargin = DJUiTheme.dp(context, 12);
        content.addView(controls, controlsParams);
        refreshControls();

        ScrollView scroll = new ScrollView(context);
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        var presets = CyberGrindClientState.getInstance().presets();
        if (presets.isEmpty()) {
            rows.addView(text(context, DJUiTheme.text("ui.djcraft.cyber_grind.no_presets"), 15,
                    DJUiTheme.TEXT_SECONDARY, Gravity.CENTER));
        }
        for (var preset : presets) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(DJUiTheme.dp(context, 14), DJUiTheme.dp(context, 10),
                    DJUiTheme.dp(context, 14), DJUiTheme.dp(context, 10));
            row.setBackground(DJUiTheme.panel(DJUiTheme.SURFACE, DJUiTheme.BORDER,
                    DJUiTheme.dp(context, 6), DJUiTheme.dp(context, 1)));
            row.addView(text(context, preset.displayName(), 18, DJUiTheme.TEXT_PRIMARY, Gravity.LEFT));
            row.addView(text(context, preset.description(), 13, DJUiTheme.TEXT_SECONDARY, Gravity.LEFT));
            Button start = button(context, DJUiTheme.text("ui.djcraft.cyber_grind.start"));
            start.setEnabled(!jukeboxes.isEmpty());
            start.setOnClickListener(view -> start(preset.id()));
            LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, DJUiTheme.dp(context, 42));
            startParams.topMargin = DJUiTheme.dp(context, 8);
            row.addView(start, startParams);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = DJUiTheme.dp(context, 10);
            rows.addView(row, rowParams);
        }
        scroll.addView(rows);
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void start(String profileId) {
        if (jukeboxes.isEmpty()) {
            return;
        }
        JukeboxChoice choice = jukeboxes.get(jukeboxIndex);
        TrackChoice track = choice.tracks.get(trackIndex);
        PacketDistributor.sendToServer(new CyberGrindStartPayload(profileId, choice.slot,
                modeIndex, track.discSlot, tablePos));
        ClientScreenBridge.closeScreen();
    }

    private void refreshControls() {
        if (jukeboxButton == null) {
            return;
        }
        if (jukeboxes.isEmpty()) {
            jukeboxButton.setText(DJUiTheme.text("ui.djcraft.cyber_grind.no_jukebox"));
            trackButton.setText("-");
        } else {
            JukeboxChoice choice = jukeboxes.get(jukeboxIndex);
            jukeboxButton.setText(DJUiTheme.text("ui.djcraft.cyber_grind.jukebox", choice.slot));
            trackButton.setText(choice.tracks.get(trackIndex).displayName);
        }
        modeButton.setText(DJUiTheme.text(switch (DJPlaybackMode.values()[modeIndex]) {
            case SEQUENTIAL -> "ui.djcraft.mode.sequential";
            case REPEAT_ONE -> "ui.djcraft.mode.repeat_one";
            case SHUFFLE -> "ui.djcraft.mode.shuffle";
        }));
    }

    private static TextView text(Context context, CharSequence value, int size, int color, int gravity) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(gravity);
        return view;
    }

    private static Button button(Context context, String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(DJUiTheme.TEXT_PRIMARY);
        button.setBackground(DJUiTheme.panel(DJUiTheme.ACCENT_DARK, DJUiTheme.ACCENT,
                DJUiTheme.dp(context, 6), DJUiTheme.dp(context, 1)));
        return button;
    }

    private record JukeboxChoice(int slot, List<TrackChoice> tracks) {
    }

    private record TrackChoice(int discSlot, String displayName) {
    }
}

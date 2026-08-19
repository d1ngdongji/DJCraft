package otto.djgun.djcraft.client.ui;

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
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.client.DJNetworkGroupClient;
import otto.djgun.djcraft.network.packet.DJGroupControlPayload;
import otto.djgun.djcraft.network.packet.DJGroupCreatePayload;
import otto.djgun.djcraft.network.packet.DJGroupInvitePayload;

public final class DJNetworkFragment extends Fragment {
    private final int jukeboxSlot;

    public DJNetworkFragment(int jukeboxSlot) {
        this.jukeboxSlot = jukeboxSlot;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable DataSet savedInstanceState) {
        Context context = requireContext();
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(DJUiTheme.dp(context, 20), DJUiTheme.dp(context, 20),
                DJUiTheme.dp(context, 20), DJUiTheme.dp(context, 20));
        root.setBackground(DJUiTheme.fill(DJUiTheme.BACKGROUND));
        scroll.addView(root);

        TextView title = text(context, DJUiTheme.text("ui.djcraft.group.title"), 22);
        root.addView(title);
        DJNetworkGroupClient client = DJNetworkGroupClient.getInstance();
        var state = client.state();

        if (!state.present()) {
            if (!client.invitations().isEmpty()) {
                var invitation = client.invitations().get(0);
                root.addView(text(context,
                        DJUiTheme.text("ui.djcraft.group.invitation", invitation.ownerName()), 15));
                root.addView(button(context, DJUiTheme.text("ui.djcraft.group.accept"),
                        ignored -> {
                            client.respondToFirstInvitation(true);
                            ClientScreenBridge.closeScreen();
                        }));
                root.addView(button(context, DJUiTheme.text("ui.djcraft.group.decline"),
                        ignored -> {
                            client.respondToFirstInvitation(false);
                            ClientScreenBridge.closeScreen();
                        }));
            } else {
                root.addView(button(context, DJUiTheme.text("ui.djcraft.group.create"),
                        ignored -> {
                            PacketDistributor.sendToServer(new DJGroupCreatePayload(jukeboxSlot));
                            ClientScreenBridge.closeScreen();
                        }));
            }
        } else {
            root.addView(text(context, DJUiTheme.text("ui.djcraft.group.owner", state.ownerName()), 15));
            root.addView(text(context, DJUiTheme.text("ui.djcraft.group.members",
                    String.join(", ", state.members())), 14));
            if (!state.pending().isEmpty()) {
                root.addView(text(context, DJUiTheme.text("ui.djcraft.group.pending",
                        String.join(", ", state.pending())), 14));
            }
            root.addView(text(context, DJUiTheme.text("ui.djcraft.group.current",
                    state.currentTrack().isBlank() ? "-" : state.currentTrack()), 14));

            if (client.isOwner()) {
                addInviteButtons(context, root);
                root.addView(button(context, DJUiTheme.text("ui.djcraft.group.stop"),
                        ignored -> send(DJGroupControlPayload.Action.STOP)));
                root.addView(button(context, DJUiTheme.text("ui.djcraft.group.disband"),
                        ignored -> {
                            send(DJGroupControlPayload.Action.DISBAND);
                            ClientScreenBridge.closeScreen();
                        }));
            } else {
                root.addView(button(context, DJUiTheme.text("ui.djcraft.group.leave"),
                        ignored -> {
                            send(DJGroupControlPayload.Action.LEAVE);
                            ClientScreenBridge.closeScreen();
                        }));
            }
            root.addView(button(context, DJUiTheme.text("ui.djcraft.group.retry"),
                    ignored -> send(DJGroupControlPayload.Action.RETRY)));
        }

        root.addView(button(context, DJUiTheme.text("ui.djcraft.close"),
                ignored -> ClientScreenBridge.closeScreen()));
        return scroll;
    }

    private static void addInviteButtons(Context context, LinearLayout root) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null || minecraft.player == null) {
            return;
        }
        root.addView(text(context, DJUiTheme.text("ui.djcraft.group.invite_players"), 16));
        for (var info : minecraft.getConnection().getOnlinePlayers()) {
            if (info.getProfile().getId().equals(minecraft.player.getUUID())) {
                continue;
            }
            root.addView(button(context, info.getProfile().getName(),
                    ignored -> PacketDistributor.sendToServer(
                            new DJGroupInvitePayload(info.getProfile().getId()))));
        }
    }

    private static void send(DJGroupControlPayload.Action action) {
        PacketDistributor.sendToServer(new DJGroupControlPayload(action, 0, 0L));
    }

    private static TextView text(Context context, CharSequence value, int size) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(DJUiTheme.TEXT_PRIMARY);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(0, DJUiTheme.dp(context, 6), 0, DJUiTheme.dp(context, 6));
        return view;
    }

    private static Button button(Context context, CharSequence label, View.OnClickListener listener) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextColor(DJUiTheme.TEXT_PRIMARY);
        button.setBackground(DJUiTheme.panel(DJUiTheme.SURFACE, DJUiTheme.BORDER,
                DJUiTheme.dp(context, 6), DJUiTheme.dp(context, 1)));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, DJUiTheme.dp(context, 42));
        params.topMargin = DJUiTheme.dp(context, 6);
        button.setLayoutParams(params);
        return button;
    }
}

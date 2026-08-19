package otto.djgun.djcraft.client.ui;

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
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import otto.djgun.djcraft.client.CyberGrindClientState;
import otto.djgun.djcraft.network.packet.CyberGrindPreparePayload;

public final class CyberGrindReadyFragment extends Fragment {
    private final CyberGrindPreparePayload payload;

    public CyberGrindReadyFragment(CyberGrindPreparePayload payload) {
        this.payload = payload;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable DataSet savedInstanceState) {
        Context context = requireContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(DJUiTheme.dp(context, 32), DJUiTheme.dp(context, 32),
                DJUiTheme.dp(context, 32), DJUiTheme.dp(context, 32));
        root.setBackground(DJUiTheme.fill(DJUiTheme.BACKGROUND));

        TextView title = new TextView(context);
        title.setText(DJUiTheme.text("ui.djcraft.cyber_grind.ready_title", payload.profileName()));
        title.setTextSize(24);
        title.setTextColor(DJUiTheme.TEXT_PRIMARY);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView members = new TextView(context);
        members.setText(DJUiTheme.text("ui.djcraft.cyber_grind.ready_members",
                String.join(", ", payload.members())));
        members.setTextSize(14);
        members.setTextColor(DJUiTheme.TEXT_SECONDARY);
        members.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams memberParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        memberParams.topMargin = DJUiTheme.dp(context, 16);
        root.addView(members, memberParams);

        Button accept = button(context, DJUiTheme.text("ui.djcraft.cyber_grind.accept"));
        accept.setOnClickListener(view -> {
            CyberGrindClientState.getInstance().respond(true);
            ClientScreenBridge.closeScreen();
        });
        LinearLayout.LayoutParams acceptParams = new LinearLayout.LayoutParams(
                DJUiTheme.dp(context, 220), DJUiTheme.dp(context, 48));
        acceptParams.topMargin = DJUiTheme.dp(context, 28);
        root.addView(accept, acceptParams);

        Button decline = button(context, DJUiTheme.text("ui.djcraft.cyber_grind.decline"));
        decline.setOnClickListener(view -> {
            CyberGrindClientState.getInstance().respond(false);
            ClientScreenBridge.closeScreen();
        });
        LinearLayout.LayoutParams declineParams = new LinearLayout.LayoutParams(
                DJUiTheme.dp(context, 220), DJUiTheme.dp(context, 48));
        declineParams.topMargin = DJUiTheme.dp(context, 10);
        root.addView(decline, declineParams);
        return root;
    }

    private static Button button(Context context, String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(DJUiTheme.TEXT_PRIMARY);
        button.setBackground(DJUiTheme.panel(DJUiTheme.ACCENT_DARK, DJUiTheme.ACCENT,
                DJUiTheme.dp(context, 6), DJUiTheme.dp(context, 1)));
        return button;
    }
}

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
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.client.CyberGrindClientState;
import otto.djgun.djcraft.network.packet.CyberGrindExitPayload;

public final class CyberGrindExitConfirmFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable DataSet savedInstanceState) {
        Context context = requireContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackground(DJUiTheme.fill(DJUiTheme.BACKGROUND));
        TextView title = new TextView(context);
        title.setText(DJUiTheme.text("ui.djcraft.cyber_grind.exit_confirm"));
        title.setTextSize(22);
        title.setTextColor(DJUiTheme.TEXT_PRIMARY);
        root.addView(title);
        Button confirm = button(context, DJUiTheme.text("ui.djcraft.cyber_grind.exit"));
        confirm.setOnClickListener(view -> {
            var state = CyberGrindClientState.getInstance().state();
            if (state.active()) {
                PacketDistributor.sendToServer(new CyberGrindExitPayload(state.runId()));
            }
            ClientScreenBridge.closeScreen();
        });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                DJUiTheme.dp(context, 220), DJUiTheme.dp(context, 48));
        confirmParams.topMargin = DJUiTheme.dp(context, 24);
        root.addView(confirm, confirmParams);
        Button cancel = button(context, DJUiTheme.text("ui.djcraft.cyber_grind.cancel"));
        cancel.setOnClickListener(view -> ClientScreenBridge.closeScreen());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                DJUiTheme.dp(context, 220), DJUiTheme.dp(context, 48));
        cancelParams.topMargin = DJUiTheme.dp(context, 10);
        root.addView(cancel, cancelParams);
        return root;
    }

    private static Button button(Context context, String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextColor(DJUiTheme.TEXT_PRIMARY);
        button.setBackground(DJUiTheme.panel(DJUiTheme.ACCENT_DARK, DJUiTheme.ACCENT,
                DJUiTheme.dp(context, 6), DJUiTheme.dp(context, 1)));
        return button;
    }
}

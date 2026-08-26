package otto.djgun.djcraft.client.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
import otto.djgun.djcraft.client.ClientTrackPackTransferService;
import otto.djgun.djcraft.client.ClientTrackRegistry;
import otto.djgun.djcraft.loader.TrackPackManager;

public final class DJDownloadFragment extends Fragment {
    private TextView progressText;
    private FrameLayout progressTrack;
    private View progressFill;
    private Button pauseButton;
    private final Runnable progressRefresh = new Runnable() {
        @Override
        public void run() {
            TextView currentProgressText = progressText;
            FrameLayout currentProgressTrack = progressTrack;
            View currentProgressFill = progressFill;
            Button currentPauseButton = pauseButton;
            if (currentProgressText == null || currentProgressTrack == null
                    || currentProgressFill == null || currentPauseButton == null) {
                return;
            }
            refreshProgress(currentProgressText, currentProgressTrack,
                    currentProgressFill, currentPauseButton);
            if (progressText == currentProgressText) {
                currentProgressText.postDelayed(this, 200L);
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable DataSet savedInstanceState) {
        Context context = requireContext();
        int dp12 = DJUiTheme.dp(context, 12);
        int dp16 = DJUiTheme.dp(context, 16);

        FrameLayout root = new FrameLayout(context);
        root.setBackground(DJUiTheme.fill(DJUiTheme.BACKGROUND));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp16, dp16, dp16, dp16);
        root.addView(content, new FrameLayout.LayoutParams(
                DJUiTheme.contentWidth(context, 620, 12),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));

        FrameLayout header = new FrameLayout(context);
        TextView title = text(context, DJUiTheme.text("ui.djcraft.download.title"),
                22, DJUiTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(title, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
        Button close = button(context, "\u00d7");
        close.setOnClickListener(view -> ClientScreenBridge.closeScreen());
        header.addView(close, new FrameLayout.LayoutParams(
                DJUiTheme.dp(context, 44), DJUiTheme.dp(context, 44), Gravity.RIGHT));
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, DJUiTheme.dp(context, 48)));

        LinearLayout progressPanel = new LinearLayout(context);
        progressPanel.setOrientation(LinearLayout.VERTICAL);
        progressPanel.setPadding(dp12, dp12, dp12, dp12);
        progressPanel.setBackground(DJUiTheme.panel(
                DJUiTheme.SURFACE, DJUiTheme.BORDER, DJUiTheme.dp(context, 6), 1));
        progressText = text(context, DJUiTheme.text("ui.djcraft.download.idle"),
                13, DJUiTheme.TEXT_SECONDARY, Gravity.LEFT);
        progressPanel.addView(progressText);

        progressTrack = new FrameLayout(context);
        progressTrack.setBackground(DJUiTheme.fill(DJUiTheme.BORDER));
        progressFill = new View(context);
        progressFill.setBackground(DJUiTheme.fill(DJUiTheme.ACCENT));
        progressTrack.addView(progressFill, new FrameLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, DJUiTheme.dp(context, 8));
        trackParams.topMargin = DJUiTheme.dp(context, 8);
        progressPanel.addView(progressTrack, trackParams);

        pauseButton = button(context, DJUiTheme.text("ui.djcraft.download.pause"));
        pauseButton.setOnClickListener(view -> {
            var snapshot = ClientTrackPackTransferService.snapshot();
            if (snapshot.paused()) {
                ClientTrackPackTransferService.resume();
            } else {
                ClientTrackPackTransferService.pause();
            }
        });
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(
                DJUiTheme.dp(context, 112), DJUiTheme.dp(context, 38));
        pauseParams.gravity = Gravity.RIGHT;
        pauseParams.topMargin = DJUiTheme.dp(context, 8);
        progressPanel.addView(pauseButton, pauseParams);
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.bottomMargin = dp12;
        content.addView(progressPanel, panelParams);

        ScrollView scroll = new ScrollView(context);
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        List<String> packIds = new ArrayList<>(
                ClientTrackRegistry.getInstance().getServerPackIds());
        packIds.sort(Comparator.naturalOrder());
        if (packIds.isEmpty()) {
            rows.addView(text(context, DJUiTheme.text("ui.djcraft.download.no_server_packs"),
                    14, DJUiTheme.TEXT_SECONDARY, Gravity.CENTER));
        } else {
            for (String packId : packIds) {
                rows.addView(createPackRow(context, packId), new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, DJUiTheme.dp(context, 66)));
            }
        }
        scroll.addView(rows, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        progressText.post(progressRefresh);
        return root;
    }

    @Override
    public void onDestroyView() {
        TextView detachedProgressText = progressText;
        progressText = null;
        progressTrack = null;
        progressFill = null;
        pauseButton = null;
        if (detachedProgressText != null) {
            detachedProgressText.removeCallbacks(progressRefresh);
        }
        super.onDestroyView();
    }

    private View createPackRow(Context context, String packId) {
        FrameLayout row = new FrameLayout(context);
        row.setPadding(DJUiTheme.dp(context, 12), DJUiTheme.dp(context, 8),
                DJUiTheme.dp(context, 12), DJUiTheme.dp(context, 8));
        row.setBackground(DJUiTheme.panel(
                DJUiTheme.SURFACE, DJUiTheme.BORDER, DJUiTheme.dp(context, 5), 1));
        boolean verified = ClientTrackRegistry.getInstance().isVerified(packId);
        boolean local = TrackPackManager.getInstance().isPackLoaded(packId);
        String statusKey = verified ? "ui.djcraft.download.verified"
                : local ? "ui.djcraft.download.mismatch" : "ui.djcraft.download.missing";

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(context, packId, 15, DJUiTheme.TEXT_PRIMARY, Gravity.LEFT));
        labels.addView(text(context, DJUiTheme.text(statusKey), 12,
                verified ? DJUiTheme.ACCENT : DJUiTheme.WARM, Gravity.LEFT));
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        labelParams.rightMargin = DJUiTheme.dp(context, 128);
        row.addView(labels, labelParams);

        Button download = button(context, DJUiTheme.text(verified
                ? "ui.djcraft.download.redownload" : "ui.djcraft.download.action"));
        download.setOnClickListener(view -> {
            download.setEnabled(false);
            ClientTrackPackTransferService.request(packId,
                    () -> ClientScreenBridge.openScreen(new DJDownloadFragment()),
                    reason -> download.post(() -> {
                        download.setEnabled(true);
                        download.setText(DJUiTheme.text("ui.djcraft.download.retry"));
                    }));
        });
        row.addView(download, new FrameLayout.LayoutParams(
                DJUiTheme.dp(context, 120), DJUiTheme.dp(context, 38),
                Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        return row;
    }

    private void refreshProgress(TextView currentProgressText, FrameLayout currentProgressTrack,
            View currentProgressFill, Button currentPauseButton) {
        var snapshot = ClientTrackPackTransferService.snapshot();
        currentPauseButton.setEnabled(snapshot.active());
        currentPauseButton.setText(DJUiTheme.text(snapshot.paused()
                ? "ui.djcraft.download.resume" : "ui.djcraft.download.pause"));
        double fraction = snapshot.fraction();
        int width = currentProgressTrack.getWidth();
        ViewGroup.LayoutParams params = currentProgressFill.getLayoutParams();
        params.width = (int) Math.round(width * fraction);
        currentProgressFill.setLayoutParams(params);
        if (!snapshot.active()) {
            currentProgressText.setText(DJUiTheme.text("ui.djcraft.download.idle"));
            return;
        }
        currentProgressText.setText(DJUiTheme.text("ui.djcraft.download.progress",
                snapshot.packId(), String.format(Locale.ROOT, "%.1f", fraction * 100.0),
                formatBytes(snapshot.receivedBytes()), formatBytes(snapshot.totalBytes()),
                formatBytes(snapshot.bytesPerSecond()) + "/s"));
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
        }
        if (bytes >= 1024L) {
            return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    private static TextView text(Context context, String value, int size, int color, int gravity) {
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
        button.setBackground(DJUiTheme.panel(
                DJUiTheme.ACCENT_DARK, DJUiTheme.ACCENT, DJUiTheme.dp(context, 5), 1));
        return button;
    }
}

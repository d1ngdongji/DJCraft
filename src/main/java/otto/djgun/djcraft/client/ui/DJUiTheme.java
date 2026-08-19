package otto.djgun.djcraft.client.ui;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.graphics.drawable.Drawable;
import net.minecraft.network.chat.Component;

final class DJUiTheme {
    static final int BACKGROUND = 0xF2111417;
    static final int SURFACE = 0xFF1C2126;
    static final int SURFACE_HOVER = 0xFF262D33;
    static final int BORDER = 0xFF39434B;
    static final int TEXT_PRIMARY = 0xFFF4F7F8;
    static final int TEXT_SECONDARY = 0xFFADB7BE;
    static final int TEXT_MUTED = 0xFF78858E;
    static final int ACCENT = 0xFF58D6C2;
    static final int ACCENT_DARK = 0xFF174E49;
    static final int WARM = 0xFFE8C56D;
    static final int DANGER = 0xFFE47D74;

    private DJUiTheme() {
    }

    static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static int contentWidth(Context context, int maximumDp, int horizontalMarginDp) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int available = Math.max(dp(context, 160), screenWidth - dp(context, horizontalMarginDp * 2));
        return Math.min(dp(context, maximumDp), available);
    }

    static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    static Drawable fill(int color) {
        return new FillDrawable(color, 0f, 0, 0f);
    }

    static Drawable panel(int color, int borderColor, float radius, float borderWidth) {
        return new FillDrawable(color, radius, borderColor, borderWidth);
    }

    private static final class FillDrawable extends Drawable {
        private final Paint fill = new Paint();
        private final Paint border = new Paint();
        private final float radius;
        private final float borderWidth;

        private FillDrawable(int color, float radius, int borderColor, float borderWidth) {
            fill.setColor(color);
            fill.setAntiAlias(true);
            border.setColor(borderColor);
            border.setAntiAlias(true);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(borderWidth);
            this.radius = radius;
            this.borderWidth = borderWidth;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            if (radius <= 0f) {
                canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, fill);
                return;
            }
            float inset = borderWidth / 2f;
            canvas.drawRoundRect(bounds.left + inset, bounds.top + inset, bounds.right - inset,
                    bounds.bottom - inset, radius, fill);
            if (borderWidth > 0f) {
                canvas.drawRoundRect(bounds.left + inset, bounds.top + inset, bounds.right - inset,
                        bounds.bottom - inset, radius, border);
            }
        }
    }
}

package otto.djgun.djcraft.client.ui;

import icyllis.modernui.text.SpannableStringBuilder;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.ForegroundColorSpan;

/**
 * 辅助工具类：将原版 MC 的颜色代码 (§a, §b, 等) 转换为 ModernUI 能够直接渲染的富文本 (Spannable)
 */
public class TextColorHelper {

    // MC 的 16 种经典颜色对应的 ARGB 颜色值
    private static final int[] MC_COLORS = {
            0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, // 0, 1, 2, 3
            0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA, // 4, 5, 6, 7
            0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, // 8, 9, a, b
            0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF // c, d, e, f
    };

    /**
     * 将带有 § 的字符串解析为 ModernUI 支持的 Spannable 文本序列
     */
    public static CharSequence parse(String text) {
        if (text == null)
            return "";
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int currentColor = -1;
        int startIndex = 0;

        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                int colorIndex = "0123456789abcdef".indexOf(code);

                if (colorIndex >= 0) {
                    // 应用上一段文本的颜色
                    if (currentColor != -1 && builder.length() > startIndex) {
                        builder.setSpan(new ForegroundColorSpan(currentColor), startIndex, builder.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    currentColor = MC_COLORS[colorIndex];
                    startIndex = builder.length();
                } else if (code == 'r') {
                    // 重置颜色
                    if (currentColor != -1 && builder.length() > startIndex) {
                        builder.setSpan(new ForegroundColorSpan(currentColor), startIndex, builder.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    currentColor = -1;
                    startIndex = builder.length();
                }
                i++; // 跳过颜色代码字符
            } else {
                builder.append(text.charAt(i));
            }
        }

        // 应用结尾最后一段的颜色
        if (currentColor != -1 && builder.length() > startIndex) {
            builder.setSpan(new ForegroundColorSpan(currentColor), startIndex, builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return builder;
    }
}

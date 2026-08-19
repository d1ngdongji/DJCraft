package otto.djgun.djcraft.client.render;

/** Shared rainbow color cycle for HUD and world-space visual effects. */
public final class DJRainbowColor {
    private DJRainbowColor() {
    }

    public static Rgb sample(long nowMs, long phaseOffsetMs) {
        long phase = Math.floorMod((nowMs - phaseOffsetMs) * 13L, 18_000L);
        float hue = phase / 18_000.0F;
        float sector = hue * 6.0F;
        int index = Math.min((int) sector, 5);
        float fraction = sector - index;
        float low = 0.10F;
        float falling = 1.0F - 0.90F * fraction;
        float rising = low + 0.90F * fraction;
        return switch (index) {
            case 0 -> new Rgb(1.0F, rising, low);
            case 1 -> new Rgb(falling, 1.0F, low);
            case 2 -> new Rgb(low, 1.0F, rising);
            case 3 -> new Rgb(low, falling, 1.0F);
            case 4 -> new Rgb(rising, low, 1.0F);
            default -> new Rgb(1.0F, low, falling);
        };
    }

    public static int argb(Rgb color, float alpha) {
        int packedAlpha = Math.round(Math.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        int red = Math.round(color.red() * 255.0F);
        int green = Math.round(color.green() * 255.0F);
        int blue = Math.round(color.blue() * 255.0F);
        return packedAlpha << 24 | red << 16 | green << 8 | blue;
    }

    public record Rgb(float red, float green, float blue) {
    }
}

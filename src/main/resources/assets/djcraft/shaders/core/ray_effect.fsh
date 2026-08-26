#version 150

uniform vec4 ColorModulator;
uniform float EffectMode;
uniform float Progress;
uniform float Time;
uniform float BeamFadeFromNear;
uniform float BeamWidthAnimated;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float alpha;
    if (EffectMode < 0.5) {
        float edge = abs(texCoord0.y * 2.0 - 1.0);
        float body = 1.0 - smoothstep(0.18, 1.0, edge);
        if (BeamFadeFromNear > 0.5) {
            float fadeStart = Progress * 1.12 - 0.12;
            float travelFade = smoothstep(fadeStart, fadeStart + 0.12, texCoord0.x);
            alpha = body * travelFade;
        } else {
            alpha = body * (BeamWidthAnimated > 0.5 ? 1.0 : 1.0 - Progress);
        }
    } else if (EffectMode < 1.5) {
        float distanceFromCenter = length(texCoord0 - vec2(0.5)) * 2.0;
        float ring = 1.0 - smoothstep(0.08, 0.22, abs(distanceFromCenter - 0.72));
        float flash = 1.0 - smoothstep(0.0, 1.0, Progress);
        alpha = ring * flash;
    } else {
        float latitudeBand = 1.0 - smoothstep(0.03, 0.12, abs(fract(texCoord0.y * 8.0) - 0.5));
        float longitudeBand = 1.0 - smoothstep(0.03, 0.12, abs(fract(texCoord0.x * 12.0 - Time * 0.08) - 0.5));
        float energy = max(latitudeBand, longitudeBand * 0.75);
        float flash = 1.0 - smoothstep(0.0, 1.0, Progress);
        alpha = (0.16 + energy * 0.84) * flash;
    }
    if (alpha <= 0.003) {
        discard;
    }
    fragColor = vertexColor * ColorModulator;
    fragColor.a *= alpha;
}

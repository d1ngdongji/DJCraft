#version 150

uniform vec4 ColorModulator;
uniform vec4 RingColor;
uniform float Progress;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float ringMask(float distanceFromCenter, float radius, float halfWidth) {
    float antialias = max(fwidth(distanceFromCenter) * 1.35, 0.0015);
    return 1.0 - smoothstep(
        halfWidth,
        halfWidth + antialias,
        abs(distanceFromCenter - radius));
}

void main() {
    vec2 point = (texCoord0 - vec2(0.5)) * 2.0;
    float distanceFromCenter = length(point);
    float progress = clamp(Progress, 0.0, 1.0);
    float expansion = 1.0 - (1.0 - progress) * (1.0 - progress);

    float primaryRadius = mix(0.13, 0.84, expansion);
    float primaryWidth = mix(0.032, 0.018, progress);
    float primaryCore = ringMask(distanceFromCenter, primaryRadius, primaryWidth);
    float primaryDelta = abs(distanceFromCenter - primaryRadius);
    float primaryGlow = exp(-pow(primaryDelta / (primaryWidth * 4.2), 2.0));
    float primaryFade = 1.0 - progress;

    float secondaryActivation = smoothstep(0.16, 0.22, progress);
    float secondaryProgress = clamp((progress - 0.18) / 0.82, 0.0, 1.0);
    float secondaryExpansion = 1.0
        - (1.0 - secondaryProgress) * (1.0 - secondaryProgress);
    float secondaryRadius = mix(0.10, 0.74, secondaryExpansion);
    float secondaryCore = ringMask(distanceFromCenter, secondaryRadius, 0.016)
        * secondaryActivation;
    float secondaryDelta = abs(distanceFromCenter - secondaryRadius);
    float secondaryGlow = exp(-pow(secondaryDelta / 0.075, 2.0))
        * secondaryActivation;
    float secondaryFade = (1.0 - secondaryProgress) * 0.55;

    float flashFade = 1.0 - smoothstep(0.0, 0.28, progress);
    float flash = exp(-distanceFromCenter * distanceFromCenter / 0.055) * flashFade;

    float alpha = (primaryCore * 0.96 + primaryGlow * 0.42) * primaryFade
        + (secondaryCore * 0.72 + secondaryGlow * 0.28) * secondaryFade
        + flash * 0.82;
    alpha = clamp(alpha, 0.0, 1.0)
        * RingColor.a * vertexColor.a * ColorModulator.a;
    if (alpha < 0.005) {
        discard;
    }

    float heat = clamp(
        primaryCore * 0.72 + secondaryCore * 0.48 + flash * 0.9,
        0.0,
        1.0);
    vec3 color = mix(RingColor.rgb, vec3(1.0), heat * 0.62);
    fragColor = vec4(color, alpha);
}

#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec4 GlowColor;
uniform float GlowStrength;
uniform float EffectMode;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float sourceAlpha(vec2 uv) {
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) {
        return 0.0;
    }
    return texture(Sampler0, uv).a;
}

float smoothSourceAlpha(vec2 uv) {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 pixel = uv * size - 0.5;
    vec2 base = floor(pixel);
    vec2 blend = fract(pixel);
    vec2 uv00 = (base + vec2(0.5, 0.5)) / size;
    vec2 uv10 = (base + vec2(1.5, 0.5)) / size;
    vec2 uv01 = (base + vec2(0.5, 1.5)) / size;
    vec2 uv11 = (base + vec2(1.5, 1.5)) / size;
    float top = mix(sourceAlpha(uv00), sourceAlpha(uv10), blend.x);
    float bottom = mix(sourceAlpha(uv01), sourceAlpha(uv11), blend.x);
    return mix(top, bottom, blend.y);
}

void main() {
    if (EffectMode > 0.5) {
        float maskAlpha = sourceAlpha(texCoord0) * vertexColor.a * ColorModulator.a;
        if (maskAlpha < 0.01) {
            discard;
        }
        fragColor = vec4(GlowColor.rgb, maskAlpha * GlowColor.a);
        return;
    }

    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    float glow = 0.0;
    float weightSum = 0.0;
    for (int x = -3; x <= 3; x++) {
        for (int y = -3; y <= 3; y++) {
            vec2 offset = vec2(float(x), float(y)) * 0.72;
            float weight = exp(-dot(offset, offset) / 4.5);
            glow += smoothSourceAlpha(texCoord0 + offset * texel) * weight;
            weightSum += weight;
        }
    }
    float alpha = clamp((glow / weightSum) * 3.8 * GlowStrength, 0.0, 1.0);
    if (alpha < 0.01) {
        discard;
    }
    fragColor = vec4(GlowColor.rgb, alpha * GlowColor.a * vertexColor.a * ColorModulator.a);
}

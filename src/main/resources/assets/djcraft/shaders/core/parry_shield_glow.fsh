#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 base = texture(Sampler0, texCoord0);
    if (base.a < 0.1) {
        discard;
    }
    float intensity = 0.55 + max(max(base.r, base.g), base.b) * 0.35;
    vec4 glow = vec4(1.0, 0.035, 0.02, base.a * 0.72 * ColorModulator.a);
    glow.rgb *= intensity;
    fragColor = linear_fog(glow, vertexDistance, FogStart, FogEnd, FogColor);
}

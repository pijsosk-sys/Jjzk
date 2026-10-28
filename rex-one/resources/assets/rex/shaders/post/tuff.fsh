#version 330

// REX "Tuff" look: one cheap full-screen pass over the world image (the HUD is drawn after it).
// Sharpen, vibrance, a soft contrast curve, warm highlights over cool shadows, and a vignette.

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform TuffConfig {
    float Sharpen;
    float Vibrance;
    float Contrast;
    float Tone;
    float Vignette;
    float Exposure;
};

out vec4 fragColor;

void main() {
    vec2 px = 1.0 / InSize;
    vec3 color = texture(InSampler, texCoord).rgb;

    // Unsharp mask from four neighbours: crisper block edges without extra passes.
    vec3 around = texture(InSampler, texCoord + vec2(px.x, 0.0)).rgb
        + texture(InSampler, texCoord - vec2(px.x, 0.0)).rgb
        + texture(InSampler, texCoord + vec2(0.0, px.y)).rgb
        + texture(InSampler, texCoord - vec2(0.0, px.y)).rgb;
    color += (color - around * 0.25) * Sharpen;
    color = max(color * Exposure, 0.0);

    // Vibrance: lift dull colours more than already saturated ones, so skin and sky stay sane.
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float chroma = max(color.r, max(color.g, color.b)) - min(color.r, min(color.g, color.b));
    color = mix(vec3(luma), color, 1.0 + Vibrance * (1.0 - clamp(chroma, 0.0, 1.0)));

    // Soft S-curve around mid grey.
    color = clamp(color, 0.0, 1.0);
    color = mix(color, color * color * (3.0 - 2.0 * color), Contrast);

    // Split toning: warm light, cool shade.
    vec3 tone = mix(vec3(0.95, 0.98, 1.06), vec3(1.05, 1.01, 0.94), smoothstep(0.15, 0.85, luma));
    color = mix(color, color * tone, Tone);

    // Vignette, scaled to the aspect ratio so it stays round.
    vec2 centre = (texCoord - 0.5) * vec2(OutSize.x / OutSize.y, 1.0);
    color *= 1.0 - Vignette * smoothstep(0.35, 1.0, length(centre));

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}

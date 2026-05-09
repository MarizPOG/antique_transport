#version 150

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;

uniform float Brightness;
uniform vec3 TintColor;
uniform float TintStrength;

in vec2 texCoord;
in vec2 oneTexel;
out vec4 fragColor;

vec4 computeOutline() {
    float c = texture(DiffuseDepthSampler, texCoord).r;
    float l = texture(DiffuseDepthSampler, texCoord - vec2(oneTexel.x, 0.0)).r;
    float r = texture(DiffuseDepthSampler, texCoord + vec2(oneTexel.x, 0.0)).r;
    float u = texture(DiffuseDepthSampler, texCoord - vec2(0.0, oneTexel.y)).r;
    float d = texture(DiffuseDepthSampler, texCoord + vec2(0.0, oneTexel.y)).r;

    float dl = abs(c - l), dr = abs(c - r);
    float du = abs(c - u), dd = abs(c - d);
    float total = clamp((dl + dr + du + dd) * 20.0, 0.0, 1.0);

    // Ciemny kontur wyprowadzony z TintColor
    float factor = (dl > dr || du > dd) ? 0.10 : 0.22;
    return vec4(TintColor * factor, total);
}

void main() {
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    vec3  color = texture(DiffuseSampler0, texCoord).rgb;

    float lum = dot(color, vec3(0.299, 0.587, 0.114));
    lum = clamp(lum * 1.5 - 0.1, 0.0, 1.0);

    // Najpierw desaturuj do skali szarości
    vec3 gray   = vec3(lum);
    // Nałóż kremowy gradient
    vec3 tinted = mix(TintColor * 0.12, TintColor, lum);
    // Zmieszaj desaturowany z kremowym — TintStrength kontroluje intensywność
    vec3 result = mix(color, tinted, TintStrength);

    vec4 outline = computeOutline();
    result = mix(result, outline.rgb, outline.a);
    result = clamp(result * Brightness, 0.0, 1.0);

    fragColor = vec4(result, step(depth, 0.9999));
}
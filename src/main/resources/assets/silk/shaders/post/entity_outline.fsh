#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 ScreenSize;
uniform vec4 OutlineColor;
uniform float OutlineWidth;
uniform float Intensity;
uniform float ShowFill;
uniform float FillAlpha;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texelSize = 1.0 / ScreenSize;
    
    vec4 center = texture(DiffuseSampler, texCoord);
    
    float outline = 0.0;
    int samples = int(OutlineWidth);
    
    for (int x = -samples; x <= samples; x++) {
        for (int y = -samples; y <= samples; y++) {
            if (x == 0 && y == 0) continue;
            
            float dist = length(vec2(float(x), float(y)));
            if (dist > float(samples) + 0.5) continue;
            
            vec2 offset = vec2(float(x), float(y)) * texelSize;
            vec4 neighbor = texture(DiffuseSampler, texCoord + offset);
            
            if (center.a < 0.1 && neighbor.a > 0.1) {
                float weight = 1.0 - (dist / (float(samples) + 1.0));
                weight = smoothstep(0.0, 1.0, weight);
                outline = max(outline, weight);
            }
        }
    }
    
    if (outline > 0.0) {
        float glow = pow(outline, 0.7) * Intensity;
        fragColor = vec4(OutlineColor.rgb, glow * OutlineColor.a);
    } else if (center.a > 0.1 && ShowFill > 0.5) {
        fragColor = vec4(OutlineColor.rgb, FillAlpha * Intensity);
    } else {
        fragColor = vec4(0.0);
    }
}

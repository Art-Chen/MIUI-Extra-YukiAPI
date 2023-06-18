package moe.chenxy.miuiextra.data

class AnimFragmentShaderChen {
    companion object {
        val glsl = """precision mediump float;

            // The actual wallpaper texture.
            uniform sampler2D uTexture;

            // Reveal is the animation value that goes from 1 (the image is hidden) to 0 (the image is visible).
            uniform float uReveal;

            // Darken is the flag whether wallpaper need to darken (1 is true).
            uniform int uDarken;

            // DarkMode is the flag whether DarkMode wallpaper enable (1 is true).
            uniform int uDarkMode;

            // WallpaperAlpha is the wallpaper color alpha
            //uniform float uWallpaperAlpha;

            // WindowAlpha is the wallpaper window alpha (1 is show)
            //uniform float uWindowAlpha;

            varying vec2 vTextureCoordinates;

            const vec3 LumCoeff = vec3(0.2125, 0.7154, 0.0721);
            const vec3 AvgLumin  = vec3(0.5, 0.5, 0.5);

            const vec4 K0 = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
            const vec4 K1 = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
            
            uniform int uBlurRadius;// 模糊步长
            uniform vec2 uBlurOffset;// 总权重
            uniform float uSumWeight;
            uniform float uAlpha;

            // PI
            const float PI = 3.1415926;

            // 边界值处理
            vec2 clampCoordinate(vec2 coordinate) {
                return vec2(clamp(coordinate.x, 0.0, 1.0), clamp(coordinate.y, 0.0, 1.0));
            }

            // 计算权重
            float getWeight(int i) {
                //float sigma = float(uBlurRadius) / 3.0;
                //return (1.0 / sqrt(2.0 * PI * sigma * sigma)) * exp(-float(i * i) / (2.0 * sigma * sigma)) / uSumWeight;
                return 1.0;
            }

            /*
             * Calculates the relative luminance of the pixel.
             */
            float luminosity(vec3 color) {
                float lum = 0.2126 * color.r + 0.7152 * color.g + 0.0722 * color.b;
                return lum;
            }

            vec3 ContrastSaturationBrightness(vec3 color, float brt, float sat, float con){
                vec3 brtColor  = color * brt;
                vec3 intensity = vec3(dot(brtColor, LumCoeff));
                vec3 satColor  = mix(intensity, brtColor, sat);
                vec3 conColor  = mix(AvgLumin, satColor, con);

                return conColor;
            }

            vec4 transform(vec3 diffuse) {
                float lum = luminosity(diffuse);
                float per = 1. - uReveal;
                per = per * smoothstep(0., uReveal, lum);
                diffuse = ContrastSaturationBrightness(diffuse, per, per, 1.);
                diffuse = mix(vec3(0.), diffuse, 1.);

                return vec4(diffuse, 1.);
            }

            vec3 rgb2hsv(vec3 rgb) {
                vec4 p = rgb.g < rgb.b ? vec4(rgb.b, rgb.g, K0.w, K0.z) : vec4(rgb.g, rgb.b, K0.x, K0.y);
                vec4 q = rgb.r < p.x ? vec4(p.x, p.y, p.w, rgb.r) : vec4(rgb.r, p.y, p.z, p.x);

                float d = q.x - min(q.w, q.y);
                float e = 1.0e-10;
                return vec3(abs((q.z + (q.w - q.y) / (6.0 * d + e))), d / (q.x + e), q.x);
            }

            vec3 hsv2rgb(vec3 hsv) {
                vec3 p = abs(fract(hsv.xxx + K1.xyz) * 6.0 - K1.www);
                return hsv.z * mix(K1.xxx, clamp(p - K1.xxx, 0.0, 1.0), hsv.y);
            }

            vec3 transformColor(vec3 oColor) {
                vec3 color = rgb2hsv(oColor);

                float z = color.z;// 记录原始明度
                color.z = mix(color.z, color.z * 0.9 + 0.1, z);// 根据明度不同程度的调节明度
                color.y = color.y * 0.98;// 调节饱和度

                color = hsv2rgb(color);
                color = mix(color, vec3(0.), .1 * z);// 根据明度不同程度的压黑

                return color;
            }

            float blendColorBurn(float base, float blend) {
                return (blend==0.0)?blend:max((1.0-((1.0-base)/blend)), 0.0);
            }

            vec3 blendColorBurn(vec3 base, vec3 blend) {
                return vec3(blendColorBurn(base.r, blend.r), blendColorBurn(base.g, blend.g), blendColorBurn(base.b, blend.b));
            }

            vec3 blendColorBurn(vec3 base, vec3 blend, float opacity) {
                return (blendColorBurn(base, blend) * opacity + base * (1.0 - opacity));
            }

            // darkMode_per : darkMode=1. lightMode=0.  (0～1可实现动画过渡)
            vec3 darkModeTransformColor(vec3 color, float darkMode_per){

                float darkPer = darkMode_per * 0.15;
                color = blendColorBurn(color, vec3(0.5), darkPer);

                color = rgb2hsv(color);
                color.y *= (1. - darkMode_per*0.05);
                color.z *= (1.-darkPer*1.7);
                color = hsv2rgb(color);

                return color;
            }
            
            void setMiuiOrig(vec4 fragColor) {
                vec4 rgba = transform(fragColor.rgb);
                vec3 darkenColor = transformColor(rgba.rgb);

                if (uDarkMode == 1) {
                    if (uDarken == 1) {
                        gl_FragColor = vec4(darkModeTransformColor(darkenColor, 1.), rgba.a);//待实现动画过度，这里先写死
                    } else {
                        gl_FragColor = vec4(darkModeTransformColor(rgba.rgb, 1.), rgba.a);
                    }
                } else {
                    if (uDarken == 1) {
                        gl_FragColor = vec4(darkenColor, rgba.a);
                    } else {
                        gl_FragColor = rgba;
                    }
                }
            }

            void main() {
                vec4 fragColor = texture2D(uTexture, vTextureCoordinates);
                // Chen Blur Support
                vec4 sourceColor = fragColor;
//                if (uBlurRadius <= 1) {
                    setMiuiOrig(vec4(fragColor.rgb, uAlpha));
                    return;
//                }
                float weight = 1.0;
                vec3 finalColor = sourceColor.rgb * weight;
                for (int i = 1; i < uBlurRadius; i++) {
//                    weight = getWeight(i);
                    
                    finalColor += texture2D(uTexture, clampCoordinate(vTextureCoordinates - uBlurOffset * float(i))).rgb * weight;
                    finalColor += texture2D(uTexture, clampCoordinate(vTextureCoordinates + uBlurOffset * float(i))).rgb * weight;
                }
                finalColor /= float(2 * uBlurRadius + 1);
                setMiuiOrig(vec4(finalColor, uAlpha));
            }
                


        """
    }
}
package cc.silk.utils.render.nanovg;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.opengl.GL11;

import java.awt.*;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;

public final class NVGRenderer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final float COLOR_DIVISOR = 255f;
    
    private static final long VG = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
    
    public static final NVGPaint NVG_PAINT = NVGPaint.create();
    public static final NVGColor NVG_COLOR_1 = NVGColor.create();
    public static final NVGColor NVG_COLOR_2 = NVGColor.create();
    private static final NVGColor NVG_TRANSPARENT = NVGColor.create();
    
    private static boolean frameStarted = false;
    private static boolean initialized = false;
    public static float globalAlpha = 1.0f;
    
    static {
        nvgRGBAf(0, 0, 0, 0, NVG_TRANSPARENT);
    }
    
    private NVGRenderer() {}
    
    private static void ensureInitialized() {
        if (!initialized) {
            try {
                GLStateManager.setup();
                NanoVGFontManager.loadFonts();
                initialized = true;
                
                if (NanoVGFontManager.getRegularFont() <= 0) {
                    System.err.println("[NVGRenderer] Warning: Regular font failed to load!");
                }
            } catch (Exception e) {
                System.err.println("[NVGRenderer] Error during initialization: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    public static boolean beginFrame() {
        if (frameStarted) {
            return false;
        }
        
        ensureInitialized();
        
        final Window window = mc.getWindow();
        final float scaleFactor = (float) window.getScaleFactor();
        
        GLStateManager.push();
        
        nvgBeginFrame(VG, 
            window.getFramebufferWidth() / scaleFactor, 
            window.getFramebufferHeight() / scaleFactor, 
            scaleFactor);
        
        frameStarted = true;
        return true;
    }
    
    public static void endFrame() {
        if (!frameStarted) {
            return;
        }
        
        nvgEndFrame(VG);
        GLStateManager.pop();
        
        GL11.glViewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
        
        frameStarted = false;
    }
    
    public static void globalAlpha(float alpha) {
        globalAlpha = alpha;
        nvgGlobalAlpha(VG, alpha);
    }
    
    public static void drawRect(float x, float y, float width, float height, Color color) {
        applyColor(color, NVG_COLOR_1);
        
        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRect(VG, x, y, width, height);
        nvgFill(VG);
        nvgClosePath(VG);
    }
    
    public static void drawRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        applyColor(color, NVG_COLOR_1);
        
        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgFill(VG);
        nvgClosePath(VG);
    }
    
    public static void drawRoundedRectVarying(float x, float y, float width, float height,
                                               float radiusTopLeft, float radiusTopRight,
                                               float radiusBottomRight, float radiusBottomLeft, Color color) {
        applyColor(color, NVG_COLOR_1);
        
        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRoundedRectVarying(VG, x, y, width, height, 
            radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
        nvgFill(VG);
        nvgClosePath(VG);
    }
    
    public static void drawRoundedRectOutline(float x, float y, float width, float height, 
                                               float radius, float strokeWidth, Color color) {
        applyColor(color, NVG_COLOR_1);
        
        nvgBeginPath(VG);
        nvgStrokeColor(VG, NVG_COLOR_1);
        nvgStrokeWidth(VG, strokeWidth);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgStroke(VG);
        nvgClosePath(VG);
    }
    
    public static void drawRoundedRectGradient(float x, float y, float width, float height,
                                                float radius, Color color1, Color color2) {
        applyColor(color1, NVG_COLOR_1);
        applyColor(color2, NVG_COLOR_2);
        
        nvgLinearGradient(VG, x, y, x, y + height, NVG_COLOR_1, NVG_COLOR_2, NVG_PAINT);
        
        nvgBeginPath(VG);
        nvgFillPaint(VG, NVG_PAINT);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgFill(VG);
        nvgClosePath(VG);
    }
    
    public static void drawCircle(float x, float y, float radius, Color color) {
        applyColor(color, NVG_COLOR_1);
        
        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgCircle(VG, x, y, radius);
        nvgFill(VG);
        nvgClosePath(VG);
    }
    
    public static void drawLine(float x1, float y1, float x2, float y2, float strokeWidth, Color color) {
        applyColor(color, NVG_COLOR_1);
        
        nvgBeginPath(VG);
        nvgStrokeColor(VG, NVG_COLOR_1);
        nvgStrokeWidth(VG, strokeWidth);
        nvgMoveTo(VG, x1, y1);
        nvgLineTo(VG, x2, y2);
        nvgStroke(VG);
        nvgClosePath(VG);
    }
    
    public static void drawRoundedRectWithShadow(float x, float y, float width, float height,
                                                   float radius, Color color, Color shadowColor,
                                                   float shadowBlur, float shadowSpread) {
        applyColor(shadowColor, NVG_COLOR_1);
        // Reuse pre-allocated transparent color instead of creating new one
        nvgBoxGradient(VG, x, y, width, height, radius, shadowBlur, NVG_COLOR_1, NVG_TRANSPARENT, NVG_PAINT);
        
        nvgBeginPath(VG);
        nvgFillPaint(VG, NVG_PAINT);
        nvgRoundedRect(VG, x - shadowSpread, y - shadowSpread, 
            width + shadowSpread * 2, height + shadowSpread * 2, radius);
        nvgFill(VG);
        nvgClosePath(VG);
        
        drawRoundedRect(x, y, width, height, radius, color);
    }
    
    public static void save() {
        nvgSave(VG);
    }
    
    public static void restore() {
        nvgRestore(VG);
    }
    
    public static void translate(float x, float y) {
        nvgTranslate(VG, x, y);
    }
    
    public static void scale(float x, float y) {
        nvgScale(VG, x, y);
    }
    
    public static void rotate(float angle) {
        nvgRotate(VG, angle);
    }
    
    public static void scissor(float x, float y, float width, float height) {
        nvgScissor(VG, x, y, width, height);
    }
    
    public static void resetScissor() {
        nvgResetScissor(VG);
    }
    
    public static void applyColor(Color color, NVGColor nvgColor) {
        nvgRGBAf(
            color.getRed() / COLOR_DIVISOR,
            color.getGreen() / COLOR_DIVISOR,
            color.getBlue() / COLOR_DIVISOR,
            color.getAlpha() / COLOR_DIVISOR,
            nvgColor
        );
    }
    
    public static long getContext() {
        return VG;
    }
    
    public static boolean isInFrame() {
        return frameStarted;
    }
    
    public static void cleanup() {
        if (VG != 0) {
            nvgDelete(VG);
        }
    }
}

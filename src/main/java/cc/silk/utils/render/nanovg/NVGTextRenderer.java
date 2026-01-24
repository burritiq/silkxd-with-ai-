package cc.silk.utils.render.nanovg;

import org.lwjgl.system.MemoryStack;

import java.awt.*;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public final class NVGTextRenderer {
    private static final float DEFAULT_SHADOW_OFFSET = 1f;
    private static final Color DEFAULT_SHADOW_COLOR = new Color(0, 0, 0, 100);
    private static final int TEXT_ALIGN_LEFT_TOP = NVG_ALIGN_LEFT | NVG_ALIGN_TOP;
    private static final float CENTER_DIVISOR = 2f;
    
    private NVGTextRenderer() {}
    
    public static void drawText(String text, float x, float y, float size, Color color) {
        drawText(text, x, y, size, color, NVGFontManager.getRegularFont());
    }
    
    public static void drawText(String text, float x, float y, float size, Color color, int fontId) {
        if (!isRenderingValid()) {
            return;
        }
        
        long context = NVGRenderer.getContext();
        
        nvgSave(context);
        try {
            setupTextRendering(context, fontId, size);
            renderText(context, text, x, y, color);
        } finally {
            nvgRestore(context);
        }
    }
    
    public static void drawTextBold(String text, float x, float y, float size, Color color) {
        drawText(text, x, y, size, color, NVGFontManager.getBoldFont());
    }
    
    public static void drawTextWithShadow(String text, float x, float y, float size, Color color) {
        drawTextWithShadow(text, x, y, size, color, NVGFontManager.getRegularFont(), 
                          DEFAULT_SHADOW_OFFSET, DEFAULT_SHADOW_OFFSET, DEFAULT_SHADOW_COLOR);
    }
    
    public static void drawTextWithShadow(String text, float x, float y, float size, Color color, int fontId) {
        drawTextWithShadow(text, x, y, size, color, fontId, 
                          DEFAULT_SHADOW_OFFSET, DEFAULT_SHADOW_OFFSET, DEFAULT_SHADOW_COLOR);
    }
    
    public static void drawTextWithShadow(String text, float x, float y, float size, Color color, 
                                          float shadowOffsetX, float shadowOffsetY, Color shadowColor) {
        drawTextWithShadow(text, x, y, size, color, NVGFontManager.getRegularFont(), 
                          shadowOffsetX, shadowOffsetY, shadowColor);
    }
    
    public static void drawTextWithShadow(String text, float x, float y, float size, Color color, 
                                          int fontId, float shadowOffsetX, float shadowOffsetY, Color shadowColor) {
        if (!isRenderingValid()) {
            return;
        }
        
        long context = NVGRenderer.getContext();
        
        // Single save/restore for both shadow and text - more efficient
        nvgSave(context);
        try {
            setupTextRendering(context, fontId, size);
            
            // Render shadow first (behind)
            renderText(context, text, x + shadowOffsetX, y + shadowOffsetY, shadowColor);
            
            // Render main text on top
            renderText(context, text, x, y, color);
        } finally {
            nvgRestore(context);
        }
    }
    
    public static void drawCenteredText(String text, float x, float y, float size, Color color) {
        drawCenteredText(text, x, y, size, color, NVGFontManager.getRegularFont());
    }
    
    public static void drawCenteredText(String text, float x, float y, float size, Color color, int fontId) {
        float width = getTextWidth(text, size, fontId);
        float centeredX = x - width / CENTER_DIVISOR;
        drawText(text, centeredX, y, size, color, fontId);
    }
    
    public static void drawCenteredTextWithShadow(String text, float x, float y, float size, Color color) {
        drawCenteredTextWithShadow(text, x, y, size, color, NVGFontManager.getRegularFont());
    }
    
    public static void drawCenteredTextWithShadow(String text, float x, float y, float size, Color color, int fontId) {
        float width = getTextWidth(text, size, fontId);
        float centeredX = x - width / CENTER_DIVISOR;
        drawTextWithShadow(text, centeredX, y, size, color, fontId);
    }
    
    public static float getTextWidth(String text, float size) {
        return getTextWidth(text, size, NVGFontManager.getRegularFont());
    }
    
    public static float getTextWidth(String text, float size, int fontId) {
        long context = NVGRenderer.getContext();
        if (context == 0) {
            return 0;
        }
        
        try (MemoryStack stack = stackPush()) {
            // Allocate native buffer directly on stack - no Java array copying
            java.nio.FloatBuffer bounds = stack.mallocFloat(4);
            nvgFontFaceId(context, fontId);
            nvgFontSize(context, size);
            // bounds buffer: [minX, minY, maxX, maxY]
            nvgTextBounds(context, 0, 0, text, bounds);
            return bounds.get(2) - bounds.get(0);
        }
    }
    
    public static float getTextHeight(float size) {
        return size;
    }
    
    private static boolean isRenderingValid() {
        return NVGRenderer.isInFrame();
    }
    
    private static void setupTextRendering(long context, int fontId, float size) {
        nvgFontFaceId(context, fontId);
        nvgFontSize(context, size);
        nvgTextAlign(context, TEXT_ALIGN_LEFT_TOP);
    }
    
    private static void renderText(long context, String text, float x, float y, Color color) {
        NVGRenderer.applyColor(color, NVGRenderer.NVG_COLOR_1);
        nvgFillColor(context, NVGRenderer.NVG_COLOR_1);
        nvgText(context, x, y, text);
    }
}

package cc.silk.utils.render.nanovg;

import org.lwjgl.system.MemoryStack;

import java.awt.*;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public final class NVGTextRenderer {
    
    private NVGTextRenderer() {}
    
    public static void drawText(String text, float x, float y, float size, Color color, int fontId) {
        if (!NVGRenderer.isInFrame()) {
            return;
        }
        
        long vg = NVGRenderer.getContext();
        
        nvgSave(vg);
        try (MemoryStack stack = stackPush()) {
            nvgFontFaceId(vg, fontId);
            nvgFontSize(vg, size);
            nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
            NVGRenderer.applyColor(color, NVGRenderer.NVG_COLOR_1);
            nvgFillColor(vg, NVGRenderer.NVG_COLOR_1);
            nvgText(vg, x, y, text);
        } finally {
            nvgRestore(vg);
        }
    }
    
    public static void drawText(String text, float x, float y, float size, Color color) {
        drawText(text, x, y, size, color, NVGFontManager.getRegularFont());
    }
    
    public static void drawTextBold(String text, float x, float y, float size, Color color) {
        drawText(text, x, y, size, color, NVGFontManager.getBoldFont());
    }
    
    public static float getTextWidth(String text, float size, int fontId) {
        if (!NVGRenderer.isInFrame()) {
            return 0;
        }
        
        long vg = NVGRenderer.getContext();
        try (MemoryStack stack = stackPush()) {
            float[] bounds = new float[4];
            nvgFontFaceId(vg, fontId);
            nvgFontSize(vg, size);
            nvgTextBounds(vg, 0, 0, text, bounds);
            return bounds[2] - bounds[0];
        }
    }
    
    public static float getTextWidth(String text, float size) {
        return getTextWidth(text, size, NVGFontManager.getRegularFont());
    }
    
    public static float getTextHeight(float size) {
        return size;
    }
    
    public static void drawCenteredText(String text, float x, float y, float size, Color color, int fontId) {
        float width = getTextWidth(text, size, fontId);
        drawText(text, x - width / 2f, y, size, color, fontId);
    }
    
    public static void drawCenteredText(String text, float x, float y, float size, Color color) {
        drawCenteredText(text, x, y, size, color, NVGFontManager.getRegularFont());
    }
}

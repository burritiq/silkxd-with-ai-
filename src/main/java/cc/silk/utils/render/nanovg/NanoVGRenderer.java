package cc.silk.utils.render.nanovg;

import lombok.experimental.UtilityClass;

import java.awt.*;

@UtilityClass
public class NanoVGRenderer {
    private static final int FALLBACK_FONT_ID = 1;
    
    public static void init() {
        GLStateManager.setup();
        NVGFontManager.loadFonts();
    }

    public static void reinit() {
        init();
    }

    public static void beginFrame() {
        NVGRenderer.beginFrame();
    }

    public static void endFrame() {
        NVGRenderer.endFrame();
    }

    public static boolean isInFrame() {
        return NVGRenderer.isInFrame();
    }

    public static void drawRect(float x, float y, float width, float height, Color color) {
        NVGRenderer.drawRect(x, y, width, height, color);
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        NVGRenderer.drawRoundedRect(x, y, width, height, radius, color);
    }

    public static void drawRoundedRectVarying(float x, float y, float width, float height,
                                              float radiusTopLeft, float radiusTopRight, 
                                              float radiusBottomRight, float radiusBottomLeft, Color color) {
        // Allows different radius per corner - prob useful for UI connecting
        NVGRenderer.drawRoundedRectVarying(x, y, width, height, 
            radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color);
    }

    public static void drawRoundedRectOutline(float x, float y, float width, float height, 
                                              float radius, float strokeWidth, Color color) {
        NVGRenderer.drawRoundedRectOutline(x, y, width, height, radius, strokeWidth, color);
    }

    public static void drawRoundedRectGradient(float x, float y, float width, float height, 
                                               float radius, Color colorTop, Color colorBottom) {
        NVGRenderer.drawRoundedRectGradient(x, y, width, height, radius, colorTop, colorBottom);
    }

    public static void drawRoundedRectWithShadow(float x, float y, float width, float height, 
                                                  float radius, Color color, Color shadowColor, 
                                                  float shadowBlur, float shadowSpread) {
        NVGRenderer.drawRoundedRectWithShadow(x, y, width, height, radius, color, 
            shadowColor, shadowBlur, shadowSpread);
    }

    public static void drawCircle(float x, float y, float radius, Color color) {
        NVGRenderer.drawCircle(x, y, radius, color);
    }

    public static void drawLine(float x1, float y1, float x2, float y2, float strokeWidth, Color color) {
        NVGRenderer.drawLine(x1, y1, x2, y2, strokeWidth, color);
    }

    public static void drawText(String text, float x, float y, float size, Color color) {
        NVGTextRenderer.drawText(text, x, y, size, color);
    }

    public static void drawText(String text, float x, float y, float size, Color color, boolean bold) {
        int fontId = NVGFontManager.getFontId(bold);
        NVGTextRenderer.drawText(text, x, y, size, color, fontId);
    }

    public static void drawTextWithFont(String text, float x, float y, float size, Color color, int fontId) {
        NVGTextRenderer.drawText(text, x, y, size, color, fontId);
    }
    
    public static void drawTextWithShadow(String text, float x, float y, float size, Color color) {
        NVGTextRenderer.drawTextWithShadow(text, x, y, size, color);
    }
    
    public static void drawTextWithShadow(String text, float x, float y, float size, Color color, int fontId) {
        NVGTextRenderer.drawTextWithShadow(text, x, y, size, color, fontId);
    }
    
    public static void drawTextWithShadow(String text, float x, float y, float size, Color color, 
                                          float shadowOffsetX, float shadowOffsetY, Color shadowColor) {
        NVGTextRenderer.drawTextWithShadow(text, x, y, size, color, 
            shadowOffsetX, shadowOffsetY, shadowColor);
    }
    
    public static void drawTextWithShadow(String text, float x, float y, float size, Color color, int fontId,
                                          float shadowOffsetX, float shadowOffsetY, Color shadowColor) {
        NVGTextRenderer.drawTextWithShadow(text, x, y, size, color, fontId, 
            shadowOffsetX, shadowOffsetY, shadowColor);
    }

    public static void drawIcon(String icon, float x, float y, float size, Color color) {
        int iconFontId = NVGFontManager.getIconFont();
        NVGTextRenderer.drawText(icon, x, y, size, color, iconFontId);
    }

    public static float getTextWidth(String text, float size) {
        return NVGTextRenderer.getTextWidth(text, size);
    }

    public static float getTextWidth(String text, float size, boolean bold) {
        int fontId = NVGFontManager.getFontId(bold);
        return NVGTextRenderer.getTextWidth(text, size, fontId);
    }

    public static float getTextWidthWithFont(String text, float size, int fontId) {
        return NVGTextRenderer.getTextWidth(text, size, fontId);
    }

    public static float getTextHeight(float size) {
        return NVGTextRenderer.getTextHeight(size);
    }

    public static int getPoppinsFontId() {
        return getFontWithFallback(NVGFontManager.getPoppinsFont());
    }

    public static int getJetBrainsFontId() {
        return getFontWithFallback(NVGFontManager.getJetbrainsFont());
    }

    public static int getRegularFontId() {
        return getFontWithFallback(NVGFontManager.getRegularFont());
    }

    public static int getMonacoFontId() {
        return getFontWithFallback(NVGFontManager.getMonacoFont());
    }

    public static int loadImage(String path) {
        return NVGImage.loadImage(path);
    }

    public static void drawImage(int imageId, float x, float y, float width, float height, Color tint) {
        NVGImage.drawImage(imageId, x, y, width, height, tint);
    }

    public static void save() {
        NVGRenderer.save();
    }

    public static void restore() {
        NVGRenderer.restore();
    }

    public static void translate(float x, float y) {
        NVGRenderer.translate(x, y);
    }

    public static void scale(float x, float y) {
        NVGRenderer.scale(x, y);
    }

    public static void scissor(float x, float y, float width, float height) {
        NVGRenderer.scissor(x, y, width, height);
    }

    public static void resetScissor() {
        NVGRenderer.resetScissor();
    }

    public static void cleanup() {
        NVGRenderer.cleanup();
    }
    
    private static int getFontWithFallback(int fontId) {
        if (fontId > 0) {
            return fontId;
        }
        
        // Try regular font, then hardcoded fallback if all else fails
        int regularFont = NVGFontManager.getRegularFont();
        return regularFont > 0 ? regularFont : FALLBACK_FONT_ID;
    }
}

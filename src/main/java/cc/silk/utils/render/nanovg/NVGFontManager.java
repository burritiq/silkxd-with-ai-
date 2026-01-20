package cc.silk.utils.render.nanovg;

public final class NVGFontManager {
    
    private NVGFontManager() {}
    
    public static void loadFonts() {
        NanoVGFontManager.loadFonts();
    }
    
    public static int getRegularFont() {
        return NanoVGFontManager.getRegularFont();
    }
    
    public static int getBoldFont() {
        return NanoVGFontManager.getBoldFont();
    }
    
    public static int getIconFont() {
        return NanoVGFontManager.getIconFont();
    }
    
    public static int getJetbrainsFont() {
        return NanoVGFontManager.getJetbrainsFont();
    }
    
    public static int getPoppinsFont() {
        return NanoVGFontManager.getPoppinsFont();
    }
    
    public static int getMonacoFont() {
        return NanoVGFontManager.getMonacoFont();
    }
    
    public static int getFontId(boolean bold) {
        return NanoVGFontManager.getFontId(bold);
    }
}

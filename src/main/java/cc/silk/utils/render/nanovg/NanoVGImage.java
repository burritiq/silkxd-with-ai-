package cc.silk.utils.render.nanovg;

import java.awt.*;

@Deprecated
public class NanoVGImage {
    
    public static int loadImage(String path) {
        return NVGImage.loadImage(path);
    }

    public static void drawImage(int imageId, float x, float y, float width, float height, Color tint) {
        NVGImage.drawImage(imageId, x, y, width, height, tint);
    }
}


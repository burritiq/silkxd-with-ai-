package cc.silk.module.modules.render;

import cc.silk.event.impl.render.Render2DEvent;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.*;
import cc.silk.utils.render.DraggableComponent;
import cc.silk.utils.render.nanovg.NanoVGRenderer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;

import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Watermark extends Module {
    private final StringSetting text = new StringSetting("Text", "Silk");
    private final NumberSetting transparency = new NumberSetting("Transparency", 0, 255, 200, 1);
    private final ModeSetting colorMode = new ModeSetting("Color Mode", "Theme", "Theme", "Custom");
    private final ColorSetting customColor = new ColorSetting("Custom Color", new Color(255, 255, 255));

    private DraggableComponent draggable;
    private boolean needsInitialCenter = true;

    private int fpsIcon = -1;
    private int userIcon = -1;
    private int clockIcon = -1;

    public Watermark() {
        super("Watermark", "Displays client watermark", -1, Category.RENDER);
        addSettings(text, transparency, colorMode, customColor);
    }

    @Override
    public void onEnable() {
        loadIcons();
    }

    private void loadIcons() {
        if (fpsIcon == -1) {
            fpsIcon = NanoVGRenderer.loadImage("assets/silk/textures/icons/fps.png");
        }
        if (userIcon == -1) {
            userIcon = NanoVGRenderer.loadImage("assets/silk/textures/icons/user.png");
        }
        if (clockIcon == -1) {
            clockIcon = NanoVGRenderer.loadImage("assets/silk/textures/icons/clock.png");
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null)
            return;

        loadIcons();

        if (draggable == null) {
            int screenWidth = mc.getWindow().getScaledWidth();
            draggable = new DraggableComponent(screenWidth / 2f, 10, 200, 20);
            needsInitialCenter = true;
        }

        boolean isInChat = mc.currentScreen instanceof ChatScreen;
        if (mc.currentScreen != null && !isInChat)
            return;

        render(isInChat);
    }

    private void render(boolean isInChat) {
        float padding = 6;
        float textSize = 11;
        float iconSize = 10;
        float spacing = 10;
        float iconTextGap = 3;

        String title = text.getValue();
        String username = mc.player.getName().getString();
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        int fps = mc.getCurrentFps();
        String fpsText = String.valueOf(fps);

        float titleWidth = NanoVGRenderer.getTextWidth(title, textSize);
        float userWidth = iconSize + iconTextGap + NanoVGRenderer.getTextWidth(username, textSize);
        float timeWidth = iconSize + iconTextGap + NanoVGRenderer.getTextWidth(time, textSize);
        float fpsWidth = iconSize + iconTextGap + NanoVGRenderer.getTextWidth(fpsText, textSize);

        float textHeight = NanoVGRenderer.getTextHeight(textSize);
        float contentHeight = Math.max(textHeight, iconSize);

        float totalWidth = titleWidth + spacing + userWidth + spacing + timeWidth + spacing + fpsWidth;
        float bgWidth = totalWidth + padding * 2;
        float bgHeight = contentHeight + padding * 2;

        draggable.setWidth(bgWidth);
        draggable.setHeight(bgHeight);

        if (needsInitialCenter) {
            int screenWidth = mc.getWindow().getScaledWidth();
            draggable.setX(screenWidth / 2f - bgWidth / 2f);
            needsInitialCenter = false;
        }

        if (isInChat) {
            draggable.update();
            snapToCenter(bgWidth);
        }

        float x = draggable.getX();
        float y = draggable.getY();

        int alpha = (int) transparency.getValue();
        Color bgColor = new Color(20, 20, 25, alpha);
        Color borderColor = new Color(40, 40, 46, alpha);
        NanoVGRenderer.drawRoundedRect(x, y, bgWidth, bgHeight, 4f, bgColor);
        NanoVGRenderer.drawRoundedRectOutline(x, y, bgWidth, bgHeight, 4f, 1f, borderColor);

        float centerY = y + bgHeight / 2f;
        float textY = centerY - textHeight / 2f;
        float iconY = centerY - iconSize / 2f - 1;

        Color accentColor = getColor();
        Color infoColor = new Color(200, 200, 200);
        Color iconColor = new Color(255, 255, 255, 255);

        float cursorX = x + padding;

        NanoVGRenderer.drawText(title, cursorX, textY, textSize, accentColor);
        cursorX += titleWidth + spacing;

        if (userIcon != -1) {
            NanoVGRenderer.drawImage(userIcon, cursorX, iconY, iconSize, iconSize, iconColor);
        }
        cursorX += iconSize + iconTextGap;
        NanoVGRenderer.drawText(username, cursorX, textY, textSize, infoColor);
        cursorX += NanoVGRenderer.getTextWidth(username, textSize) + spacing;

        if (clockIcon != -1) {
            NanoVGRenderer.drawImage(clockIcon, cursorX, iconY, iconSize, iconSize, iconColor);
        }
        cursorX += iconSize + iconTextGap;
        NanoVGRenderer.drawText(time, cursorX, textY, textSize, infoColor);
        cursorX += NanoVGRenderer.getTextWidth(time, textSize) + spacing;

        if (fpsIcon != -1) {
            NanoVGRenderer.drawImage(fpsIcon, cursorX, iconY, iconSize, iconSize, iconColor);
        }
        cursorX += iconSize + iconTextGap;
        NanoVGRenderer.drawText(fpsText, cursorX, textY, textSize, infoColor);
    }

    private void snapToCenter(float width) {
        int screenWidth = mc.getWindow().getScaledWidth();
        float centerX = screenWidth / 2f;
        float componentCenterX = draggable.getX() + width / 2f;

        float snapDistance = 5f;
        if (Math.abs(componentCenterX - centerX) < snapDistance) {
            draggable.setX(centerX - width / 2f);
        }
    }

    private Color getColor() {
        return switch (colorMode.getMode()) {
            case "Custom" -> customColor.getValue();
            default -> cc.silk.module.modules.client.NewClickGUIModule.getAccentColor();
        };
    }
}

package cc.silk.module.modules.render;

import cc.silk.SilkClient;
import cc.silk.event.impl.render.Render2DEvent;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.*;
import cc.silk.utils.render.nanovg.NanoVGRenderer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;

import java.awt.*;
import java.util.Comparator;
import java.util.List;

public class ArrayList extends Module {
    private static final float PADDING = 4f;
    private static final float MARGIN = 5f;
    private static final float CORNER_RADIUS = 3f;
    private static final float WIDTH_TOLERANCE = 0.5f;
    private static final Color TEXT_COLOR = new Color(255, 255, 255, 255);
    private static final Color BACKGROUND_BASE = new Color(20, 20, 25);
    
    private final ModeSetting position;
    private final NumberSetting fontSize;
    private final NumberSetting bgAlpha;
    private final BooleanSetting rounded;
    private final NumberSetting barWidth;
    private final BooleanSetting textShadow;

    public ArrayList() {
        super("ArrayList", "Displays enabled modules", -1, Category.RENDER);
        
        this.position = new ModeSetting("Position", "Top Right", "Top Left", "Top Right");
        this.fontSize = new NumberSetting("Font Size", 8, 24, 14, 1);
        this.bgAlpha = new NumberSetting("BG Alpha", 0, 255, 150, 1);
        this.rounded = new BooleanSetting("Rounded", true);
        this.barWidth = new NumberSetting("Bar Width", 1, 5, 2, 0.5);
        this.textShadow = new BooleanSetting("Text Shadow", true);
        
        addSettings(position, fontSize, bgAlpha, rounded, barWidth, textShadow);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!shouldRender()) {
            return;
        }

        List<Module> sortedModules = getSortedEnabledModules();
        if (sortedModules.isEmpty()) {
            return;
        }

        NanoVGRenderer.beginFrame();
        renderModuleList(sortedModules);
        NanoVGRenderer.endFrame();
    }

    private boolean shouldRender() {
        return mc.player != null 
            && mc.world != null 
            && (mc.currentScreen == null || mc.currentScreen instanceof ChatScreen);
    }

    private List<Module> getSortedEnabledModules() {
        List<Module> enabledModules = SilkClient.INSTANCE.getModuleManager().getEnabledModules();
        List<Module> sortedModules = new java.util.ArrayList<>(enabledModules);
        
        float currentFontSize = (float) fontSize.getValue();
        sortedModules.sort(Comparator.comparingDouble(
            module -> -NanoVGRenderer.getTextWidth(module.getName(), currentFontSize)
        ));
        
        return sortedModules;
    }

    private void renderModuleList(List<Module> modules) {
        RenderContext context = new RenderContext();
        
        for (int i = 0; i < modules.size(); i++) {
            ModuleEntry entry = new ModuleEntry(modules, i, context);
            renderModuleEntry(entry, context);
        }
    }

    private void renderModuleEntry(ModuleEntry entry, RenderContext context) {
        renderBackground(entry, context);
        renderSideBar(entry, context);
        renderText(entry, context);
        
        context.currentY += entry.bgHeight;
    }

    private void renderBackground(ModuleEntry entry, RenderContext context) {
        // Round corners at width transitions and list boundaries
        if (context.isRounded && entry.needsRounding()) {
            drawRoundedBackground(entry, context);
        } else {
            NanoVGRenderer.drawRect(entry.x, entry.y, entry.bgWidth, entry.bgHeight, context.bgColor);
        }
    }

    private void drawRoundedBackground(ModuleEntry entry, RenderContext context) {
        // Only round corners where width actually changes (gets narrower)
        if (context.isRightAligned) {
            float topLeft = 0, topRight = 0, bottomRight = 0, bottomLeft = 0;
            
            // Round top if first OR this item is narrower than previous
            if (entry.isFirst || entry.isNarrowerThanPrev) {
                topLeft = CORNER_RADIUS;
            }
            // Round bottom if last OR next item is narrower than this
            if (entry.isLast || entry.isWiderThanNext) {
                bottomLeft = CORNER_RADIUS;
            }
            
            NanoVGRenderer.drawRoundedRectVarying(
                entry.x, entry.y, entry.bgWidth, entry.bgHeight, 
                topLeft, topRight, bottomRight, bottomLeft, 
                context.bgColor
            );
        } else {
            float topLeft = 0, topRight = 0, bottomRight = 0, bottomLeft = 0;
            
            // Round top if first OR this item is narrower than previous
            if (entry.isFirst || entry.isNarrowerThanPrev) {
                topRight = CORNER_RADIUS;
            }
            // Round bottom if last OR next item is narrower than this
            if (entry.isLast || entry.isWiderThanNext) {
                bottomRight = CORNER_RADIUS;
            }
            
            NanoVGRenderer.drawRoundedRectVarying(
                entry.x, entry.y, entry.bgWidth, entry.bgHeight, 
                topLeft, topRight, bottomRight, bottomLeft, 
                context.bgColor
            );
        }
    }

    private void renderSideBar(ModuleEntry entry, RenderContext context) {
        float barX = context.isRightAligned ? entry.x + entry.bgWidth - context.barWidth : entry.x;
        
        // Sidebar always sharp - no rounding
        NanoVGRenderer.drawRect(barX, entry.y, context.barWidth, entry.bgHeight, context.accentColor);
    }

    private void renderText(ModuleEntry entry, RenderContext context) {
        float textX = entry.x + PADDING + (context.isRightAligned ? 0 : context.barWidth);
        float textY = entry.y + PADDING;
        
        if (textShadow.getValue()) {
            NanoVGRenderer.drawTextWithShadow(entry.displayText, textX, textY, context.fontSize, TEXT_COLOR);
        } else {
            NanoVGRenderer.drawText(entry.displayText, textX, textY, context.fontSize, TEXT_COLOR);
        }
    }

    private class RenderContext {
        final int screenWidth;
        final float fontSize;
        final float barWidth;
        final boolean isRightAligned;
        final boolean isRounded;
        final Color accentColor;
        final Color bgColor;
        float currentY;

        RenderContext() {
            this.screenWidth = mc.getWindow().getScaledWidth();
            this.fontSize = (float) ArrayList.this.fontSize.getValue();
            this.barWidth = (float) ArrayList.this.barWidth.getValue();
            this.isRightAligned = position.getMode().contains("Right");
            this.isRounded = rounded.getValue();
            this.accentColor = cc.silk.module.modules.client.NewClickGUIModule.getAccentColor();
            this.bgColor = new Color(BACKGROUND_BASE.getRed(), BACKGROUND_BASE.getGreen(), 
                                     BACKGROUND_BASE.getBlue(), (int) bgAlpha.getValue());
            this.currentY = MARGIN;
        }
    }

    private class ModuleEntry {
        final String displayText;
        final float textWidth;
        final float textHeight;
        final float bgWidth;
        final float bgHeight;
        final float x;
        final float y;
        final boolean isFirst;
        final boolean isLast;
        final boolean isNarrowerThanPrev;
        final boolean isWiderThanNext;

        ModuleEntry(List<Module> modules, int index, RenderContext context) {
            Module module = modules.get(index);
            this.displayText = module.getName();
            this.textWidth = NanoVGRenderer.getTextWidth(displayText, context.fontSize);
            this.textHeight = NanoVGRenderer.getTextHeight(context.fontSize);
            this.bgWidth = textWidth + PADDING * 2;
            this.bgHeight = textHeight + PADDING * 2;
            this.x = context.isRightAligned ? context.screenWidth - bgWidth - MARGIN : MARGIN;
            this.y = context.currentY;
            this.isFirst = index == 0;
            this.isLast = index == modules.size() - 1;
            this.isNarrowerThanPrev = !isFirst && isNarrowerThan(modules.get(index - 1), context);
            this.isWiderThanNext = !isLast && isWiderThan(modules.get(index + 1), context);
        }

        private boolean isNarrowerThan(Module other, RenderContext context) {
            float otherTextWidth = NanoVGRenderer.getTextWidth(other.getName(), context.fontSize);
            float otherBgWidth = otherTextWidth + PADDING * 2;
            return bgWidth < otherBgWidth - WIDTH_TOLERANCE;
        }

        private boolean isWiderThan(Module other, RenderContext context) {
            float otherTextWidth = NanoVGRenderer.getTextWidth(other.getName(), context.fontSize);
            float otherBgWidth = otherTextWidth + PADDING * 2;
            return bgWidth > otherBgWidth + WIDTH_TOLERANCE;
        }

        boolean shouldRoundCorner() {
            // Round if first, last, OR width changes (narrower than prev OR wider than next)
            return isFirst || isLast || isNarrowerThanPrev || isWiderThanNext;
        }
        
        boolean needsRounding() {
            return shouldRoundCorner();
        }
    }
}

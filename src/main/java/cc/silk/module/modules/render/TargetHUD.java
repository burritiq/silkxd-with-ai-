package cc.silk.module.modules.render;

import cc.silk.event.impl.player.AttackEvent;
import cc.silk.event.impl.render.Render2DEvent;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.BooleanSetting;
import cc.silk.module.setting.NumberSetting;
import cc.silk.utils.render.DraggableComponent;
import cc.silk.utils.render.blur.BlurRenderer;
import cc.silk.utils.render.nanovg.NanoVGRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;

public final class TargetHUD extends Module {
    private static final int BASE_WIDTH = 120;
    private static final int BASE_HEIGHT = 40;
    private static final int PADDING = 6;
    private static final int HEAD_SIZE = 28;
    private static final int ITEM_SIZE = 14;
    private static final int ITEM_SPACING = 0;
    private static final float CORNER_RADIUS = 12f;
    private static final float HEALTH_BAR_HEIGHT = 6f;
    private static final float HEALTH_BAR_RADIUS = 3f;
    private static final float NAME_SIZE = 9f;
    private static final float ANIMATION_SPEED = 3.0f;
    private static final float ITEM_SCALE = 0.875f;
    private static final float HEAD_SCALE = HEAD_SIZE / 8.0f;
    private static final int PARTICLE_LIFETIME_MS = 1000;
    private static final float PARTICLE_GRAVITY = 200f;
    private static final double TARGET_RANGE = 8.0;
    private static final Color BACKGROUND_BASE = new Color(20, 20, 25);
    private static final Color BACKGROUND_DRAG = new Color(30, 30, 35);
    private static final Color BAR_BACKGROUND = new Color(15, 15, 20);
    private static final Color TEXT_COLOR = new Color(255, 255, 255, 255);

    private final NumberSetting transparency;
    private final BooleanSetting blur;
    private final NumberSetting blurRadius;
    private final BooleanSetting gradientHealth;
    private final BooleanSetting showArmor;
    private final BooleanSetting showHeldItem;
    private final BooleanSetting particles;
    private final NumberSetting particleCount;

    private final DraggableComponent draggable;
    private final Deque<Particle> particleQueue;
    
    private float animatedHealthPercent;
    private float lastHealthPercent;

    public TargetHUD() {
        super("Target HUD", "Displays information about the current combat target", -1, Category.RENDER);
        
        this.transparency = new NumberSetting("Transparency", 0, 255, 200, 5);
        this.blur = new BooleanSetting("Blur", true);
        this.blurRadius = new NumberSetting("Blur Radius", 1, 30, 12, 1);
        this.gradientHealth = new BooleanSetting("Gradient Health", true);
        this.showArmor = new BooleanSetting("Show Armor", true);
        this.showHeldItem = new BooleanSetting("Show Item", true);
        this.particles = new BooleanSetting("Particles", true);
        this.particleCount = new NumberSetting("Particle Count", 5, 30, 15, 1);
        
        this.draggable = new DraggableComponent(20, 100, BASE_WIDTH, BASE_HEIGHT);
        this.particleQueue = new ArrayDeque<>();
        this.animatedHealthPercent = 1.0f;
        this.lastHealthPercent = 1.0f;
        
        addSettings(transparency, blur, blurRadius, gradientHealth, showArmor, showHeldItem, particles, particleCount);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!shouldRender()) {
            return;
        }

        PlayerEntity target = findTarget();
        if (target == null && mc.currentScreen == null) {
            return;
        }

        draggable.update();
        
        RenderData data = new RenderData(target);
        renderHUD(event, data);
    }

    private boolean shouldRender() {
        return mc.player != null && mc.world != null;
    }

    private void renderHUD(Render2DEvent event, RenderData data) {
        float x = draggable.getX();
        float y = draggable.getY();
        
        int dynamicWidth = calculateDynamicWidth(data);
        draggable.setWidth(dynamicWidth);
        
        renderBackground(event, x, y, dynamicWidth);
        renderNameAndHealthBar(data, x, y);
        
        if (particles.getValue()) {
            renderParticles(event.getContext().getScaledWindowWidth(), 
                          event.getContext().getScaledWindowHeight());
        }
        
        if (data.target != null) {
            renderPlayerHead(event.getContext(), data.target, x, y);
            
            if (showArmor.getValue() || showHeldItem.getValue()) {
                renderEquipment(event.getContext(), data.target, x + BASE_WIDTH - 2, y);
            }
        }
    }

    private int calculateDynamicWidth(RenderData data) {
        int itemCount = 0;
        
        if (data.target != null) {
            if (showHeldItem.getValue() && !data.target.getMainHandStack().isEmpty()) {
                itemCount++;
            }
            if (showArmor.getValue()) {
                for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, 
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    if (!data.target.getEquippedStack(slot).isEmpty()) {
                        itemCount++;
                    }
                }
            }
        }
        
        return BASE_WIDTH + (itemCount > 0 ? (itemCount * ITEM_SIZE) + 4 : 0);
    }

    private void renderBackground(Render2DEvent event, float x, float y, int width) {
        if (blur.getValue()) {
            BlurRenderer.drawBlur(
                event.getContext().getMatrices(),
                x, y, width, BASE_HEIGHT,
                CORNER_RADIUS,
                Color.WHITE,
                (float) blurRadius.getValue()
            );
        }

        int alpha = transparency.getValueInt();
        Color bgColor = draggable.isDragging() 
            ? new Color(BACKGROUND_DRAG.getRed(), BACKGROUND_DRAG.getGreen(), 
                       BACKGROUND_DRAG.getBlue(), Math.min(255, alpha + 30))
            : new Color(BACKGROUND_BASE.getRed(), BACKGROUND_BASE.getGreen(), 
                       BACKGROUND_BASE.getBlue(), blur.getValue() ? alpha / 2 : alpha);
        
        NanoVGRenderer.drawRoundedRect(x, y, width, BASE_HEIGHT, CORNER_RADIUS, bgColor);

        if (draggable.isDragging()) {
            Color accentColor = getAccentColor();
            NanoVGRenderer.drawRoundedRectOutline(x, y, width, BASE_HEIGHT, CORNER_RADIUS, 2f, accentColor);
        }
    }

    private void renderNameAndHealthBar(RenderData data, float x, float y) {
        float headX = x + PADDING;
        float barX = headX + HEAD_SIZE + PADDING;
        float barY = y + PADDING + 6;
        float barWidth = BASE_WIDTH - HEAD_SIZE - PADDING * 3;

        int poppinsFontId = NanoVGRenderer.getPoppinsFontId();
        NanoVGRenderer.drawTextWithFont(data.name, barX, barY, NAME_SIZE, TEXT_COLOR, poppinsFontId);

        renderHealthBar(data, barX, barY + 10, barWidth);
    }

    private void renderHealthBar(RenderData data, float x, float y, float width) {
        int alpha = transparency.getValueInt();
        int barAlpha = Math.min(255, alpha + 20);
        Color barBgColor = new Color(BAR_BACKGROUND.getRed(), BAR_BACKGROUND.getGreen(), 
                                     BAR_BACKGROUND.getBlue(), barAlpha);
        
        NanoVGRenderer.drawRoundedRect(x, y, width, HEALTH_BAR_HEIGHT, HEALTH_BAR_RADIUS, barBgColor);

        updateHealthAnimation(data.healthPercent);
        
        float healthBarWidth = width * animatedHealthPercent;
        if (healthBarWidth > 0) {
            if (gradientHealth.getValue()) {
                renderGradientHealthBar(x, y, healthBarWidth);
            } else {
                Color accentColor = getAccentColor();
                NanoVGRenderer.drawRoundedRect(x, y, healthBarWidth, HEALTH_BAR_HEIGHT, 
                                              HEALTH_BAR_RADIUS, accentColor);
            }
        }
    }

    private void renderGradientHealthBar(float x, float y, float width) {
        Color accentColor = getAccentColor();
        Color blackColor = new Color(0, 0, 0);
        
        NanoVGRenderer.drawRoundedRectGradient(x, y, width, HEALTH_BAR_HEIGHT, 
                                              HEALTH_BAR_RADIUS, accentColor, blackColor);
    }

    private void updateHealthAnimation(float targetHealthPercent) {
        float deltaTime = mc.getRenderTickCounter().getTickDelta(true) / 20f;

        if (Math.abs(targetHealthPercent - lastHealthPercent) > 0.001f) {
            lastHealthPercent = targetHealthPercent;
        }

        if (animatedHealthPercent > lastHealthPercent) {
            animatedHealthPercent = Math.max(lastHealthPercent, 
                                            animatedHealthPercent - ANIMATION_SPEED * deltaTime);
        } else if (animatedHealthPercent < lastHealthPercent) {
            animatedHealthPercent = Math.min(lastHealthPercent, 
                                            animatedHealthPercent + ANIMATION_SPEED * deltaTime);
        }
    }

    private void renderEquipment(DrawContext context, PlayerEntity target, float startX, float hudY) {
        float itemX = startX;
        float itemY = hudY + (BASE_HEIGHT - ITEM_SIZE) / 2f;

        if (showHeldItem.getValue()) {
            ItemStack heldItem = target.getMainHandStack();
            if (!heldItem.isEmpty()) {
                renderItem(context, heldItem, itemX, itemY);
                itemX += ITEM_SIZE + ITEM_SPACING;
            }
        }

        if (showArmor.getValue()) {
            ItemStack[] armor = {
                target.getEquippedStack(EquipmentSlot.HEAD),
                target.getEquippedStack(EquipmentSlot.CHEST),
                target.getEquippedStack(EquipmentSlot.LEGS),
                target.getEquippedStack(EquipmentSlot.FEET)
            };
            
            for (ItemStack piece : armor) {
                if (!piece.isEmpty()) {
                    renderItem(context, piece, itemX, itemY);
                    itemX += ITEM_SIZE + ITEM_SPACING;
                }
            }
        }
    }

    private void renderItem(DrawContext context, ItemStack item, float x, float y) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(ITEM_SCALE, ITEM_SCALE, 1f);
        context.drawItem(item, 0, 0);
        context.getMatrices().pop();
    }

    @EventHandler
    private void onAttack(AttackEvent event) {
        if (!particles.getValue() || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }

        spawnParticles(target);
    }

    private void spawnParticles(LivingEntity target) {
        float x = draggable.getX();
        float y = draggable.getY();
        float headX = x + PADDING;
        float barX = headX + HEAD_SIZE + PADDING;
        float barY = y + PADDING + 6;
        float barWidth = BASE_WIDTH - HEAD_SIZE - PADDING * 3;

        float maxHealth = Math.max(target.getMaxHealth(), 1f);
        float health = MathHelper.clamp(target.getHealth(), 0f, maxHealth);
        float healthPercent = MathHelper.clamp(health / maxHealth, 0f, 1f);
        float healthBarWidth = barWidth * healthPercent;

        float spawnX = barX + healthBarWidth;
        float spawnY = barY + 10 + HEALTH_BAR_HEIGHT / 2f;

        Color accentColor = getAccentColor();
        int count = particleCount.getValueInt();
        long now = System.currentTimeMillis();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            float angle = (float) (rnd.nextDouble() * Math.PI * 2);
            float speed = 50 + rnd.nextFloat() * 100;
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed;
            float size = 2 + rnd.nextFloat() * 3;

            particleQueue.addLast(new Particle(spawnX, spawnY, vx, vy, size, accentColor, now));
        }
    }

    private void renderParticles(int screenWidth, int screenHeight) {
        long now = System.currentTimeMillis();
        float deltaTime = mc.getRenderTickCounter().getTickDelta(true) / 20f;

        Iterator<Particle> it = particleQueue.iterator();
        while (it.hasNext()) {
            Particle p = it.next();

            if (now - p.spawnTime > PARTICLE_LIFETIME_MS) {
                it.remove();
                continue;
            }

            p.x += p.vx * deltaTime;
            p.y += p.vy * deltaTime;
            p.vy += PARTICLE_GRAVITY * deltaTime;

            float life = (now - p.spawnTime) / (float) PARTICLE_LIFETIME_MS;
            int alpha = (int) ((1f - life) * p.color.getAlpha());

            if (alpha > 0) {
                Color particleColor = new Color(p.color.getRed(), p.color.getGreen(), 
                                               p.color.getBlue(), alpha);
                NanoVGRenderer.drawCircle(p.x, p.y, p.size, particleColor);
            }
        }
    }

    private PlayerEntity findTarget() {
        if (mc.player == null || mc.world == null) {
            return null;
        }

        if (mc.targetedEntity instanceof PlayerEntity player && player.isAlive() 
            && player != mc.player && !player.isSpectator()) {
            return player;
        }

        PlayerEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            
            double distance = mc.player.distanceTo(player);
            if (distance <= TARGET_RANGE && distance < closestDistance) {
                closest = player;
                closestDistance = distance;
            }
        }
        
        return closest;
    }

    private void renderPlayerHead(DrawContext context, PlayerEntity player, float hudX, float hudY) {
        Identifier texture = resolveSkin(player);
        if (texture == null) {
            return;
        }

        int x = (int) (hudX + PADDING);
        int y = (int) (hudY + PADDING);
        
        MatrixStack matrices = context.getMatrices();
        matrices.push();

        context.enableScissor(x, y, x + HEAD_SIZE, y + HEAD_SIZE);
        matrices.translate(x, y, 0);
        matrices.scale(HEAD_SCALE, HEAD_SCALE, 1f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.drawTexture(RenderLayer::getGuiTextured, texture, 0, 0, 8f, 8f, 8, 8, 64, 64);
        context.drawTexture(RenderLayer::getGuiTextured, texture, 0, 0, 40f, 8f, 8, 8, 64, 64);
        RenderSystem.disableBlend();

        context.disableScissor();
        matrices.pop();
    }

    private Identifier resolveSkin(PlayerEntity player) {
        SkinTextures textures;
        if (player instanceof AbstractClientPlayerEntity clientPlayer) {
            textures = clientPlayer.getSkinTextures();
        } else {
            textures = DefaultSkinHelper.getSkinTextures(player.getUuid());
        }
        return textures.texture();
    }

    private Color getAccentColor() {
        return cc.silk.module.modules.client.NewClickGUIModule.getAccentColor();
    }

    private static class Particle {
        float x, y;
        float vx, vy;
        float size;
        Color color;
        long spawnTime;

        Particle(float x, float y, float vx, float vy, float size, Color color, long spawnTime) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.color = color;
            this.spawnTime = spawnTime;
        }
    }

    private static class RenderData {
        final PlayerEntity target;
        final String name;
        final float healthPercent;

        RenderData(PlayerEntity target) {
            this.target = target;
            this.name = target != null ? target.getName().getString() : "Target";
            
            if (target != null) {
                float maxHealth = Math.max(target.getMaxHealth(), 1f);
                float health = MathHelper.clamp(target.getHealth(), 0f, maxHealth);
                this.healthPercent = MathHelper.clamp(health / maxHealth, 0f, 1f);
            } else {
                this.healthPercent = 0.75f;
            }
        }
    }
}

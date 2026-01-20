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

    private static final int BOX_WIDTH = 120;
    private static final int BOX_HEIGHT = 40;
    private static final int PADDING = 6;
    private static final int HEAD_SIZE = 28;
    private static final float CORNER_RADIUS = 12f;

    private final NumberSetting transparency = new NumberSetting("Transparency", 0, 255, 200, 5);
    private final BooleanSetting blur = new BooleanSetting("Blur", true);
    private final NumberSetting blurRadius = new NumberSetting("Blur Radius", 1, 30, 12, 1);
    private final BooleanSetting showArmor = new BooleanSetting("Show Armor", true);
    private final BooleanSetting showHeldItem = new BooleanSetting("Show Item", true);
    private final BooleanSetting particles = new BooleanSetting("Particles", true);
    private final NumberSetting particleCount = new NumberSetting("Particle Count", 5, 30, 15, 1);

    private final DraggableComponent draggable;
    private final Deque<HUDParticle> hudParticles = new ArrayDeque<>();

    private float animatedHealthPercent = 1.0f;
    private float lastHealthPercent = 1.0f;

    public TargetHUD() {
        super("Target HUD", "Displays information about the current combat target", -1, Category.RENDER);
        this.draggable = new DraggableComponent(20, 100, BOX_WIDTH, BOX_HEIGHT);
        addSettings(transparency, blur, blurRadius, showArmor, showHeldItem, particles, particleCount);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (isNull())
            return;

        PlayerEntity target = findTarget();
        if (target == null && mc.currentScreen == null) {
            return;
        }

        draggable.update();

        float x = draggable.getX();
        float y = draggable.getY();

        int itemCount = 0;
        if (target != null) {
            if (showHeldItem.getValue() && !target.getMainHandStack().isEmpty()) itemCount++;
            if (showArmor.getValue()) {
                if (!target.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) itemCount++;
                if (!target.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) itemCount++;
                if (!target.getEquippedStack(EquipmentSlot.LEGS).isEmpty()) itemCount++;
                if (!target.getEquippedStack(EquipmentSlot.FEET).isEmpty()) itemCount++;
            }
        }

        int dynamicWidth = BOX_WIDTH + (itemCount > 0 ? (itemCount * 14) + 4 : 0);
        int dynamicHeight = BOX_HEIGHT;

        if (blur.getValue()) {
            BlurRenderer.drawBlur(
                    event.getContext().getMatrices(),
                    x, y, dynamicWidth, dynamicHeight,
                    CORNER_RADIUS,
                    Color.WHITE,
                    (float) blurRadius.getValue()
            );
        }

        int alpha = transparency.getValueInt();
        int dragAlpha = Math.min(255, alpha + 30);
        Color bgColor = draggable.isDragging() ? new Color(30, 30, 35, dragAlpha) : new Color(20, 20, 25, blur.getValue() ? alpha / 2 : alpha);
        NanoVGRenderer.drawRoundedRect(x, y, dynamicWidth, dynamicHeight, CORNER_RADIUS, bgColor);

        if (draggable.isDragging()) {
            Color accentColor = cc.silk.module.modules.client.NewClickGUIModule.getAccentColor();
            NanoVGRenderer.drawRoundedRectOutline(x, y, dynamicWidth, dynamicHeight, CORNER_RADIUS, 2f, accentColor);
        }

        float headX = x + PADDING;
        float headY = y + PADDING;

        renderPlayerHeadCircle(headX, headY);

        float barX = headX + HEAD_SIZE + PADDING;
        float barY = y + PADDING + 6;
        float barWidth = BOX_WIDTH - HEAD_SIZE - PADDING * 3;
        float barHeight = 6;

        String name = target != null ? target.getName().getString() : "Target";
        float nameY = barY;
        drawPoppinsText(name, barX, nameY, 9f, new Color(255, 255, 255, 255));

        int barAlpha = Math.min(255, alpha + 20);
        Color barBgColor = new Color(15, 15, 20, barAlpha);
        float healthBarY = barY + 10;
        NanoVGRenderer.drawRoundedRect(barX, healthBarY, barWidth, barHeight, barHeight / 2f, barBgColor);

        float maxHealth = target != null ? Math.max(target.getMaxHealth(), 1f) : 20f;
        float health = target != null ? MathHelper.clamp(target.getHealth(), 0f, maxHealth) : 15f;
        float healthPercent = MathHelper.clamp(health / maxHealth, 0f, 1f);

        float deltaTime = mc.getRenderTickCounter().getTickDelta(true) / 20f;
        float animationSpeed = 3.0f;

        if (Math.abs(healthPercent - lastHealthPercent) > 0.001f) {
            lastHealthPercent = healthPercent;
        }

        if (animatedHealthPercent > lastHealthPercent) {
            animatedHealthPercent = Math.max(lastHealthPercent, animatedHealthPercent - animationSpeed * deltaTime);
        } else if (animatedHealthPercent < lastHealthPercent) {
            animatedHealthPercent = Math.min(lastHealthPercent, animatedHealthPercent + animationSpeed * deltaTime);
        }

        float healthBarWidth = barWidth * animatedHealthPercent;

        if (healthBarWidth > 0) {
            Color accentColor = cc.silk.module.modules.client.NewClickGUIModule.getAccentColor();
            NanoVGRenderer.drawRoundedRect(barX, healthBarY, healthBarWidth, barHeight, barHeight / 2f, accentColor);
        }

        if (particles.getValue()) {
            renderParticles(event.getContext().getScaledWindowWidth(), event.getContext().getScaledWindowHeight());
        }

        if (target != null) {
            renderPlayerHead(event.getContext(), target, (int) headX, (int) headY);
        }

        if (target != null && (showArmor.getValue() || showHeldItem.getValue())) {
            renderEquipment(event.getContext(), target, x + BOX_WIDTH - 2, y);
        }
    }

    private void renderEquipment(DrawContext context, PlayerEntity target, float startX, float hudY) {
        float itemX = startX;
        float itemY = hudY + (BOX_HEIGHT - 14) / 2f;
        int itemSize = 14;
        int spacing = 0;

        if (showHeldItem.getValue()) {
            ItemStack heldItem = target.getMainHandStack();
            if (!heldItem.isEmpty()) {
                context.getMatrices().push();
                context.getMatrices().translate(itemX, itemY, 0);
                context.getMatrices().scale(0.875f, 0.875f, 1f);
                context.drawItem(heldItem, 0, 0);
                context.getMatrices().pop();
                itemX += itemSize + spacing;
            }
        }

        if (showArmor.getValue()) {
            ItemStack helmet = target.getEquippedStack(EquipmentSlot.HEAD);
            ItemStack chestplate = target.getEquippedStack(EquipmentSlot.CHEST);
            ItemStack leggings = target.getEquippedStack(EquipmentSlot.LEGS);
            ItemStack boots = target.getEquippedStack(EquipmentSlot.FEET);

            ItemStack[] armor = {helmet, chestplate, leggings, boots};
            for (ItemStack piece : armor) {
                if (!piece.isEmpty()) {
                    context.getMatrices().push();
                    context.getMatrices().translate(itemX, itemY, 0);
                    context.getMatrices().scale(0.875f, 0.875f, 1f);
                    context.drawItem(piece, 0, 0);
                    context.getMatrices().pop();
                    itemX += itemSize + spacing;
                }
            }
        }
    }

    @EventHandler
    private void onAttack(AttackEvent e) {
        if (!particles.getValue())
            return;
        if (!(e.getTarget() instanceof LivingEntity target))
            return;

        long now = System.currentTimeMillis();
        int count = particleCount.getValueInt();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        float x = draggable.getX();
        float y = draggable.getY();
        float headX = x + PADDING;
        float barX = headX + HEAD_SIZE + PADDING;
        float barY = y + PADDING + 6;
        float barWidth = BOX_WIDTH - HEAD_SIZE - PADDING * 3;
        float barHeight = 6;

        float maxHealth = Math.max(target.getMaxHealth(), 1f);
        float health = MathHelper.clamp(target.getHealth(), 0f, maxHealth);
        float healthPercent = MathHelper.clamp(health / maxHealth, 0f, 1f);
        float healthBarWidth = barWidth * healthPercent;

        float particleSpawnX = barX + healthBarWidth;
        float particleSpawnY = barY + 10 + barHeight / 2f;

        Color accentColor = cc.silk.module.modules.client.NewClickGUIModule.getAccentColor();

        for (int i = 0; i < count; i++) {
            float angle = (float) (rnd.nextDouble() * Math.PI * 2);
            float speed = 50 + rnd.nextFloat() * 100;
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed;
            float size = 2 + rnd.nextFloat() * 3;

            hudParticles.addLast(new HUDParticle(particleSpawnX, particleSpawnY, vx, vy, size, accentColor, now));
        }
    }

    private void renderParticles(int screenWidth, int screenHeight) {
        long now = System.currentTimeMillis();
        float deltaTime = mc.getRenderTickCounter().getTickDelta(true) / 20f;

        Iterator<HUDParticle> it = hudParticles.iterator();
        while (it.hasNext()) {
            HUDParticle p = it.next();

            if (now - p.spawnTime > 1000) {
                it.remove();
                continue;
            }

            p.x += p.vx * deltaTime;
            p.y += p.vy * deltaTime;
            p.vy += 200 * deltaTime;

            float life = (now - p.spawnTime) / 1000f;
            int alpha = (int) ((1f - life) * p.color.getAlpha());

            if (alpha > 0) {
                Color particleColor = new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), alpha);
                NanoVGRenderer.drawCircle(p.x, p.y, p.size, particleColor);
            }
        }
    }

    private static class HUDParticle {
        float x, y;
        float vx, vy;
        float size;
        Color color;
        long spawnTime;

        HUDParticle(float x, float y, float vx, float vy, float size, Color color, long spawnTime) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.color = color;
            this.spawnTime = spawnTime;
        }
    }

    private PlayerEntity findTarget() {
        if (mc.player == null || mc.world == null) {
            return null;
        }

        if (mc.targetedEntity instanceof PlayerEntity player && player.isAlive() && player != mc.player
                && !player.isSpectator()) {
            return player;
        }

        double range = 8.0;
        PlayerEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator())
                continue;
            double distance = mc.player.distanceTo(player);
            if (distance <= range && distance < closestDistance) {
                closest = player;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private void renderPlayerHeadCircle(float x, float y) {
        float centerX = x + HEAD_SIZE / 2f;
        float centerY = y + HEAD_SIZE / 2f;
        float radius = HEAD_SIZE / 2f;

        Color accentColor = cc.silk.module.modules.client.NewClickGUIModule.getAccentColor();
        Color headBg = new Color(
                accentColor.getRed(),
                accentColor.getGreen(),
                accentColor.getBlue(),
                80);

        NanoVGRenderer.drawCircle(centerX, centerY, radius, headBg);
    }

    private void drawPoppinsText(String text, float x, float y, float size, Color color) {
        int poppinsFontId = NanoVGRenderer.getPoppinsFontId();
        NanoVGRenderer.drawTextWithFont(text, x, y, size, color, poppinsFontId);
    }

    private void renderPlayerHead(DrawContext context, PlayerEntity player, int x, int y) {
        Identifier texture = resolveSkin(player);
        if (texture == null) {
            return;
        }

        MatrixStack matrices = context.getMatrices();
        matrices.push();

        context.enableScissor(x, y, x + HEAD_SIZE, y + HEAD_SIZE);

        matrices.translate(x, y, 0);
        float scale = HEAD_SIZE / 8.0f;
        matrices.scale(scale, scale, 1f);

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
}

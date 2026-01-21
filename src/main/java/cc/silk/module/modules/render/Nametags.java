package cc.silk.module.modules.render;

import cc.silk.event.impl.render.Render2DEvent;
import cc.silk.mixin.WorldRendererAccessor;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.BooleanSetting;
import cc.silk.module.setting.ColorSetting;
import cc.silk.module.setting.ModeSetting;
import cc.silk.module.setting.NumberSetting;
import cc.silk.utils.render.W2SUtil;
import cc.silk.utils.render.nanovg.NanoVGRenderer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4d;

import java.awt.*;

public class Nametags extends Module {

    private final ModeSetting targets = new ModeSetting("Targets", "Players", "Players", "Passives", "Hostiles", "All");
    private final BooleanSetting showSelf = new BooleanSetting("Show Self", false);
    private final NumberSetting range = new NumberSetting("Range", 10, 200, 64, 5);
    private final BooleanSetting showHealth = new BooleanSetting("Show Health", true);
    private final BooleanSetting showArmor = new BooleanSetting("Show Armor", true);
    private final BooleanSetting showWeapon = new BooleanSetting("Show Weapon", true);
    private final BooleanSetting scaleWithDistance = new BooleanSetting("Scale With Distance", true);
    private final NumberSetting minScale = new NumberSetting("Min Scale", 0.1, 2.0, 0.5, 0.1);
    private final NumberSetting maxScale = new NumberSetting("Max Scale", 0.1, 3.0, 1.5, 0.1);
    private final BooleanSetting teamColor = new BooleanSetting("Team Color", false);
    private final ColorSetting borderColor = new ColorSetting("Border Color", new Color(255, 255, 255));
    private final ColorSetting playerColor = new ColorSetting("Player Color", new Color(255, 255, 255));
    private final ColorSetting passiveColor = new ColorSetting("Passive Color", new Color(0, 255, 0));
    private final ColorSetting hostileColor = new ColorSetting("Hostile Color", new Color(255, 0, 0));

    public Nametags() {
        super("Nametags", "Displays nametags above entities", -1, Category.RENDER);
        addSettings(targets, showSelf, range, showHealth, showArmor, showWeapon, scaleWithDistance, 
                   minScale, maxScale, teamColor, borderColor, playerColor, passiveColor, hostileColor);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (isNull() || mc.world == null || mc.player == null)
            return;

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

        NanoVGRenderer.beginFrame();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity))
                continue;

            if (!shouldRender(entity))
                continue;

            Box box = entity.getBoundingBox();

            if (!((WorldRendererAccessor) mc.worldRenderer).getFrustum().isVisible(box))
                continue;

            double x = entity.prevX + (entity.getX() - entity.prevX) * mc.getRenderTickCounter().getTickDelta(false);
            double y = entity.prevY + (entity.getY() - entity.prevY) * mc.getRenderTickCounter().getTickDelta(false);
            double z = entity.prevZ + (entity.getZ() - entity.prevZ) * mc.getRenderTickCounter().getTickDelta(false);

            Box expandedBox = new Box(
                box.minX - entity.getX() + x - 0.05,
                box.minY - entity.getY() + y,
                box.minZ - entity.getZ() + z - 0.05,
                box.maxX - entity.getX() + x + 0.05,
                box.maxY - entity.getY() + y + 0.1,
                box.maxZ - entity.getZ() + z + 0.05
            );

            Vec3d[] vectors = new Vec3d[] {
                new Vec3d(expandedBox.minX, expandedBox.minY, expandedBox.minZ),
                new Vec3d(expandedBox.minX, expandedBox.maxY, expandedBox.minZ),
                new Vec3d(expandedBox.maxX, expandedBox.minY, expandedBox.minZ),
                new Vec3d(expandedBox.maxX, expandedBox.maxY, expandedBox.minZ),
                new Vec3d(expandedBox.minX, expandedBox.minY, expandedBox.maxZ),
                new Vec3d(expandedBox.minX, expandedBox.maxY, expandedBox.maxZ),
                new Vec3d(expandedBox.maxX, expandedBox.minY, expandedBox.maxZ),
                new Vec3d(expandedBox.maxX, expandedBox.maxY, expandedBox.maxZ),
            };

            Vector4d position = null;

            for (Vec3d vector : vectors) {
                Vec3d vectorToScreen = W2SUtil.getCoords(vector);

                if (vectorToScreen.z > 0 && vectorToScreen.z < 1) {
                    if (position == null) {
                        position = new Vector4d(vectorToScreen.x, vectorToScreen.y, vectorToScreen.z, 0);
                    }

                    position.x = Math.min(vectorToScreen.x, position.x);
                    position.y = Math.min(vectorToScreen.y, position.y);
                    position.z = Math.max(vectorToScreen.x, position.z);
                    position.w = Math.max(vectorToScreen.y, position.w);
                }
            }

            if (position != null) {
                float posX = (float) position.x;
                float posY = (float) position.y;
                float endPosX = (float) position.z;

                LivingEntity livingEntity = (LivingEntity) entity;
                String name = entity.getName().getString();
                float health = livingEntity.getHealth();
                float maxHealth = livingEntity.getMaxHealth();

                float distance = mc.player.distanceTo(entity);
                float scale = 1.0f;
                
                if (scaleWithDistance.getValue()) {
                    float minDist = 5.0f;
                    float maxDist = (float) range.getValue();
                    float normalizedDist = Math.min(Math.max((distance - minDist) / (maxDist - minDist), 0), 1);
                    scale = (float) (maxScale.getValue() - normalizedDist * (maxScale.getValue() - minScale.getValue()));
                }

                String healthText = showHealth.getValue() ? String.format(" %.1f", health) : "";

                float fontSize = 9f * scale;
                float nameWidth = NanoVGRenderer.getTextWidth(name, fontSize);
                float healthWidth = showHealth.getValue() ? NanoVGRenderer.getTextWidth(healthText, fontSize) : 0;
                float totalWidth = nameWidth + healthWidth;

                float padding = 3f * scale;
                float tagWidth = totalWidth + padding * 2;
                float tagHeight = fontSize + padding * 2;
                float tagX = posX + (endPosX - posX - tagWidth) / 2f;
                float tagY = posY - tagHeight - 3f * scale;

                Color bgColor = new Color(0, 0, 0, 180);
                Color accentColor = getColorForEntity(entity);
                Color border = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 200);

                NanoVGRenderer.drawRoundedRect(tagX, tagY, tagWidth, tagHeight, 3f * scale, bgColor);
                NanoVGRenderer.drawRoundedRectOutline(tagX, tagY, tagWidth, tagHeight, 3f * scale, 1f * scale, border);

                float textX = tagX + padding;
                float textY = tagY + padding;

                Color nameColor = new Color(255, 255, 255, 255);
                NanoVGRenderer.drawText(name, textX, textY, fontSize, nameColor);

                if (showHealth.getValue()) {
                    float healthPercent = health / maxHealth;
                    Color healthColor;
                    if (healthPercent > 0.6f) {
                        healthColor = new Color(85, 255, 85, 255);
                    } else if (healthPercent > 0.3f) {
                        healthColor = new Color(255, 255, 85, 255);
                    } else {
                        healthColor = new Color(255, 85, 85, 255);
                    }

                    NanoVGRenderer.drawText(healthText, textX + nameWidth, textY, fontSize, healthColor);
                }
            }
        }

        NanoVGRenderer.endFrame();

        renderItems(event);

        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderItems(Render2DEvent event) {
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player))
                continue;

            if (!shouldRender(entity))
                continue;

            Box box = entity.getBoundingBox();

            if (!((WorldRendererAccessor) mc.worldRenderer).getFrustum().isVisible(box))
                continue;

            double x = entity.prevX + (entity.getX() - entity.prevX) * mc.getRenderTickCounter().getTickDelta(false);
            double y = entity.prevY + (entity.getY() - entity.prevY) * mc.getRenderTickCounter().getTickDelta(false);
            double z = entity.prevZ + (entity.getZ() - entity.prevZ) * mc.getRenderTickCounter().getTickDelta(false);

            Box expandedBox = new Box(
                box.minX - entity.getX() + x - 0.05,
                box.minY - entity.getY() + y,
                box.minZ - entity.getZ() + z - 0.05,
                box.maxX - entity.getX() + x + 0.05,
                box.maxY - entity.getY() + y + 0.1,
                box.maxZ - entity.getZ() + z + 0.05
            );

            Vec3d[] vectors = new Vec3d[] {
                new Vec3d(expandedBox.minX, expandedBox.minY, expandedBox.minZ),
                new Vec3d(expandedBox.minX, expandedBox.maxY, expandedBox.minZ),
                new Vec3d(expandedBox.maxX, expandedBox.minY, expandedBox.minZ),
                new Vec3d(expandedBox.maxX, expandedBox.maxY, expandedBox.minZ),
                new Vec3d(expandedBox.minX, expandedBox.minY, expandedBox.maxZ),
                new Vec3d(expandedBox.minX, expandedBox.maxY, expandedBox.maxZ),
                new Vec3d(expandedBox.maxX, expandedBox.minY, expandedBox.maxZ),
                new Vec3d(expandedBox.maxX, expandedBox.maxY, expandedBox.maxZ),
            };

            Vector4d position = null;

            for (Vec3d vector : vectors) {
                Vec3d vectorToScreen = W2SUtil.getCoords(vector);

                if (vectorToScreen.z > 0 && vectorToScreen.z < 1) {
                    if (position == null) {
                        position = new Vector4d(vectorToScreen.x, vectorToScreen.y, vectorToScreen.z, 0);
                    }

                    position.x = Math.min(vectorToScreen.x, position.x);
                    position.y = Math.min(vectorToScreen.y, position.y);
                    position.z = Math.max(vectorToScreen.x, position.z);
                    position.w = Math.max(vectorToScreen.y, position.w);
                }
            }

            if (position != null) {
                float posX = (float) position.x;
                float posY = (float) position.y;
                float endPosX = (float) position.z;

                float distance = mc.player.distanceTo(entity);
                float scale = 1.0f;
                
                if (scaleWithDistance.getValue()) {
                    float minDist = 5.0f;
                    float maxDist = (float) range.getValue();
                    float normalizedDist = Math.min(Math.max((distance - minDist) / (maxDist - minDist), 0), 1);
                    scale = (float) (maxScale.getValue() - normalizedDist * (maxScale.getValue() - minScale.getValue()));
                }

                String name = entity.getName().getString();
                String healthText = showHealth.getValue() ? String.format(" %.1f", player.getHealth()) : "";

                float fontSize = 9f * scale;
                float nameWidth = NanoVGRenderer.getTextWidth(name, fontSize);
                float healthWidth = showHealth.getValue() ? NanoVGRenderer.getTextWidth(healthText, fontSize) : 0;
                float totalWidth = nameWidth + healthWidth;

                float padding = 3f * scale;
                float tagWidth = totalWidth + padding * 2;
                float tagHeight = fontSize + padding * 2;
                float tagX = posX + (endPosX - posX - tagWidth) / 2f;
                float tagY = posY - tagHeight - 3f * scale;

                float itemY = tagY - 18f * scale - 2f * scale;
                float itemSize = 16f * scale;
                float itemSpacing = 2f * scale;

                java.util.List<net.minecraft.item.ItemStack> items = new java.util.ArrayList<>();
                
                if (showWeapon.getValue() && !player.getMainHandStack().isEmpty()) {
                    items.add(player.getMainHandStack());
                }
                
                if (showArmor.getValue()) {
                    for (net.minecraft.item.ItemStack armor : player.getInventory().armor) {
                        if (!armor.isEmpty()) {
                            items.add(armor);
                        }
                    }
                }

                if (!items.isEmpty()) {
                    float totalItemWidth = items.size() * itemSize + (items.size() - 1) * itemSpacing;
                    float itemX = posX + (endPosX - posX - totalItemWidth) / 2f;

                    event.getContext().getMatrices().push();
                    event.getContext().getMatrices().translate(itemX, itemY, 0);
                    event.getContext().getMatrices().scale(scale, scale, 1);

                    for (int i = 0; i < items.size(); i++) {
                        net.minecraft.item.ItemStack stack = items.get(i);
                        float xOffset = i * (itemSize + itemSpacing) / scale;
                        event.getContext().drawItem(stack, (int) xOffset, 0);
                    }

                    event.getContext().getMatrices().pop();
                }
            }
        }
    }

    private Color getColorForEntity(Entity entity) {
        if (teamColor.getValue() && entity instanceof LivingEntity livingEntity) {
            Team team = livingEntity.getScoreboardTeam();
            if (team != null) {
                Formatting teamFormatting = team.getColor();
                if (teamFormatting != null && teamFormatting.getColorValue() != null) {
                    return new Color(teamFormatting.getColorValue());
                }
            }
        }

        if (entity instanceof PlayerEntity)
            return playerColor.getValue();
        if (entity instanceof PassiveEntity)
            return passiveColor.getValue();
        if (entity instanceof HostileEntity)
            return hostileColor.getValue();
        return borderColor.getValue();
    }

    private boolean shouldRender(Entity entity) {
        if (entity == mc.player && !showSelf.getValue())
            return false;

        if (mc.player.distanceTo(entity) > range.getValue())
            return false;

        return switch (targets.getMode()) {
            case "Players" -> entity instanceof PlayerEntity;
            case "Passives" -> entity instanceof PassiveEntity;
            case "Hostiles" -> entity instanceof HostileEntity;
            case "All" -> entity instanceof PlayerEntity || entity instanceof PassiveEntity || entity instanceof HostileEntity;
            default -> false;
        };
    }
}

package cc.silk.module.modules.render;

import cc.silk.mixin.WorldRendererAccessor;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.BooleanSetting;
import cc.silk.module.setting.ColorSetting;
import cc.silk.module.setting.NumberSetting;
import cc.silk.utils.friend.FriendManager;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class CustomOutlineESP extends Module {

    private static CustomOutlineESP instance;

    private final BooleanSetting showSelf = new BooleanSetting("Show Self", false);
    private final BooleanSetting showPlayers = new BooleanSetting("Show Players", true);
    private final BooleanSetting showPassives = new BooleanSetting("Show Passives", false);
    private final BooleanSetting showHostiles = new BooleanSetting("Show Hostiles", false);
    private final NumberSetting range = new NumberSetting("Range", 10, 200, 100, 5);
    private final NumberSetting outlineWidth = new NumberSetting("Outline Width", 1, 10, 3, 1);
    private final NumberSetting intensity = new NumberSetting("Intensity", 0.1, 2.0, 1.0, 0.1);
    private final BooleanSetting showFill = new BooleanSetting("Show Fill", true);
    private final NumberSetting fillAlpha = new NumberSetting("Fill Alpha", 0, 255, 50, 5);
    private final ColorSetting playerColor = new ColorSetting("Player Color", new Color(255, 50, 50));
    private final ColorSetting friendColor = new ColorSetting("Friend Color", new Color(50, 255, 50));
    private final ColorSetting passiveColor = new ColorSetting("Passive Color", new Color(50, 255, 50));
    private final ColorSetting hostileColor = new ColorSetting("Hostile Color", new Color(255, 165, 0));

    private final Set<Entity> targetEntities = new HashSet<>();

    public CustomOutlineESP() {
        super("Custom Outline", "Custom post-processed outline ESP", Category.RENDER);
        instance = this;
        addSettings(showSelf, showPlayers, showPassives, showHostiles, range,
                outlineWidth, intensity, showFill, fillAlpha, playerColor, friendColor, passiveColor, hostileColor);
    }

    public static CustomOutlineESP getInstance() {
        return instance;
    }


    public boolean shouldRenderOutline(Entity entity) {
        if (!isEnabled() || isNull()) return false;
        return shouldTarget(entity);
    }

    public void updateTargets() {
        targetEntities.clear();
        if (isNull() || mc.world == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity && shouldTarget(entity)) {
                targetEntities.add(entity);
            }
        }
    }

    public Set<Entity> getTargetEntities() {
        return targetEntities;
    }

    private boolean shouldTarget(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (mc.player == null) return false;
        if (mc.player.squaredDistanceTo(entity) > range.getValue() * range.getValue()) return false;

        if (entity instanceof PlayerEntity player) {
            if (player == mc.player && !showSelf.getValue()) return false;
            return showPlayers.getValue();
        }
        if (entity instanceof PassiveEntity) return showPassives.getValue();
        if (entity instanceof HostileEntity) return showHostiles.getValue();

        return false;
    }

    public Color getColorForEntity(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            if (FriendManager.isFriend(player.getUuid())) {
                return friendColor.getValue();
            }
            return playerColor.getValue();
        }
        if (entity instanceof PassiveEntity) return passiveColor.getValue();
        if (entity instanceof HostileEntity) return hostileColor.getValue();
        return playerColor.getValue();
    }

    public float getOutlineWidth() {
        return outlineWidth.getValueFloat();
    }

    public float getIntensity() {
        return intensity.getValueFloat();
    }

    public boolean shouldShowFill() {
        return showFill.getValue();
    }

    public float getFillAlpha() {
        return fillAlpha.getValueFloat() / 255f;
    }

    public Framebuffer getOutlineFramebuffer() {
        if (mc.worldRenderer == null) return null;
        return ((WorldRendererAccessor) mc.worldRenderer).getEntityOutlineFramebuffer();
    }
}

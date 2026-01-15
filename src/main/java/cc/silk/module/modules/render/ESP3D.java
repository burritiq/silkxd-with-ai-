package cc.silk.module.modules.render;

import cc.silk.event.impl.render.Render3DEvent;
import cc.silk.mixin.WorldRendererAccessor;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.BooleanSetting;
import cc.silk.module.setting.ColorSetting;
import cc.silk.module.setting.ModeSetting;
import cc.silk.module.setting.NumberSetting;
import cc.silk.utils.friend.FriendManager;
import cc.silk.utils.render.Render3DEngine;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.*;

public class ESP3D extends Module {

    private final ModeSetting targets = new ModeSetting("Targets", "Players", "Players", "Passives", "Hostiles", "All");
    private final BooleanSetting showSelf = new BooleanSetting("Show Self", false);
    private final NumberSetting range = new NumberSetting("Range", 10, 200, 64, 5);
    private final ModeSetting mode = new ModeSetting("Mode", "Both", "Outline", "Filled", "Both");
    private final NumberSetting fillOpacity = new NumberSetting("Fill Opacity", 0, 255, 50, 5);
    private final BooleanSetting throughWalls = new BooleanSetting("Through Walls", true);
    private final BooleanSetting teamColor = new BooleanSetting("Team Color", false);
    private final ColorSetting playerColor = new ColorSetting("Player Color", new Color(255, 50, 50));
    private final ColorSetting friendColor = new ColorSetting("Friend Color", new Color(50, 255, 50));
    private final ColorSetting passiveColor = new ColorSetting("Passive Color", new Color(50, 255, 50));
    private final ColorSetting hostileColor = new ColorSetting("Hostile Color", new Color(255, 165, 0));

    public ESP3D() {
        super("3D ESP", "Draws 3D boxes around entities", -1, Category.RENDER);
        addSettings(targets, showSelf, range, mode, fillOpacity, throughWalls,
                teamColor, playerColor, friendColor, passiveColor, hostileColor);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (isNull() || mc.world == null) return;

        MatrixStack stack = event.getMatrixStack();
        float tickDelta = mc.getRenderTickCounter().getTickDelta(true);

        if (throughWalls.getValue()) {
            Render3DEngine.setupThroughWalls();
        } else {
            Render3DEngine.setup();
        }

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity)) continue;
            if (!shouldRender(entity)) continue;

            Box box = entity.getBoundingBox();
            if (!((WorldRendererAccessor) mc.worldRenderer).getFrustum().isVisible(box)) continue;

            Vec3d pos = Render3DEngine.getInterpolatedPos(entity, tickDelta);
            Color color = getEntityColor(entity);

            if (mode.isMode("Filled") || mode.isMode("Both")) {
                Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), fillOpacity.getValueInt());
                Render3DEngine.drawFilledBox(stack, pos.x, pos.y, pos.z, entity.getWidth(), entity.getHeight(), fill);
            }

            if (mode.isMode("Outline") || mode.isMode("Both")) {
                Render3DEngine.drawOutlineBox(stack, pos.x, pos.y, pos.z, entity.getWidth(), entity.getHeight(), color);
            }
        }

        Render3DEngine.end();
    }

    private boolean shouldRender(Entity entity) {
        if (entity == mc.player && !showSelf.getValue()) return false;
        if (mc.player.squaredDistanceTo(entity) > range.getValue() * range.getValue()) return false;

        String targetMode = targets.getMode();
        if (entity instanceof PlayerEntity) {
            return targetMode.equals("Players") || targetMode.equals("All");
        } else if (entity instanceof PassiveEntity) {
            return targetMode.equals("Passives") || targetMode.equals("All");
        } else if (entity instanceof HostileEntity) {
            return targetMode.equals("Hostiles") || targetMode.equals("All");
        }
        return targetMode.equals("All");
    }

    private Color getEntityColor(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            if (FriendManager.isFriend(player.getUuid())) {
                return friendColor.getValue();
            }
            if (teamColor.getValue()) {
                Team team = player.getScoreboardTeam();
                if (team != null && team.getColor().getColorValue() != null) {
                    return new Color(team.getColor().getColorValue());
                }
            }
            return playerColor.getValue();
        } else if (entity instanceof PassiveEntity) {
            return passiveColor.getValue();
        } else if (entity instanceof HostileEntity) {
            return hostileColor.getValue();
        }
        return playerColor.getValue();
    }
}

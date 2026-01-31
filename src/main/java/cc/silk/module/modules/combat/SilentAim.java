package cc.silk.module.modules.combat;

import cc.silk.event.impl.input.HandleInputEvent;
import cc.silk.event.impl.player.AttackEvent;
import cc.silk.event.impl.player.TickEvent;
import cc.silk.event.impl.render.Render3DEvent;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.modules.misc.Teams;
import cc.silk.module.setting.BooleanSetting;
import cc.silk.module.setting.NumberSetting;
import cc.silk.utils.friend.FriendManager;
import cc.silk.utils.render.Render3DEngine;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class SilentAim extends Module {

    private final NumberSetting range = new NumberSetting("Range", 1.0, 6.0, 4.0, 0.1);
    private final NumberSetting fov = new NumberSetting("FOV", 10.0, 180.0, 60.0, 5.0);
    private final BooleanSetting targetPlayers = new BooleanSetting("Target Players", true);
    private final BooleanSetting targetMobs = new BooleanSetting("Target Mobs", false);
    private final BooleanSetting targetCrystals = new BooleanSetting("Target Crystals", false);
    private final BooleanSetting ignoreFriends = new BooleanSetting("Ignore Friends", true);
    private final BooleanSetting ignoreInvisible = new BooleanSetting("Ignore Invisible", false);
    private final BooleanSetting weaponsOnly = new BooleanSetting("Weapons Only", true);
    private final BooleanSetting showVisuals = new BooleanSetting("Show Visuals", true);

    // Current tracked target
    private Entity currentTarget = null;

    public SilentAim() {
        super("Silent Aim", "Silent aim assist - tracks targets in FOV without moving camera", Category.COMBAT);
        addSettings(range, fov, targetPlayers, targetMobs, targetCrystals, ignoreFriends, ignoreInvisible, weaponsOnly,
                showVisuals);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        currentTarget = null;
        SilentAimHelper.clear();
    }

    /**
     * On every tick, find target in FOV and set it via SilentAimHelper.
     * The mixin intercepts raycast and returns our target, making attack indicator
     * appear.
     */
    @EventHandler
    private void onTick(TickEvent event) {
        if (isNull()) {
            SilentAimHelper.clear();
            return;
        }
        if (mc.currentScreen != null) {
            SilentAimHelper.clear();
            return;
        }

        // Check weapons only setting
        if (weaponsOnly.getValue() && !isHoldingWeapon()) {
            currentTarget = null;
            SilentAimHelper.clear();
            return;
        }

        // Find target within FOV circle
        Entity target = findTargetInFOV();

        if (target != null) {
            currentTarget = target;
            // Set the silent aim target - mixin will intercept raycast and return this
            SilentAimHelper.setSilentTarget(target);
        } else {
            currentTarget = null;
            SilentAimHelper.clear();
        }
    }

    /**
     * Before an attack, send rotation packet to server so hit registers on our
     * target.
     */
    @EventHandler
    private void onAttack(AttackEvent event) {
        if (isNull() || currentTarget == null)
            return;

        // Use vectors to find the closest point on the target's hitbox
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d aimPoint = getClosestPointOnHitbox(eyePos, currentTarget.getBoundingBox());

        // Calculate rotation to that point
        float[] targetRotations = calculateRotation(aimPoint);

        // Find shortest yaw path
        float targetYaw = targetRotations[0];
        float currentYaw = mc.player.getYaw();
        float shortestYaw = currentYaw + MathHelper.wrapDegrees(targetYaw - currentYaw);

        // Send silent rotation packet BEFORE the attack
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                shortestYaw,
                targetRotations[1],
                mc.player.isOnGround(),
                mc.player.horizontalCollision));
    }

    /**
     * Handle left-click input to manually attack silent aim targets when clicking
     * air.
     */
    @EventHandler
    private void onHandleInput(HandleInputEvent event) {
        if (isNull() || currentTarget == null)
            return;
        if (mc.currentScreen != null)
            return;

        // Check if left mouse button is pressed
        if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS)
            return;

        // Only manually attack if we're not already targeting something (clicking on
        // air/block)
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY)
            return;

        // Prevent attack spam - use cooldown
        if (mc.player.getAttackCooldownProgress(0.0f) < 1.0f)
            return;

        // Calculate rotation to target
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d aimPoint = getClosestPointOnHitbox(eyePos, currentTarget.getBoundingBox());
        float[] targetRotations = calculateRotation(aimPoint);
        float targetYaw = targetRotations[0];
        float currentYaw = mc.player.getYaw();
        float shortestYaw = currentYaw + MathHelper.wrapDegrees(targetYaw - currentYaw);

        // Send rotation packet
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                shortestYaw,
                targetRotations[1],
                mc.player.isOnGround(),
                mc.player.horizontalCollision));

        // Send attack packet to the target
        mc.player.networkHandler
                .sendPacket(PlayerInteractEntityC2SPacket.attack(currentTarget, mc.player.isSneaking()));

        // Swing arm animation
        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        mc.player.swingHand(Hand.MAIN_HAND);

        // Reset attack cooldown
        mc.player.resetLastAttackedTicks();
    }

    /**
     * Finds the closest valid target whose hitbox is within the FOV circle.
     * For crystals, also checks if they're above the block we're looking at.
     */
    private Entity findTargetInFOV() {
        Entity closest = null;
        double closestDistance = range.getValue();

        // Special handling for crystals: find crystals near the block we're aiming at
        if (targetCrystals.getValue() && mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult blockHit) {
            net.minecraft.util.math.BlockPos targetPos = blockHit.getBlockPos();
            // Check for crystals above or on the target block
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof EndCrystalEntity))
                    continue;
                if (entity.isRemoved())
                    continue;

                double distance = mc.player.distanceTo(entity);
                if (distance > range.getValue())
                    continue;

                // Check if crystal is near the block we're looking at (within 2 blocks)
                double dx = Math.abs(entity.getX() - targetPos.getX() - 0.5);
                double dz = Math.abs(entity.getZ() - targetPos.getZ() - 0.5);
                double dy = entity.getY() - targetPos.getY();

                if (dx <= 1.5 && dz <= 1.5 && dy >= -0.5 && dy <= 2.5) {
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closest = entity;
                    }
                }
            }
            if (closest != null)
                return closest;
        }

        // Normal FOV-based targeting for players/mobs
        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity))
                continue;

            // Check if entity hitbox is within FOV
            if (!isInFOV(entity))
                continue;

            double distance = mc.player.distanceTo(entity);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = entity;
            }
        }

        return closest;
    }

    /**
     * Checks if any part of the entity's hitbox is within the FOV circle.
     */
    private boolean isInFOV(Entity entity) {
        Vec3d eyePos = mc.player.getEyePos();
        Box hitbox = entity.getBoundingBox();

        // Get the closest point on hitbox to check FOV
        Vec3d targetPoint = getClosestPointOnHitbox(eyePos, hitbox);

        // Calculate angle to target point
        float[] targetRot = calculateRotation(targetPoint);
        float yawDiff = MathHelper.wrapDegrees(targetRot[0] - mc.player.getYaw());
        float pitchDiff = targetRot[1] - mc.player.getPitch();

        // Calculate total angular distance
        double angleDiff = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);

        // Check if within FOV circle (half because FOV is total diameter)
        return angleDiff <= fov.getValue() / 2.0;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (isNull() || !showVisuals.getValue())
            return;

        // Find target within FOV for visualization
        Entity target = findTargetInFOV();
        if (target == null) {
            currentTarget = null;
            return;
        }

        currentTarget = target;

        float tickDelta = mc.getRenderTickCounter().getTickDelta(true);

        // Get interpolated target position (camera-relative)
        Vec3d targetPos = Render3DEngine.getInterpolatedPos(target, tickDelta);

        // Get player eye position (interpolated and camera-relative)
        Vec3d playerPos = Render3DEngine.getInterpolatedPos(mc.player, tickDelta);
        Vec3d eyePos = playerPos.add(0, mc.player.getEyeHeight(mc.player.getPose()), 0);

        // Calculate aim point on hitbox (need to offset hitbox to interpolated
        // position)
        Box interpolatedHitbox = new Box(
                targetPos.x - target.getWidth() / 2, targetPos.y, targetPos.z - target.getWidth() / 2,
                targetPos.x + target.getWidth() / 2, targetPos.y + target.getHeight(),
                targetPos.z + target.getWidth() / 2);
        Vec3d aimPoint = getClosestPointOnHitbox(eyePos, interpolatedHitbox);

        // Draw silent aim marker at aim point (red cube)
        double markerSize = 0.08;
        Render3DEngine.setup();
        Render3DEngine.drawFilledBox(event.getMatrixStack(),
                aimPoint.x, aimPoint.y - markerSize, aimPoint.z,
                (float) (markerSize * 2), (float) (markerSize * 2),
                new Color(255, 50, 50, 255));
        Render3DEngine.end();
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == mc.player)
            return false;

        // Crystals use isRemoved() instead of isAlive()
        if (entity instanceof EndCrystalEntity) {
            if (entity.isRemoved())
                return false;
            if (!targetCrystals.getValue())
                return false;
            // Skip line of sight check for crystals - they sit on blocks
            if (mc.player.distanceTo(entity) > range.getValue())
                return false;
            return true;
        }

        // For living entities, check isAlive
        if (!entity.isAlive())
            return false;

        // Check entity type
        if (entity instanceof PlayerEntity player) {
            if (!targetPlayers.getValue())
                return false;
            if (ignoreFriends.getValue() && FriendManager.isFriend(player.getUuid()))
                return false;
        } else if (entity instanceof MobEntity) {
            if (!targetMobs.getValue())
                return false;
        } else if (!(entity instanceof LivingEntity)) {
            return false;
        }

        // Check invisibility
        if (ignoreInvisible.getValue() && entity.isInvisible())
            return false;

        // Check teams
        if (Teams.isTeammate(entity))
            return false;

        // Check line of sight
        if (!mc.player.canSee(entity))
            return false;

        // Check range
        if (mc.player.distanceTo(entity) > range.getValue())
            return false;

        return true;
    }

    /**
     * Uses vector math to find the closest point on the hitbox to the eye position.
     * This is where the "silent crosshair" will be placed.
     */
    private Vec3d getClosestPointOnHitbox(Vec3d eyePos, Box hitbox) {
        // Clamp eye position to hitbox bounds to find closest point
        double x = MathHelper.clamp(eyePos.x, hitbox.minX, hitbox.maxX);
        double y = MathHelper.clamp(eyePos.y, hitbox.minY, hitbox.maxY);
        double z = MathHelper.clamp(eyePos.z, hitbox.minZ, hitbox.maxZ);
        return new Vec3d(x, y, z);
    }

    private float[] calculateRotation(Vec3d target) {
        Vec3d diff = target.subtract(mc.player.getEyePos());
        double distance = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, distance));
        return new float[] { MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -89.0f, 89.0f) };
    }

    private boolean isHoldingWeapon() {
        if (mc.player == null)
            return false;
        Item item = mc.player.getMainHandStack().getItem();
        return item instanceof SwordItem || item instanceof AxeItem;
    }
}

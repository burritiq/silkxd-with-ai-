package cc.silk.module.modules.combat;

import cc.silk.event.impl.player.AttackEvent;
import cc.silk.event.impl.player.TickEvent;
import cc.silk.event.impl.render.Render3DEvent;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.BooleanSetting;
import cc.silk.module.setting.ModeSetting;
import cc.silk.module.setting.NumberSetting;
import cc.silk.module.setting.RangeSetting;
import cc.silk.utils.friend.FriendManager;
import cc.silk.utils.math.MathUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class KnockbackDisplacement extends Module {

    private final ModeSetting direction = new ModeSetting("Direction", "Left", "Left", "Right", "Random");
    private final RangeSetting angle = new RangeSetting("Angle", 15, 90, 30, 60, 5);
    private final NumberSetting chance = new NumberSetting("Chance", 0, 100, 100, 5);
    private final BooleanSetting playersOnly = new BooleanSetting("Players Only", true);
    private final BooleanSetting ignoreFriends = new BooleanSetting("Ignore Friends", true);

    // Cobweb settings
    private final BooleanSetting placeCobweb = new BooleanSetting("Place Cobweb", true);
    private final NumberSetting cobwebDelay = new NumberSetting("Cobweb Delay (ticks)", 1, 20, 3, 1);
    private final NumberSetting maxCobwebDistance = new NumberSetting("Max Distance", 1, 8, 4, 0.5);
    private final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", 1.0, 15.0, 5.0, 0.5);

    private Entity lastHitTarget = null;
    private float lastDisplacedYaw = 0;
    private int ticksSinceHit = 0;
    private boolean pendingCobweb = false;

    private BlockPos targetPlacePos = null;
    private boolean isRotatingToPlace = false;
    private boolean hasPlaced = false;
    private long lastRotationTime = 0;

    public KnockbackDisplacement() {
        super("KB Displacement", "Silently rotates before hitting to send knockback sideways, then places cobweb", -1,
                Category.COMBAT);
        this.addSettings(direction, angle, chance, playersOnly, ignoreFriends, placeCobweb, cobwebDelay,
                maxCobwebDistance, rotationSpeed);
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (isNull())
            return;
        if (event.getTarget() == null)
            return;
        if (!(event.getTarget() instanceof LivingEntity))
            return;

        // Players only check
        if (playersOnly.getValue() && !(event.getTarget() instanceof PlayerEntity))
            return;

        // Friend check
        if (ignoreFriends.getValue() && FriendManager.isFriend(event.getTarget().getUuid()))
            return;

        // Roll KB displacement chance (0-100) - if not triggered, skip
        double roll = Math.random() * 100;
        if (roll > chance.getValue()) {
            return; // Skip KB displacement this hit
        }

        // Get current player yaw
        float currentYaw = mc.player.getYaw();

        // Calculate the displaced yaw (sideways)
        float displacedYaw = calculateDisplacedYaw(currentYaw);
        lastDisplacedYaw = displacedYaw;

        // Send silent rotation packet BEFORE the attack happens
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                displacedYaw,
                mc.player.getPitch(),
                mc.player.isOnGround(),
                mc.player.horizontalCollision));

        // Track target for cobweb placement (only on KB hits)
        if (placeCobweb.getValue()) {
            lastHitTarget = event.getTarget();
            ticksSinceHit = 0;
            pendingCobweb = true;
            isRotatingToPlace = false;
            hasPlaced = false;
            targetPlacePos = null;
        }
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (isNull())
            return;

        if (!pendingCobweb || lastHitTarget == null)
            return;

        ticksSinceHit++;

        // Wait for the configured delay before starting rotation
        if (ticksSinceHit >= cobwebDelay.getValueInt()) {
            // Check if we have cobwebs first
            if (getCobwebSlot() == -1) {
                pendingCobweb = false;
                lastHitTarget = null;
                return;
            }

            // Calculate cobweb position based on displacement direction
            // The target will fly in the direction of the KB (lastDisplacedYaw)
            // Place cobweb: 1 block forward from target + 2 blocks in KB direction

            BlockPos predictedPos = calculateDisplacementLandingPos(lastHitTarget);

            if (predictedPos != null && canPlaceCobweb(predictedPos)) {
                targetPlacePos = predictedPos;
                isRotatingToPlace = true;
                hasPlaced = false;
                lastRotationTime = 0;
            }
            // Fallback: try one block up from predicted
            else if (predictedPos != null && canPlaceCobweb(predictedPos.up())) {
                targetPlacePos = predictedPos.up();
                isRotatingToPlace = true;
                hasPlaced = false;
                lastRotationTime = 0;
            }

            // Reset pending state
            pendingCobweb = false;
            lastHitTarget = null;
        }
    }

    private BlockPos calculateDisplacementLandingPos(Entity target) {
        if (target == null || mc.player == null)
            return null;

        // Get direction from player to target (this is the KB direction)
        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = target.getPos();

        double dx = targetPos.x - playerPos.x;
        double dz = targetPos.z - playerPos.z;
        double length = Math.sqrt(dx * dx + dz * dz);

        if (length < 0.1)
            return null; // Too close

        // Calculate the ACTUAL knockback velocity direction
        // KB is sent at the displaced yaw angle (lastDisplacedYaw)
        // Convert yaw to radians: 0 yaw is +Z, 90 is -X
        float yawRad = (float) Math.toRadians(lastDisplacedYaw);

        // Velocity direction from yaw
        double velX = -Math.sin(yawRad);
        double velZ = Math.cos(yawRad);

        // Minecraft knockback velocity magnitude (approximately 0.4 per hit + weapon
        // kb)
        // Over time, velocity decays by friction (~0.6 per tick on ground, 0.98 in air)
        double kbStrength = 0.4; // Base knockback
        double ticksToLand = cobwebDelay.getValueFloat() + 5; // Extra ticks for flight time

        // Approximate distance traveled (integration of velocity with air friction)
        // v(t) = v0 * 0.98^t, distance = sum ≈ v0 * (1 - 0.98^t) / (1 - 0.98)
        double frictionFactor = 0.98;
        double totalDistance = kbStrength * (1 - Math.pow(frictionFactor, ticksToLand)) / (1 - frictionFactor);

        // Scale distance (blocks per second of simulation)
        totalDistance *= 20; // Convert to blocks (velocity is blocks/tick)

        // Cap the distance to maxCobwebDistance setting
        double maxDist = maxCobwebDistance.getValue();
        if (totalDistance > maxDist) {
            totalDistance = maxDist;
        }

        // Predict landing position
        double landingX = targetPos.x + velX * totalDistance;
        double landingZ = targetPos.z + velZ * totalDistance;

        return new BlockPos(
                (int) Math.floor(landingX),
                (int) Math.floor(targetPos.y),
                (int) Math.floor(landingZ));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (isNull() || !isEnabled())
            return;

        // Handle cobweb placement with smooth rotation
        if (isRotatingToPlace && targetPlacePos != null) {
            handleRotationAndPlace();
        }
    }

    private void handleRotationAndPlace() {
        if (targetPlacePos == null) {
            resetPlacementState();
            return;
        }

        // Delta time for frame-rate independent smooth rotation
        long currentTime = System.currentTimeMillis();
        if (lastRotationTime == 0) {
            lastRotationTime = currentTime;
            return;
        }

        float deltaTime = (currentTime - lastRotationTime) / 1000.0f;
        lastRotationTime = currentTime;

        // Skip if delta is too small or too large
        if (deltaTime < 0.001f || deltaTime > 0.1f)
            return;

        // Calculate rotation to the block placement position
        Direction placeDir = findPlaceDirection(targetPlacePos);
        if (placeDir == null) {
            resetPlacementState();
            return;
        }

        BlockPos placeAgainst = targetPlacePos.offset(placeDir);
        Vec3d hitVec = Vec3d.ofCenter(placeAgainst).add(
                placeDir.getOpposite().getOffsetX() * 0.5,
                placeDir.getOpposite().getOffsetY() * 0.5,
                placeDir.getOpposite().getOffsetZ() * 0.5);

        float[] targetRotation = calculateRotation(hitVec);

        // Apply smooth interpolation (like aim assist)
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float speed = rotationSpeed.getValueFloat();
        float yawDiff = MathHelper.wrapDegrees(targetRotation[0] - currentYaw);
        float pitchDiff = targetRotation[1] - currentPitch;

        // Smooth interpolation based on delta time
        float interpolation = deltaTime * speed * 3.0f;
        float newYaw = currentYaw + yawDiff * Math.min(interpolation, 1.0f);
        float newPitch = MathHelper.clamp(currentPitch + pitchDiff * Math.min(interpolation, 1.0f), -89f, 89f);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);

        // Check if we've reached target
        float remainingYaw = Math.abs(MathHelper.wrapDegrees(targetRotation[0] - newYaw));
        float remainingPitch = Math.abs(targetRotation[1] - newPitch);

        if (remainingYaw < 3 && remainingPitch < 3) {
            if (!hasPlaced) {
                placeCobweb();
                hasPlaced = true;
            }

            // Keep camera in place for combo follow-up (don't snap back)
            resetPlacementState();
        }
    }

    private void placeCobweb() {
        if (targetPlacePos == null)
            return;

        int cobwebSlot = getCobwebSlot();
        if (cobwebSlot == -1)
            return;

        // Find a direction to place against
        Direction placeDir = findPlaceDirection(targetPlacePos);
        if (placeDir == null)
            return;

        int originalSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = cobwebSlot;

        // Create proper BlockHitResult for placement
        BlockPos placeAgainst = targetPlacePos.offset(placeDir);
        Vec3d hitVec = Vec3d.ofCenter(placeAgainst).add(
                placeDir.getOpposite().getOffsetX() * 0.5,
                placeDir.getOpposite().getOffsetY() * 0.5,
                placeDir.getOpposite().getOffsetZ() * 0.5);

        BlockHitResult hitResult = new BlockHitResult(
                hitVec,
                placeDir.getOpposite(),
                placeAgainst,
                false);

        // Place block using interactBlock
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);

        // Restore original slot
        mc.player.getInventory().selectedSlot = originalSlot;
    }

    private Direction findPlaceDirection(BlockPos pos) {
        // Check all directions for a solid block to place against
        for (Direction dir : Direction.values()) {
            BlockPos adjacent = pos.offset(dir);
            if (!mc.world.getBlockState(adjacent).isAir()) {
                return dir;
            }
        }
        return null;
    }

    private void resetPlacementState() {
        isRotatingToPlace = false;
        targetPlacePos = null;
        hasPlaced = false;
    }

    private float[] calculateRotation(Vec3d target) {
        Vec3d diff = target.subtract(mc.player.getEyePos());
        double distance = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, distance));
        return new float[] { MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -89.0f, 89.0f) };
    }

    private float calculateDisplacedYaw(float currentYaw) {
        // Randomize angle within configured range (like TriggerBot's reaction time)
        float angleValue = (float) MathUtils.randomDoubleBetween(angle.getMinValue(), angle.getMaxValue());

        return switch (direction.getMode()) {
            case "Left" -> currentYaw - angleValue;
            case "Right" -> currentYaw + angleValue;
            case "Random" -> {
                if (Math.random() > 0.5) {
                    yield currentYaw + angleValue;
                } else {
                    yield currentYaw - angleValue;
                }
            }
            default -> currentYaw;
        };
    }

    private boolean canPlaceCobweb(BlockPos pos) {
        if (mc.world == null || mc.player == null)
            return false;
        if (!mc.world.getBlockState(pos).isAir())
            return false;

        double distance = mc.player.getPos().distanceTo(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        if (distance > 4.5)
            return false;

        // Check if there's an adjacent block to place against
        return findPlaceDirection(pos) != null;
    }

    private int getCobwebSlot() {
        // Only check hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.COBWEB) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onEnable() {
        lastHitTarget = null;
        pendingCobweb = false;
        ticksSinceHit = 0;
        resetPlacementState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        lastHitTarget = null;
        pendingCobweb = false;
        ticksSinceHit = 0;
        resetPlacementState();
        super.onDisable();
    }
}

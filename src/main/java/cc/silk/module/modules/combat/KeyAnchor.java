package cc.silk.module.modules.combat;

import cc.silk.event.impl.input.HandleInputEvent;
import cc.silk.event.impl.player.TickEvent;
import cc.silk.event.impl.render.Render3DEvent;
import cc.silk.mixin.MinecraftClientAccessor;
import cc.silk.SilkClient;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.KeybindSetting;
import cc.silk.module.setting.NumberSetting;
import cc.silk.utils.keybinding.KeyUtils;
import cc.silk.utils.math.TimerUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class KeyAnchor extends Module {

    private final KeybindSetting anchorKeybind = new KeybindSetting("Anchor Key", GLFW.GLFW_KEY_X, false);
    private final NumberSetting delay = new NumberSetting("Delay (MS)", 1, 500, 50, 1);
    private final NumberSetting restoreDelayTicks = new NumberSetting("Restore Delay", 1, 20, 2, 1);
    private final NumberSetting safeAnchor = new NumberSetting("Safe Anchor", 0, 1, 1, 1);
    private final NumberSetting safeAnchorHealth = new NumberSetting("Safe Anchor Health", 0, 20, 10, 0.5);
    private final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", 1.0, 15.0, 5.0, 0.5);

    private final TimerUtil timer = new TimerUtil();
    private boolean keyPressed = false;
    private boolean isActive = false;
    private int originalSlot = -1;
    private boolean hasPlacedThisCycle = false;
    private boolean pendingRestoreSlot = false;
    private int pendingRestoreTicksLeft = 0;
    private boolean waitingForBlockPlace = false;
    private boolean safeBlockPlaced = false;
    private int anchorsPlacedCount = 0;

    // Smooth rotation state machine for safe anchor
    private enum RotationState {
        IDLE, ROTATING_TO_GLOWSTONE, PLACING_GLOWSTONE, ROTATING_TO_ANCHOR
    }

    private RotationState rotationState = RotationState.IDLE;
    private BlockPos pendingGlowstonePos = null;
    private BlockPos pendingAnchorPos = null;
    private float targetYaw = 0;
    private float targetPitch = 0;
    private long lastRotationTime = 0;

    public KeyAnchor() {
        super("Key Anchor", "Automatically places and explodes respawn anchors for PvP", -1, Category.COMBAT);
        this.addSettings(anchorKeybind, delay, restoreDelayTicks, safeAnchor, safeAnchorHealth, rotationSpeed);
        this.getSettings().removeIf(setting -> setting instanceof KeybindSetting && !setting.equals(anchorKeybind));
    }

    // Public static method for mixin to check if airplace should work
    public static boolean shouldAllowAirplace() {
        // Double anchor feature removed - no airplace needed
        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (isNull() || !isEnabled())
            return;

        // Handle smooth rotation state machine (per-frame for smooth 60+ FPS)
        if (rotationState != RotationState.IDLE) {
            handleSmoothRotation();
        }
    }

    private void handleSmoothRotation() {
        if (mc.player == null)
            return;

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

        float speed = rotationSpeed.getValueFloat();
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        // Smooth interpolation based on delta time (like aim assist)
        float interpolation = deltaTime * speed * 3.0f;
        float newYaw = currentYaw + yawDiff * Math.min(interpolation, 1.0f);
        float newPitch = MathHelper.clamp(currentPitch + pitchDiff * Math.min(interpolation, 1.0f), -89f, 89f);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);

        // Check if we've reached target
        float remainingYaw = Math.abs(MathHelper.wrapDegrees(targetYaw - newYaw));
        float remainingPitch = Math.abs(targetPitch - newPitch);

        if (remainingYaw < 3 && remainingPitch < 3) {
            switch (rotationState) {
                case ROTATING_TO_GLOWSTONE -> {
                    // Now place the glowstone
                    rotationState = RotationState.PLACING_GLOWSTONE;
                    doPlaceGlowstone();
                    // Start rotating to anchor (target the TOP of the anchor)
                    if (pendingAnchorPos != null) {
                        Vec3d anchorTop = new Vec3d(
                                pendingAnchorPos.getX() + 0.5,
                                pendingAnchorPos.getY() + 1.0,
                                pendingAnchorPos.getZ() + 0.5);
                        float[] anchorRotation = calculateRotationToBlock(anchorTop);
                        targetYaw = anchorRotation[0];
                        targetPitch = anchorRotation[1];
                        rotationState = RotationState.ROTATING_TO_ANCHOR;
                    } else {
                        resetRotationState();
                    }
                }
                case ROTATING_TO_ANCHOR -> {
                    // Done - ready to interact with anchor
                    resetRotationState();
                }
                default -> resetRotationState();
            }
        }
    }

    private void resetRotationState() {
        rotationState = RotationState.IDLE;
        pendingGlowstonePos = null;
        pendingAnchorPos = null;
    }

    @EventHandler
    private void onTickEvent(HandleInputEvent event) {
        if (isNull() || !isEnabled())
            return;
        if (mc.currentScreen != null)
            return;

        boolean currentKeyState = KeyUtils.isKeyPressed(anchorKeybind.getKeyCode());

        if (currentKeyState && !keyPressed) {
            startAnchorPvP();
        } else if (!currentKeyState && keyPressed) {
            stopAnchorPvP();
        } else if (!currentKeyState) {
            hasPlacedThisCycle = false;
        }

        keyPressed = currentKeyState;

        // Normal anchor PvP logic
        boolean shouldProcess = false;
        if (isActive && timer.hasElapsedTime(delay.getValueInt())) {
            shouldProcess = true;
        }

        if (shouldProcess) {
            processAnchorPvP();
            timer.reset();
        }

        if (pendingRestoreSlot) {
            if (pendingRestoreTicksLeft <= 0) {
                restoreOriginalSlot();
                pendingRestoreSlot = false;
            } else {
                pendingRestoreTicksLeft--;
            }
        }
    }

    private void startAnchorPvP() {
        if (isActive)
            return;

        isActive = true;
        originalSlot = mc.player.getInventory().selectedSlot;
        hasPlacedThisCycle = false;
        safeBlockPlaced = false;
        anchorsPlacedCount = 0;

        timer.reset();
    }

    private void stopAnchorPvP() {
        if (!isActive)
            return;

        if (originalSlot != -1) {
            mc.player.getInventory().selectedSlot = originalSlot;
        }
        isActive = false;
        originalSlot = -1;
        pendingRestoreSlot = false;
        pendingRestoreTicksLeft = 0;
        safeBlockPlaced = false;
        anchorsPlacedCount = 0;
        resetRotationState();
    }

    private void doUse() {
        if (mc.interactionManager == null)
            return;
        ((MinecraftClientAccessor) mc).invokeDoItemUse();
    }

    private boolean hasRequiredItems() {
        boolean hasAnchor = false;
        boolean hasGlowstone = false;

        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty())
                continue;

            if (stack.getItem() == Items.RESPAWN_ANCHOR)
                hasAnchor = true;
            if (stack.getItem() == Items.GLOWSTONE)
                hasGlowstone = true;
        }

        return hasAnchor && hasGlowstone;
    }

    private void processAnchorPvP() {
        if (!(mc.crosshairTarget instanceof BlockHitResult blockHit))
            return;

        BlockPos targetBlock = blockHit.getBlockPos();
        var blockState = mc.world.getBlockState(targetBlock);

        if (blockState.isAir())
            return;

        if (blockState.getBlock() == Blocks.RESPAWN_ANCHOR) {
            int charges = blockState.get(RespawnAnchorBlock.CHARGES);

            // If anchor has charges and we need safe anchor
            if (charges > 0) {
                // Check if we need to place a glowstone block first (reduces explosion damage)
                if (shouldUseSafeAnchor() && !safeBlockPlaced && !waitingForBlockPlace) {
                    BlockPos safeBlockPos = getSafeBlockPosition(targetBlock);
                    if (safeBlockPos != null && mc.world.getBlockState(safeBlockPos).isAir()) {
                        // Place glowstone and auto-rotate to anchor
                        if (placeGlowstoneSilently(safeBlockPos, targetBlock)) {
                            waitingForBlockPlace = true;
                            safeBlockPlaced = true;
                            hasPlacedThisCycle = true;
                            return; // Wait for next cycle to explode
                        }
                    } else if (safeBlockPos != null && !mc.world.getBlockState(safeBlockPos).isAir()) {
                        // Block already exists, mark as placed
                        safeBlockPlaced = true;
                    }
                }

                // Explode the anchor - auto-detect totem
                waitingForBlockPlace = false;

                if (swapToTotem()) {
                    ((MinecraftClientAccessor) mc).invokeDoItemUse();
                    hasPlacedThisCycle = true;
                    safeBlockPlaced = false;

                    scheduleRestoreOriginalSlot();
                }
            } else {
                // Anchor has no charges - check if we need safe block BEFORE charging
                if (shouldUseSafeAnchor() && !safeBlockPlaced && !waitingForBlockPlace) {
                    BlockPos safeBlockPos = getSafeBlockPosition(targetBlock);
                    // Only try to place if the position is AIR (not already placed)
                    if (safeBlockPos != null && mc.world.getBlockState(safeBlockPos).isAir()) {
                        // Place glowstone and auto-rotate to anchor
                        if (placeGlowstoneSilently(safeBlockPos, targetBlock)) {
                            waitingForBlockPlace = true;
                            safeBlockPlaced = true;
                            hasPlacedThisCycle = true;
                            return;
                        }
                    } else if (safeBlockPos != null && !mc.world.getBlockState(safeBlockPos).isAir()) {
                        // Block already exists, mark as placed
                        safeBlockPlaced = true;
                    }
                }

                // Now charge the anchor with glowstone
                waitingForBlockPlace = false;
                if (swapToItem(Items.GLOWSTONE)) {
                    ((MinecraftClientAccessor) mc).invokeDoItemUse();
                    hasPlacedThisCycle = true;
                }
            }
            return;
        }

        waitingForBlockPlace = false;
        safeBlockPlaced = false; // Reset when looking at non-anchor blocks
        BlockPos placementPos = targetBlock.offset(blockHit.getSide());

        // Normal placement (requires a block to place against)
        if (isValidAnchorPosition(placementPos) && !hasPlacedThisCycle) {
            if (swapToItem(Items.RESPAWN_ANCHOR)) {
                hasPlacedThisCycle = true;
                ((MinecraftClientAccessor) mc).invokeDoItemUse();
            }
        }
    }

    private boolean shouldUseSafeAnchor() {
        if (safeAnchor.getValueInt() == 0)
            return false;
        if (mc.player == null)
            return false;

        float currentHealth = mc.player.getHealth();
        return currentHealth <= safeAnchorHealth.getValueFloat();
    }

    private BlockPos getSafeBlockPosition(BlockPos anchorPos) {
        if (mc.player == null || mc.world == null)
            return null;

        // Calculate direction from anchor to player (horizontal only)
        Vec3d anchorCenter = Vec3d.ofCenter(anchorPos);
        Vec3d playerPos = mc.player.getPos();

        double deltaX = playerPos.x - anchorCenter.x;
        double deltaZ = playerPos.z - anchorCenter.z;

        // Determine which horizontal direction has the largest delta
        BlockPos safePos = null;

        if (Math.abs(deltaX) > Math.abs(deltaZ)) {
            // X direction is dominant
            if (deltaX > 0) {
                // Player is east of anchor, place block on east side
                safePos = anchorPos.east();
            } else {
                // Player is west of anchor, place block on west side
                safePos = anchorPos.west();
            }
        } else {
            // Z direction is dominant
            if (deltaZ > 0) {
                // Player is south of anchor, place block on south side
                safePos = anchorPos.south();
            } else {
                // Player is north of anchor, place block on north side
                safePos = anchorPos.north();
            }
        }

        // Make sure it's a valid position
        if (safePos != null && mc.player.getPos().distanceTo(Vec3d.ofCenter(safePos)) <= 4.5) {
            return safePos;
        }

        return null;
    }

    private boolean placeGlowstoneSilently(BlockPos glowstonePos, BlockPos anchorPos) {
        if (mc.player == null || mc.interactionManager == null)
            return false;

        // Find glowstone slot
        int glowstoneSlot = -1;
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.GLOWSTONE) {
                glowstoneSlot = i;
                break;
            }
        }

        if (glowstoneSlot == -1)
            return false;

        // Check if there's a block to place against
        Direction placeDirection = findPlaceableDirection(glowstonePos);
        if (placeDirection == null)
            return false;

        // Start smooth rotation state machine (aim assist-like)
        pendingGlowstonePos = glowstonePos;
        pendingAnchorPos = anchorPos;

        // Calculate rotation to the adjacent block face we're placing against
        BlockPos placeAgainst = glowstonePos.offset(placeDirection);
        Vec3d hitVec = Vec3d.ofCenter(placeAgainst).add(
                placeDirection.getOpposite().getOffsetX() * 0.5,
                placeDirection.getOpposite().getOffsetY() * 0.5,
                placeDirection.getOpposite().getOffsetZ() * 0.5);

        float[] targetRotation = calculateRotationToBlock(hitVec);
        targetYaw = targetRotation[0];
        targetPitch = targetRotation[1];

        // Start rotating towards glowstone placement position
        rotationState = RotationState.ROTATING_TO_GLOWSTONE;

        return true;
    }

    private void doPlaceGlowstone() {
        if (pendingGlowstonePos == null || mc.player == null || mc.interactionManager == null)
            return;

        // Find glowstone slot
        int glowstoneSlot = -1;
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.GLOWSTONE) {
                glowstoneSlot = i;
                break;
            }
        }

        if (glowstoneSlot == -1)
            return;

        // Check if there's a block to place against
        Direction placeDirection = findPlaceableDirection(pendingGlowstonePos);
        if (placeDirection == null)
            return;

        // Save original slot
        int originalSelectedSlot = mc.player.getInventory().selectedSlot;

        // Switch to glowstone
        mc.player.getInventory().selectedSlot = glowstoneSlot;

        // Create proper BlockHitResult and use interactBlock
        BlockPos placeAgainst = pendingGlowstonePos.offset(placeDirection);
        Vec3d hitVec = Vec3d.ofCenter(placeAgainst).add(
                placeDirection.getOpposite().getOffsetX() * 0.5,
                placeDirection.getOpposite().getOffsetY() * 0.5,
                placeDirection.getOpposite().getOffsetZ() * 0.5);

        BlockHitResult hitResult = new BlockHitResult(
                hitVec,
                placeDirection.getOpposite(),
                placeAgainst,
                false);

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);

        // Restore original slot
        mc.player.getInventory().selectedSlot = originalSelectedSlot;
    }

    private float[] calculateRotationToBlock(Vec3d target) {
        Vec3d diff = target.subtract(mc.player.getEyePos());
        double distance = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, distance));
        return new float[] { MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -89.0f, 89.0f) };
    }

    private Direction findPlaceableDirection(BlockPos pos) {
        // Check all directions for a solid block to place against
        for (Direction dir : Direction.values()) {
            BlockPos adjacent = pos.offset(dir);
            if (!mc.world.getBlockState(adjacent).isAir()) {
                return dir;
            }
        }
        return null;
    }

    private boolean isValidAnchorPosition(BlockPos pos) {
        if (mc.world == null || mc.player == null)
            return false;
        if (mc.player.getPos().distanceTo(Vec3d.ofCenter(pos)) > 4.5)
            return false;
        if (!mc.world.getBlockState(pos).isAir())
            return false;

        BlockPos playerPos = mc.player.getBlockPos();
        return !pos.equals(playerPos) && !pos.equals(playerPos.up());
    }

    private boolean swapToItem(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                mc.player.getInventory().selectedSlot = i;
                return true;
            }
        }
        return false;
    }

    private boolean swapToTotem() {
        if (mc.player == null)
            return false;

        // Auto-detect totem in hotbar
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING) {
                mc.player.getInventory().selectedSlot = i;
                return true;
            }
        }

        // Fallback to sword if no totem found
        return swapToSword();
    }

    private boolean swapToSword() {
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof SwordItem) {
                mc.player.getInventory().selectedSlot = i;
                return true;
            }
        }
        return false;
    }

    private void restoreOriginalSlot() {
        if (originalSlot != -1) {
            mc.player.getInventory().selectedSlot = originalSlot;
        }
    }

    private void scheduleRestoreOriginalSlot() {
        if (originalSlot != -1) {
            pendingRestoreSlot = true;
            pendingRestoreTicksLeft = restoreDelayTicks.getValueInt();
        }
    }

    @Override
    public void onEnable() {
        keyPressed = false;
        isActive = false;
        originalSlot = -1;
        hasPlacedThisCycle = false;
        pendingRestoreSlot = false;
        pendingRestoreTicksLeft = 0;
        waitingForBlockPlace = false;
        safeBlockPlaced = false;
        anchorsPlacedCount = 0;
        timer.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        stopAnchorPvP();
        super.onDisable();
    }

    @Override
    public int getKey() {
        return -1;
    }
}
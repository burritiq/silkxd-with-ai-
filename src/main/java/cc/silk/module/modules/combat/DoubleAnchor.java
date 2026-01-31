package cc.silk.module.modules.combat;

import cc.silk.SilkClient;
import cc.silk.event.impl.input.HandleInputEvent;
import cc.silk.mixin.MinecraftClientAccessor;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.KeybindSetting;
import cc.silk.module.setting.NumberSetting;
import cc.silk.utils.keybinding.KeyUtils;
import cc.silk.utils.math.TimerUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public final class DoubleAnchor extends Module {

    private final KeybindSetting anchorKeybind = new KeybindSetting("Double Anchor Key", GLFW.GLFW_KEY_V, false);
    private final NumberSetting delay = new NumberSetting("Delay (MS)", 1, 500, 50, 1);
    private final NumberSetting restoreDelayTicks = new NumberSetting("Restore Delay", 1, 20, 2, 1);

    private final TimerUtil timer = new TimerUtil();
    private boolean keyPressed = false;
    private int originalSlot = -1;
    private boolean pendingRestoreSlot = false;
    private int pendingRestoreTicksLeft = 0;

    // Double anchor step-based sequence variables
    private int doubleAnchorStep = 0;
    private boolean isDoubleAnchoring = false;
    private int anchorsPlacedInSequence = 0;

    public DoubleAnchor() {
        super("Double Anchor", "Places and explodes two respawn anchors in quick succession", -1, Category.COMBAT);
        this.addSettings(anchorKeybind, delay, restoreDelayTicks);
        this.getSettings().removeIf(setting -> setting instanceof KeybindSetting && !setting.equals(anchorKeybind));
    }

    // Public static method for mixin to check if airplace should work
    public static boolean shouldAllowAirplace() {
        if (SilkClient.INSTANCE == null)
            return false;

        var doubleAnchorOpt = SilkClient.INSTANCE.getModuleManager().getModule(DoubleAnchor.class);
        if (doubleAnchorOpt.isEmpty())
            return false;

        DoubleAnchor doubleAnchor = doubleAnchorOpt.get();
        if (!doubleAnchor.isEnabled())
            return false;

        // Allow airplace during anchor placement steps (step 1 and step 5)
        return doubleAnchor.isDoubleAnchoring
                && (doubleAnchor.doubleAnchorStep == 1 || doubleAnchor.doubleAnchorStep == 5);
    }

    @EventHandler
    private void onInput(HandleInputEvent event) {
        if (isNull() || !isEnabled())
            return;
        if (mc.currentScreen != null)
            return;

        boolean currentKeyState = KeyUtils.isKeyPressed(anchorKeybind.getKeyCode());

        if (currentKeyState && !keyPressed) {
            startDoubleAnchor();
        } else if (!currentKeyState && keyPressed) {
            stopDoubleAnchor();
        }

        keyPressed = currentKeyState;

        // Process the double anchor sequence while key is held
        if (isDoubleAnchoring && timer.hasElapsedTime(delay.getValueInt())) {
            processDoubleAnchorSequence();
            timer.reset();
        }

        // Handle pending slot restore
        if (pendingRestoreSlot) {
            if (pendingRestoreTicksLeft <= 0) {
                restoreOriginalSlot();
                pendingRestoreSlot = false;
            } else {
                pendingRestoreTicksLeft--;
            }
        }
    }

    private void startDoubleAnchor() {
        if (isDoubleAnchoring)
            return;

        isDoubleAnchoring = true;
        originalSlot = mc.player.getInventory().selectedSlot;
        doubleAnchorStep = 0;
        anchorsPlacedInSequence = 0;
        timer.reset();
    }

    private void stopDoubleAnchor() {
        if (originalSlot != -1 && mc.player != null) {
            mc.player.getInventory().selectedSlot = originalSlot;
        }
        isDoubleAnchoring = false;
        originalSlot = -1;
        doubleAnchorStep = 0;
        anchorsPlacedInSequence = 0;
        pendingRestoreSlot = false;
        pendingRestoreTicksLeft = 0;
    }

    private void processDoubleAnchorSequence() {
        // Stop if we've already placed 2 anchors
        if (anchorsPlacedInSequence >= 2) {
            resetDoubleAnchorSequence();
            return;
        }

        if (!(mc.crosshairTarget instanceof BlockHitResult blockHit)) {
            resetDoubleAnchorSequence();
            return;
        }

        BlockPos targetBlock = blockHit.getBlockPos();
        var blockState = mc.world.getBlockState(targetBlock);

        // Check if we're looking at air when we shouldn't be (except for placement
        // steps)
        if (blockState.isAir() && doubleAnchorStep != 1 && doubleAnchorStep != 5) {
            resetDoubleAnchorSequence();
            return;
        }

        // Check for required items
        if (!hasRequiredItems()) {
            resetDoubleAnchorSequence();
            return;
        }

        // Store original slot on first step
        if (originalSlot == -1) {
            originalSlot = mc.player.getInventory().selectedSlot;
        }

        // Step machine (double anchor sequence)
        // Step 0: Switch to anchor
        // Step 1: Place first anchor
        // Step 2: Switch to glowstone
        // Step 3: Charge first anchor
        // Step 4: Switch to anchor again
        // Step 5: Place second anchor + explode first anchor
        // Step 6: Switch to glowstone
        // Step 7: Charge second anchor
        // Step 8: Switch to totem/sword
        // Step 9: Explode second anchor
        // Step 10: Done

        if (doubleAnchorStep == 0) {
            if (!swapToItem(Items.RESPAWN_ANCHOR)) {
                resetDoubleAnchorSequence();
                return;
            }
        } else if (doubleAnchorStep == 1) {
            doUse();
        } else if (doubleAnchorStep == 2) {
            if (!swapToItem(Items.GLOWSTONE)) {
                resetDoubleAnchorSequence();
                return;
            }
        } else if (doubleAnchorStep == 3) {
            doUse();
            anchorsPlacedInSequence++; // First anchor cycle complete
        } else if (doubleAnchorStep == 4) {
            if (!swapToItem(Items.RESPAWN_ANCHOR)) {
                resetDoubleAnchorSequence();
                return;
            }
        } else if (doubleAnchorStep == 5) {
            doUse(); // Place second anchor
            doUse(); // Explode first anchor
        } else if (doubleAnchorStep == 6) {
            if (!swapToItem(Items.GLOWSTONE)) {
                resetDoubleAnchorSequence();
                return;
            }
        } else if (doubleAnchorStep == 7) {
            doUse(); // Charge second anchor
        } else if (doubleAnchorStep == 8) {
            // Switch to totem for safety
            if (!swapToTotem()) {
                resetDoubleAnchorSequence();
                return;
            }
        } else if (doubleAnchorStep == 9) {
            doUse(); // Explode second anchor
            anchorsPlacedInSequence++; // Second anchor exploded
            scheduleRestoreOriginalSlot();
        } else if (doubleAnchorStep == 10) {
            resetDoubleAnchorSequence();
            return;
        }

        doubleAnchorStep++;
    }

    private void resetDoubleAnchorSequence() {
        isDoubleAnchoring = false;
        doubleAnchorStep = 0;
        if (originalSlot != -1 && mc.player != null) {
            mc.player.getInventory().selectedSlot = originalSlot;
        }
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
        if (originalSlot != -1 && mc.player != null) {
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
        isDoubleAnchoring = false;
        originalSlot = -1;
        doubleAnchorStep = 0;
        anchorsPlacedInSequence = 0;
        pendingRestoreSlot = false;
        pendingRestoreTicksLeft = 0;
        timer.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        stopDoubleAnchor();
        super.onDisable();
    }

    @Override
    public int getKey() {
        return -1;
    }
}

package cc.silk.module.modules.combat;

import net.minecraft.entity.Entity;

/**
 * Helper class to share silent aim target between the module and mixin.
 */
public class SilentAimHelper {
    private static Entity silentTarget = null;

    public static void setSilentTarget(Entity target) {
        silentTarget = target;
    }

    public static Entity getSilentTarget() {
        return silentTarget;
    }

    public static boolean hasTarget() {
        return silentTarget != null;
    }

    public static void clear() {
        silentTarget = null;
    }
}

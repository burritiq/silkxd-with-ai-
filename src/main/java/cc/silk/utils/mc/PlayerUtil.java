package cc.silk.utils.mc;

import cc.silk.utils.IMinecraft;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

@UtilityClass
@Getter
public final class PlayerUtil implements IMinecraft {
    public static boolean isLookingAt(BlockPos pos, double maxDistance) {
        if (mc.player == null || mc.world == null) return false;

        Vec3d eyePos = mc.player.getCameraPosVec(1.0f);
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        Vec3d reachVec = eyePos.add(lookVec.multiply(maxDistance));

        BlockHitResult result = mc.world.raycast(new net.minecraft.world.RaycastContext(
                eyePos,
                reachVec,
                net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        return result != null && result.getBlockPos().equals(pos);
    }
}

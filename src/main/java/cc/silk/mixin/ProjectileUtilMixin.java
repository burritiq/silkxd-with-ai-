package cc.silk.mixin;

import cc.silk.module.modules.combat.HitboxHelper;
import cc.silk.module.modules.combat.SilentAimHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {

    @Inject(method = "raycast(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/EntityHitResult;", at = @At("HEAD"))
    private static void onRaycastStart(Entity entity, Vec3d min, Vec3d max, Box box, Predicate<Entity> predicate,
            double d, CallbackInfoReturnable<EntityHitResult> cir) {
        HitboxHelper.setRaycasting(true);
    }

    @Inject(method = "raycast(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/EntityHitResult;", at = @At("RETURN"), cancellable = true)
    private static void onRaycastEnd(Entity entity, Vec3d min, Vec3d max, Box box, Predicate<Entity> predicate,
            double d, CallbackInfoReturnable<EntityHitResult> cir) {
        HitboxHelper.setRaycasting(false);

        // Silent Aim: if we have a target and this is the player's raycast, return our
        // target
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && entity == mc.player && SilentAimHelper.hasTarget()) {
            Entity silentTarget = SilentAimHelper.getSilentTarget();
            if (silentTarget != null && silentTarget.isAlive()) {
                // Create hit result pointing at the silent aim target's center
                Vec3d hitPos = silentTarget.getPos().add(0, silentTarget.getHeight() / 2.0, 0);
                cir.setReturnValue(new EntityHitResult(silentTarget, hitPos));
            }
        }
    }
}

package cc.silk.mixin;

import cc.silk.module.modules.render.CustomOutlineESP;
import cc.silk.utils.render.OutlinePostProcessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.*;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow private Framebuffer entityOutlineFramebuffer;

    @Inject(method = "drawEntityOutlinesFramebuffer", at = @At("HEAD"), cancellable = true)
    private void onDrawEntityOutlines(CallbackInfo ci) {
        CustomOutlineESP outlineESP = CustomOutlineESP.getInstance();
        if (outlineESP == null || !outlineESP.isEnabled()) return;
        if (entityOutlineFramebuffer == null) return;

        outlineESP.updateTargets();

        Color color = outlineESP.getTargetEntities().stream()
                .findFirst()
                .map(outlineESP::getColorForEntity)
                .orElse(new Color(255, 50, 50));

        OutlinePostProcessor.process(
                entityOutlineFramebuffer,
                color,
                outlineESP.getOutlineWidth(),
                outlineESP.getIntensity(),
                outlineESP.shouldShowFill(),
                outlineESP.getFillAlpha()
        );

        ci.cancel();
    }
}
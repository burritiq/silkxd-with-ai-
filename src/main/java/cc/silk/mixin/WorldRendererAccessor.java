package cc.silk.mixin;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WorldRenderer.class)
public interface WorldRendererAccessor {
    @Accessor("frustum")
    Frustum getFrustum();

    @Accessor("entityOutlineFramebuffer")
    Framebuffer getEntityOutlineFramebuffer();

    @Invoker("canDrawEntityOutlines")
    boolean invokeCanDrawEntityOutlines();
}

package cc.silk.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.awt.*;

public class OutlinePostProcessor {
    private static final String SHADER_NAME = "entity_outline";
    private static int vao = -1;
    private static int vbo = -1;
    private static boolean initialized = false;
    private static SimpleFramebuffer outputBuffer = null;

    private static final float[] QUAD_VERTICES = {
            0.0f, 0.0f,
            1.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f
    };

    public static void init() {
        if (initialized) return;

        int shaderProgram = ShaderManager.loadShaderProgram(
                SHADER_NAME,
                "shaders/post/entity_outline.vsh",
                "shaders/post/entity_outline.fsh"
        );

        if (shaderProgram == 0) {
            System.err.println("[Silk] Failed to load entity outline shader");
            return;
        }

        vao = GL30.glGenVertexArrays();
        vbo = GL20.glGenBuffers();

        GL30.glBindVertexArray(vao);
        GL20.glBindBuffer(GL20.GL_ARRAY_BUFFER, vbo);
        GL20.glBufferData(GL20.GL_ARRAY_BUFFER, QUAD_VERTICES, GL20.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 2 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);
        GL20.glBindBuffer(GL20.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        initialized = true;
    }


    public static void process(Framebuffer outlineBuffer, Color color, float width, float intensity) {
        process(outlineBuffer, color, width, intensity, false, 0.2f);
    }

    public static void process(Framebuffer outlineBuffer, Color color, float width, float intensity, boolean showFill, float fillAlpha) {
        if (!initialized) init();
        if (!initialized || outlineBuffer == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int screenWidth = mc.getWindow().getFramebufferWidth();
        int screenHeight = mc.getWindow().getFramebufferHeight();

        Integer shaderProgram = ShaderManager.getShaderProgram(SHADER_NAME);
        if (shaderProgram == null || shaderProgram == 0) return;

        if (outputBuffer == null || outputBuffer.textureWidth != screenWidth || outputBuffer.textureHeight != screenHeight) {
            if (outputBuffer != null) outputBuffer.delete();
            outputBuffer = new SimpleFramebuffer(screenWidth, screenHeight, false);
        }

        int[] prevVAO = new int[1];
        int[] prevProgram = new int[1];
        int[] prevFBO = new int[1];
        GL11.glGetIntegerv(GL30.GL_VERTEX_ARRAY_BINDING, prevVAO);
        GL11.glGetIntegerv(GL20.GL_CURRENT_PROGRAM, prevProgram);
        GL11.glGetIntegerv(GL30.GL_FRAMEBUFFER_BINDING, prevFBO);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);

        try {
            outputBuffer.clear();
            outputBuffer.beginWrite(false);

            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            GL30.glBindVertexArray(vao);
            ShaderManager.useShader(SHADER_NAME);

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, outlineBuffer.getColorAttachment());

            ShaderManager.setUniform1i(SHADER_NAME, "DiffuseSampler", 0);
            ShaderManager.setUniform2f(SHADER_NAME, "ScreenSize", screenWidth, screenHeight);
            ShaderManager.setUniform4f(SHADER_NAME, "OutlineColor",
                    color.getRed() / 255f,
                    color.getGreen() / 255f,
                    color.getBlue() / 255f,
                    color.getAlpha() / 255f);
            ShaderManager.setUniform1f(SHADER_NAME, "OutlineWidth", width);
            ShaderManager.setUniform1f(SHADER_NAME, "Intensity", intensity);
            ShaderManager.setUniform1f(SHADER_NAME, "ShowFill", showFill ? 1.0f : 0.0f);
            ShaderManager.setUniform1f(SHADER_NAME, "FillAlpha", fillAlpha);

            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

            outputBuffer.endWrite();

            mc.getFramebuffer().beginWrite(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA
            );

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, outputBuffer.getColorAttachment());

            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        } finally {
            GL30.glBindVertexArray(prevVAO[0]);
            GL20.glUseProgram(prevProgram[0]);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFBO[0]);

            if (depthEnabled) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();

            if (!blendEnabled) RenderSystem.disableBlend();
            else RenderSystem.defaultBlendFunc();
        }
    }

    public static void cleanup() {
        if (vao != -1) {
            GL30.glDeleteVertexArrays(vao);
            vao = -1;
        }
        if (vbo != -1) {
            GL20.glDeleteBuffers(vbo);
            vbo = -1;
        }
        if (outputBuffer != null) {
            outputBuffer.delete();
            outputBuffer = null;
        }
        initialized = false;
    }
}

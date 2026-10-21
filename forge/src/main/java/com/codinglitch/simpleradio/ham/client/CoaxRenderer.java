package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.ham.block.CoaxLink;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;

/**
 * Draws the coaxial cable from a station or repeater up to its antenna as a slightly sagging black line.
 */
public class CoaxRenderer<T extends BlockEntity & CoaxLink.Endpoint> implements BlockEntityRenderer<T> {
    private static final int SEGMENTS = 24;
    private static final float THICKNESS = 0.035f;

    public CoaxRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(T blockEntity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockPos antenna = blockEntity.getCoax().getAntenna();
        if (antenna == null || blockEntity.getLevel() == null) return;

        BlockPos origin = blockEntity.getBlockPos();
        float dx = antenna.getX() - origin.getX();
        float dy = antenna.getY() - origin.getY() + 0.05f - 1.0f;
        float dz = antenna.getZ() - origin.getZ();

        int endLight = LevelRenderer.getLightColor(blockEntity.getLevel(), antenna);
        int startLight = LevelRenderer.getLightColor(blockEntity.getLevel(), origin.above());

        pose.pushPose();
        pose.translate(0.5, 1.0, 0.5); // leaves the top of the station
        Matrix4f matrix = pose.last().pose();
        VertexConsumer consumer = buffers.getBuffer(RenderType.leash());

        float horizontal = Mth.sqrt(dx * dx + dz * dz);
        float sag = Math.min(1.5f, 0.06f * Mth.sqrt(dx * dx + dy * dy + dz * dz));
        float offsetX, offsetZ;
        if (horizontal < 0.01f) {
            offsetX = THICKNESS / 2;
            offsetZ = 0;
        } else {
            offsetX = dz / horizontal * THICKNESS / 2;
            offsetZ = dx / horizontal * THICKNESS / 2;
        }

        for (int i = 0; i <= SEGMENTS; i++) vertexPair(consumer, matrix, dx, dy, dz, sag, startLight, endLight, THICKNESS, THICKNESS, offsetX, offsetZ, i, false);
        for (int i = SEGMENTS; i >= 0; i--) vertexPair(consumer, matrix, dx, dy, dz, sag, startLight, endLight, THICKNESS, 0, offsetX, offsetZ, i, true);

        pose.popPose();
    }

    private static void vertexPair(VertexConsumer consumer, Matrix4f matrix, float dx, float dy, float dz, float sag,
                                   int startLight, int endLight, float thickness, float lift, float offsetX, float offsetZ, int segment, boolean flip) {
        float t = (float) segment / SEGMENTS;
        int block = (int) Mth.lerp(t, LightTexture.block(startLight), LightTexture.block(endLight));
        int sky = (int) Mth.lerp(t, LightTexture.sky(startLight), LightTexture.sky(endLight));
        int packed = LightTexture.pack(block, sky);

        float shade = segment % 2 == (flip ? 1 : 0) ? 0.75f : 1.0f;
        float grey = 0.09f * shade;

        float x = dx * t;
        float y = dy * t - sag * 4 * t * (1 - t);
        float z = dz * t;

        consumer.vertex(matrix, x - offsetX, y + lift, z + offsetZ).color(grey, grey, grey * 1.1f, 1.0f).uv2(packed).endVertex();
        consumer.vertex(matrix, x + offsetX, y + thickness - lift, z - offsetZ).color(grey, grey, grey * 1.1f, 1.0f).uv2(packed).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}

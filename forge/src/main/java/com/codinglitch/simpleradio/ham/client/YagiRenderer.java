package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.ham.block.YagiAntennaBlock;
import com.codinglitch.simpleradio.ham.block.YagiAntennaBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Renders the boom and elements of the yagi rotated to its azimuth (16 steps of 22.5 degrees).
 */
public class YagiRenderer implements BlockEntityRenderer<YagiAntennaBlockEntity> {
    public static final ResourceLocation BOOM_MODEL = CommonSimpleRadio.id("block/yagi_boom");

    public YagiRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(YagiAntennaBlockEntity blockEntity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = blockEntity.getBlockState();
        if (!(state.getBlock() instanceof YagiAntennaBlock)) return;

        BakedModel model = Minecraft.getInstance().getModelManager().getModel(BOOM_MODEL);
        float azimuth = (float) YagiAntennaBlock.azimuthOf(state);

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-azimuth));
        pose.translate(-0.5, 0, -0.5);

        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                pose.last(), buffers.getBuffer(RenderType.cutout()), state, model,
                1f, 1f, 1f, light, overlay, ModelData.EMPTY, RenderType.cutout());

        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(YagiAntennaBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}

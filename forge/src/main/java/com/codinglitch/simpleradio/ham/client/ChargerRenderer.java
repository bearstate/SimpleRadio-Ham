package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.ham.block.ChargerBlock;
import com.codinglitch.simpleradio.ham.block.ChargerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Shows the docked handheld standing in the charger.
 */
public class ChargerRenderer implements BlockEntityRenderer<ChargerBlockEntity> {
    public ChargerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ChargerBlockEntity charger, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = charger.getStack();
        if (stack.isEmpty()) return;

        float facing = charger.getBlockState().getValue(ChargerBlock.FACING).toYRot();

        pose.pushPose();
        pose.translate(0.5, 0.62, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing));
        pose.scale(0.55f, 0.55f, 0.55f);

        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, charger.getLevel(), (int) charger.getBlockPos().asLong());
        pose.popPose();
    }
}

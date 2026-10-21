package com.codinglitch.simpleradio.ham;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.ham.block.RepeaterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps enabled repeaters (and a small area around them for their power supply) loaded and ticking,
 * so a repeater built far away from every player keeps relaying. Tickets survive server restarts
 * and are validated on load.
 */
public final class HamChunkLoading {
    private HamChunkLoading() {}

    public static void register() {
        ForgeChunkManager.setForcedChunkLoadingCallback(CommonSimpleRadio.ID, HamChunkLoading::validate);
    }

    private static int radius() {
        return Math.max(0, Math.min(2, SimpleRadioLibrary.SERVER_CONFIG.repeater.chunkLoadRadius));
    }

    public static void setLoaded(ServerLevel level, BlockPos owner, boolean loaded) {
        int chunkX = owner.getX() >> 4;
        int chunkZ = owner.getZ() >> 4;
        // release the largest possible area so a changed radius does not leave tickets behind
        int radius = loaded ? radius() : 2;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ForgeChunkManager.forceChunk(level, CommonSimpleRadio.ID, owner, chunkX + dx, chunkZ + dz, loaded, true);
            }
        }
    }

    private static void validate(ServerLevel level, ForgeChunkManager.TicketHelper helper) {
        List<BlockPos> invalid = new ArrayList<>();
        for (BlockPos owner : helper.getBlockTickets().keySet()) {
            boolean keep = SimpleRadioLibrary.SERVER_CONFIG.repeater.chunkLoading
                    && level.getBlockState(owner).getBlock() instanceof RepeaterBlock;
            if (!keep) invalid.add(owner);
        }
        invalid.forEach(helper::removeAllTickets);
    }
}

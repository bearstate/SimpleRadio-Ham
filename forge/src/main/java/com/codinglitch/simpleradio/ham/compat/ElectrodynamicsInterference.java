package com.codinglitch.simpleradio.ham.compat;

import com.codinglitch.simpleradio.core.registry.SimpleRadioSounds;
import com.codinglitch.simpleradio.ham.radio.Propagation;
import electrodynamics.common.network.type.ElectricNetwork;
import electrodynamics.common.tile.electricitygrid.GenericTileWire;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.*;

/**
 * Electrodynamics side of {@link Interference}. Only class-loaded when Electrodynamics is present.
 * <p>
 * Electrodynamics wire networks act as a single shared bus, so a network that contains a disrupted
 * bare wire stops carrying power as a whole (see MixinElectricNetwork).
 */
public final class ElectrodynamicsInterference {
    private static final Map<ResourceKey<Level>, Map<BlockPos, Long>> DISRUPTED = new HashMap<>();
    // AbstractNetwork overrides equals but not hashCode, so identity is the safe choice
    private static final Set<ElectricNetwork> NETWORKS = Collections.newSetFromMap(new IdentityHashMap<>());

    private ElectrodynamicsInterference() {}

    public static boolean isDisrupted(ElectricNetwork network) {
        return !NETWORKS.isEmpty() && NETWORKS.contains(network);
    }

    private static boolean isBare(BlockEntity blockEntity) {
        return blockEntity instanceof GenericTileWire wire && !wire.getCableType().getInsulation().insulated();
    }

    static void disruptAround(ServerLevel level, BlockPos antenna, double radius, double azimuth, long until) {
        boolean directional = !Double.isNaN(azimuth);
        double reach = directional ? radius * 2.5 : radius;
        int r = (int) Math.ceil(reach);

        Map<BlockPos, Long> disrupted = DISRUPTED.computeIfAbsent(level.dimension(), key -> new HashMap<>());

        int minChunkX = (antenna.getX() - r) >> 4, maxChunkX = (antenna.getX() + r) >> 4;
        int minChunkZ = (antenna.getZ() - r) >> 4, maxChunkZ = (antenna.getZ() + r) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) continue;
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!isBare(blockEntity)) continue;

                    BlockPos pos = blockEntity.getBlockPos();
                    double distance = Math.sqrt(pos.distSqr(antenna));

                    double effective = radius;
                    if (directional) {
                        // the field is stronger in front of a yagi and weaker behind it
                        double offAxis = Propagation.angleBetween(azimuth, Propagation.azimuth(antenna, pos));
                        effective = radius * Math.max(0.5, Math.min(2.5, Math.sqrt(Propagation.yagiGain(offAxis))));
                    }
                    if (distance > effective) continue;

                    disrupted.merge(pos.immutable(), until, Math::max);
                }
            }
        }
    }

    static void refresh(MinecraftServer server) {
        NETWORKS.clear();
        if (DISRUPTED.isEmpty()) return;

        Iterator<Map.Entry<ResourceKey<Level>, Map<BlockPos, Long>>> levels = DISRUPTED.entrySet().iterator();
        while (levels.hasNext()) {
            Map.Entry<ResourceKey<Level>, Map<BlockPos, Long>> entry = levels.next();
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                levels.remove();
                continue;
            }

            long now = level.getGameTime();
            Iterator<Map.Entry<BlockPos, Long>> wires = entry.getValue().entrySet().iterator();
            while (wires.hasNext()) {
                Map.Entry<BlockPos, Long> wire = wires.next();
                BlockPos pos = wire.getKey();

                if (wire.getValue() < now || !level.isLoaded(pos)) {
                    wires.remove();
                    continue;
                }

                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (!(blockEntity instanceof GenericTileWire conductor) || !isBare(blockEntity)) {
                    wires.remove();
                    continue;
                }

                NETWORKS.add(conductor.getNetwork());

                // crackling bare wires
                if (level.random.nextInt(40) == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4,
                            0.25, 0.25, 0.25, 0.05);
                    if (level.random.nextInt(4) == 0) {
                        level.playSound(null, pos, SimpleRadioSounds.SHORT_CIRCUIT, SoundSource.BLOCKS, 0.08f, 1.5f + level.random.nextFloat() * 0.3f);
                    }
                }
            }

            if (entry.getValue().isEmpty()) levels.remove();
        }
    }

    static void clear() {
        DISRUPTED.clear();
        NETWORKS.clear();
    }
}

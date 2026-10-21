package com.codinglitch.simpleradio.ham.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The wire run through a building: the wired blocks touching one another face to face, from a source to whatever
 * takes power at its far end. The wired blocks hold no power and do no work of their own; a source walks the run and
 * hands power straight to the equipment along it, as a cable carries current without storing it.
 */
public final class WireNetwork {

    private WireNetwork() {}

    /** The longest run walked, in blocks. */
    public static final int LONGEST = 128;

    /** Where a piece of equipment along a run takes power: its block entity and the face the wire meets. */
    public record Sink(BlockEntity entity, Direction face) {
        public IEnergyStorage storage() {
            return entity.getCapability(ForgeCapabilities.ENERGY, face).orElse(null);
        }
    }

    /**
     * Everything that takes power along the runs leaving a source: the equipment touching the source itself, and the
     * equipment touching any wired block connected to it.
     */
    public static List<Sink> sinks(Level level, BlockPos source) {
        List<Sink> sinks = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> open = new ArrayDeque<>();
        seen.add(source);
        open.add(source);
        while (!open.isEmpty() && seen.size() <= LONGEST) {
            BlockPos at = open.poll();
            for (Direction d : Direction.values()) {
                BlockPos next = at.relative(d);
                if (seen.contains(next) || !level.isLoaded(next)) continue;
                if (level.getBlockState(next).getBlock() instanceof Wired) {
                    seen.add(next);
                    open.add(next);
                    continue;
                }
                BlockEntity be = level.getBlockEntity(next);
                if (be == null || be.getBlockPos().equals(source)) continue;
                IEnergyStorage storage = be.getCapability(ForgeCapabilities.ENERGY, d.getOpposite()).orElse(null);
                if (storage != null && storage.canReceive()) {
                    seen.add(next);
                    sinks.add(new Sink(be, d.getOpposite()));
                }
            }
        }
        return sinks;
    }

    /** A block that carries power along a run: looks like what it is, with the wire laid in it. */
    public interface Wired {}
}

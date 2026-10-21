package com.codinglitch.simpleradio.ham.compat;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

/**
 * Strong radio frequency fields induce currents in bare conductors. While a station transmits
 * above the configured power, nearby uninsulated Electrodynamics wires lose their power.
 */
public final class Interference {
    private static Boolean electrodynamicsLoaded;

    private Interference() {}

    public static boolean isElectrodynamicsLoaded() {
        if (electrodynamicsLoaded == null) electrodynamicsLoaded = ModList.get().isLoaded("electrodynamics");
        return electrodynamicsLoaded;
    }

    /**
     * Radiates from an antenna for a short while.
     * @param level the level of the antenna
     * @param antenna the radiating element
     * @param watts the output power
     * @param azimuth the yagi azimuth in degrees, or NaN for an omnidirectional antenna
     */
    public static void emit(ServerLevel level, BlockPos antenna, int watts, double azimuth) {
        if (!SimpleRadioLibrary.SERVER_CONFIG.interference.enabled) return;
        if (watts < SimpleRadioLibrary.SERVER_CONFIG.interference.minWatts) return;
        if (!isElectrodynamicsLoaded()) return;

        double radius = SimpleRadioLibrary.SERVER_CONFIG.interference.radiusPerSqrtWatt * Math.sqrt(watts);
        long until = level.getGameTime() + SimpleRadioLibrary.SERVER_CONFIG.interference.lingerTicks + 10;

        ElectrodynamicsInterference.disruptAround(level, antenna, radius, azimuth, until);
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isElectrodynamicsLoaded()) return;

        ElectrodynamicsInterference.refresh(event.getServer());
    }

    public static void clear() {
        if (isElectrodynamicsLoaded()) ElectrodynamicsInterference.clear();
    }
}

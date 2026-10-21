package com.codinglitch.simpleradio.ham.radio;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * The receiver of a handheld direction finder: a small directional antenna pointed where its holder looks,
 * plus an attenuator for when the fox is close.
 */
public class DirectionalReceiver extends RadioReceiver {
    public volatile double attenuationDb = 0;

    public DirectionalReceiver(Frequency frequency, Entity owner, UUID id) {
        super(frequency, owner, id);
    }

    /**
     * Relative gain (0-1) of the antenna at an angle off its pointing direction.
     */
    public static double pattern(double offAxis) {
        var config = SimpleRadioLibrary.SERVER_CONFIG.direction_finder;
        double back = Math.pow(10, -config.frontToBackDb / 10.0);
        double theta = Math.abs(offAxis);
        if (theta >= 90) return back;

        double halfBeam = Math.toRadians(Math.max(5, Math.min(170, config.beamWidth)) / 2.0);
        double exponent = Math.log(0.5) / Math.log(Math.cos(halfBeam));
        return Math.max(back, Math.pow(Math.cos(Math.toRadians(theta)), exponent));
    }

    /**
     * Compass direction (0 = north) an entity is looking at.
     */
    public static double lookAzimuth(Entity entity) {
        return ((entity.getYHeadRot() + 180) % 360 + 360) % 360;
    }

    @Override
    public double directionalLossDb(WorldlyPosition other) {
        if (owner == null || other == null) return attenuationDb;
        double target = Propagation.azimuth(owner.getX(), owner.getZ(), other.x, other.z);
        double offAxis = Propagation.angleBetween(target, lookAzimuth(owner));
        return -10 * Math.log10(pattern(offAxis)) + attenuationDb;
    }
}

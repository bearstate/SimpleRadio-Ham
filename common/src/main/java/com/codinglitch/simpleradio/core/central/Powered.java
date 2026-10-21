package com.codinglitch.simpleradio.core.central;

/**
 * Radio equipment that needs electricity. The loader attaches the
 * energy capabilities (Electrodynamics / Forge Energy) to block entities implementing this.
 */
public interface Powered {
    PowerBuffer getPowerBuffer();

    /**
     * Amateur radio equipment runs on 13.8 V DC from a power supply instead of mains.
     */
    default boolean needsDc() {
        return false;
    }
}

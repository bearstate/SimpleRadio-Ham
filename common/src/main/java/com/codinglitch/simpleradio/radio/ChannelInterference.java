package com.codinglitch.simpleradio.radio;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.Frequency;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Several transmitters at once on one channel, as heard by one receiver.
 * <ul>
 *     <li>FM: the capture effect - a clearly stronger signal takes over and the weaker one disappears;
 *     two about equally strong ones garble each other ("doubling").</li>
 *     <li>AM: both are heard, and their carriers whistle (heterodyne).</li>
 *     <li>SSB: both are simply heard at once.</li>
 * </ul>
 * Keeps the phase of its tones between frames, so every speaker needs its own instance.
 */
public class ChannelInterference {
    /**
     * One transmitter's frame.
     * @param signalDb its strength (NaN: unknown, e.g. over wires - counts as very strong)
     * @param carrierOffsetHz how far its carrier is off the channel (AM carriers whistle at their difference)
     */
    public record Input(short[] frame, float signalDb, @Nullable Frequency.Modulation modulation, double carrierOffsetHz) {
        float strength() {
            return Float.isNaN(signalDb) ? Float.MAX_VALUE : signalDb;
        }
    }

    private final Random random = new Random();
    private double beatPhase = 0;
    private double flutterPhase = 0;

    public short[] mix(List<Input> inputs) {
        if (inputs.size() == 1) return inputs.get(0).frame();
        double captureRatio = SimpleRadioLibrary.SERVER_CONFIG.frequency.captureRatioDb;

        Input strongest = null;
        for (Input input : inputs) {
            if (input.modulation() != Frequency.Modulation.FREQUENCY) continue;
            if (strongest == null || input.strength() > strongest.strength()) strongest = input;
        }

        List<short[]> heard = new ArrayList<>();
        List<Double> amCarriers = new ArrayList<>();
        boolean doubling = false;
        for (Input input : inputs) {
            if (input.modulation() == Frequency.Modulation.FREQUENCY && strongest != null && input != strongest) {
                if (strongest.strength() - input.strength() >= captureRatio) continue; // captured away
                doubling = true;
            }
            if (input.modulation() == Frequency.Modulation.AMPLITUDE) amCarriers.add(input.carrierOffsetHz());
            heard.add(input.frame());
        }

        short[] mixed = CommonRadioPlugin.combineAudio(heard);
        if (!doubling && amCarriers.size() < 2) return mixed;

        double beatHz = amCarriers.size() >= 2 ? Math.abs(amCarriers.get(0) - amCarriers.get(1)) + 150 : 0;
        short[] result = new short[mixed.length];
        for (int i = 0; i < mixed.length; i++) {
            double sample = mixed[i] / 32768.0;

            if (doubling) {
                // two FM carriers beating: the audio flutters and is torn up by noise
                flutterPhase += 2 * Math.PI * 47 / 48000.0;
                sample *= 1 - 0.8 * Math.abs(Math.sin(flutterPhase));
                sample += (random.nextDouble() * 2 - 1) * 0.12;
            }
            if (beatHz > 0) {
                beatPhase += 2 * Math.PI * beatHz / 48000.0;
                sample += Math.sin(beatPhase) * 0.12;
            }

            result[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sample * 32767)));
        }
        flutterPhase %= Math.PI * 2;
        beatPhase %= Math.PI * 2;
        return result;
    }
}

package com.codinglitch.simpleradio.ham.aprs;

import com.codinglitch.simpleradio.ham.radio.CwGenerator;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Real APRS audio: an AX.25 UI frame sent as Bell 202 AFSK at 1200 baud (1200 Hz mark, 2200 Hz space,
 * NRZI with bit stuffing). A software TNC listening to the radio's speaker can decode it.
 */
public final class Afsk {
    private static final int BAUD = 1200;
    private static final double MARK = 1200, SPACE = 2200;
    private static final int PREAMBLE_FLAGS = 40;
    private static final int TAIL_FLAGS = 4;

    private Afsk() {}

    // ---- AX.25 ---- \\

    private static void address(ByteArrayOutputStream out, String callsign, int ssid, boolean last) {
        String call = (callsign.toUpperCase(Locale.ROOT) + "      ").substring(0, 6);
        for (char c : call.toCharArray()) out.write((c & 0x7F) << 1);
        out.write(0x60 | ((ssid & 0x0F) << 1) | (last ? 1 : 0));
    }

    /**
     * A UI frame (without flags) including its frame check sequence.
     */
    public static byte[] frame(String source, int sourceSsid, String information) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        address(out, "APZSRD", 0, false);       // destination: experimental software id
        address(out, source, sourceSsid, false);
        address(out, "WIDE1", 1, true);          // path
        out.write(0x03); // UI frame
        out.write(0xF0); // no layer 3
        byte[] info = information.getBytes(StandardCharsets.US_ASCII);
        out.write(info, 0, info.length);

        byte[] body = out.toByteArray();
        int fcs = crc(body);
        out.write(fcs & 0xFF);
        out.write((fcs >> 8) & 0xFF);
        return out.toByteArray();
    }

    /**
     * CRC-16-CCITT as used by AX.25 (reflected, initial 0xFFFF, final complement).
     */
    public static int crc(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc ^= b & 0xFF;
            for (int i = 0; i < 8; i++) crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0x8408 : crc >>> 1;
        }
        return ~crc & 0xFFFF;
    }

    /**
     * Bits on the air: flags, the bit-stuffed frame (least significant bit first), flags.
     */
    static List<Boolean> bits(byte[] frame) {
        List<Boolean> bits = new ArrayList<>();
        for (int i = 0; i < PREAMBLE_FLAGS; i++) addByte(bits, 0x7E);

        int ones = 0;
        for (byte b : frame) {
            for (int i = 0; i < 8; i++) {
                boolean bit = ((b >> i) & 1) == 1;
                bits.add(bit);
                if (bit) {
                    if (++ones == 5) {
                        bits.add(false);
                        ones = 0;
                    }
                } else {
                    ones = 0;
                }
            }
        }

        for (int i = 0; i < TAIL_FLAGS; i++) addByte(bits, 0x7E);
        return bits;
    }

    private static void addByte(List<Boolean> bits, int value) {
        for (int i = 0; i < 8; i++) bits.add(((value >> i) & 1) == 1);
    }

    /**
     * Renders a frame as 20 ms audio frames (48 kHz): NRZI, a 0 bit toggles the tone.
     */
    public static List<short[]> render(byte[] frame, double amplitude) {
        List<Boolean> bits = bits(frame);
        int samplesPerBit = CwGenerator.SAMPLE_RATE / BAUD;
        int total = bits.size() * samplesPerBit;
        int frameCount = (total + CwGenerator.FRAME_SIZE - 1) / CwGenerator.FRAME_SIZE;
        short[] samples = new short[frameCount * CwGenerator.FRAME_SIZE];

        boolean mark = true;
        double phase = 0;
        double peak = Math.max(0, Math.min(1, amplitude)) * Short.MAX_VALUE;
        int index = 0;
        for (boolean bit : bits) {
            if (!bit) mark = !mark;
            double step = 2 * Math.PI * (mark ? MARK : SPACE) / CwGenerator.SAMPLE_RATE;
            for (int i = 0; i < samplesPerBit; i++) {
                samples[index++] = (short) Math.round(Math.sin(phase) * peak);
                phase += step;
            }
            phase %= 2 * Math.PI;
        }

        List<short[]> frames = new ArrayList<>();
        for (int f = 0; f < frameCount; f++) {
            short[] out = new short[CwGenerator.FRAME_SIZE];
            System.arraycopy(samples, f * CwGenerator.FRAME_SIZE, out, 0, CwGenerator.FRAME_SIZE);
            frames.add(out);
        }
        return frames;
    }
}

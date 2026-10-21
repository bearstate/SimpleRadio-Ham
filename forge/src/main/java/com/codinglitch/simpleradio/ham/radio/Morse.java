package com.codinglitch.simpleradio.ham.radio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders text as morse code audio (e.g. a repeater's call sign or a fox hunt transmitter's MOE).
 * Timing follows the PARIS standard: a dit lasts 1200 / WPM milliseconds.
 */
public final class Morse {
    private static final Map<Character, String> CODE = Map.ofEntries(
            Map.entry('A', ".-"), Map.entry('B', "-..."), Map.entry('C', "-.-."), Map.entry('D', "-.."),
            Map.entry('E', "."), Map.entry('F', "..-."), Map.entry('G', "--."), Map.entry('H', "...."),
            Map.entry('I', ".."), Map.entry('J', ".---"), Map.entry('K', "-.-"), Map.entry('L', ".-.."),
            Map.entry('M', "--"), Map.entry('N', "-."), Map.entry('O', "---"), Map.entry('P', ".--."),
            Map.entry('Q', "--.-"), Map.entry('R', ".-."), Map.entry('S', "..."), Map.entry('T', "-"),
            Map.entry('U', "..-"), Map.entry('V', "...-"), Map.entry('W', ".--"), Map.entry('X', "-..-"),
            Map.entry('Y', "-.--"), Map.entry('Z', "--.."),
            Map.entry('0', "-----"), Map.entry('1', ".----"), Map.entry('2', "..---"), Map.entry('3', "...--"),
            Map.entry('4', "....-"), Map.entry('5', "....."), Map.entry('6', "-...."), Map.entry('7', "--..."),
            Map.entry('8', "---.."), Map.entry('9', "----."),
            Map.entry('/', "-..-."), Map.entry('?', "..--.."), Map.entry('.', ".-.-.-"), Map.entry('=', "-...-")
    );

    private Morse() {}

    public static boolean isValid(String text) {
        for (char character : text.toUpperCase(Locale.ROOT).toCharArray()) {
            if (character != ' ' && !CODE.containsKey(character)) return false;
        }
        return true;
    }

    /**
     * @return pairs of {tone Hz (0 = silence), duration ms}, ready for {@link ToneBurst#render(int[][], double)}
     */
    public static int[][] pattern(String text, int wpm, int toneHz) {
        int dit = Math.max(20, 1200 / Math.max(5, wpm));
        List<int[]> elements = new ArrayList<>();

        String upper = text.toUpperCase(Locale.ROOT).trim();
        for (int i = 0; i < upper.length(); i++) {
            char character = upper.charAt(i);
            if (character == ' ') {
                elements.add(new int[]{0, dit * 4}); // with the letter gap before it: 7 dits between words
                continue;
            }

            String code = CODE.get(character);
            if (code == null) continue;
            for (int j = 0; j < code.length(); j++) {
                elements.add(new int[]{toneHz, code.charAt(j) == '-' ? dit * 3 : dit});
                elements.add(new int[]{0, dit});
            }
            elements.add(new int[]{0, dit * 2}); // letter gap: 3 dits
        }
        return elements.toArray(new int[0][]);
    }

    public static List<short[]> render(String text, int wpm, int toneHz, double amplitude) {
        return ToneBurst.render(pattern(text, wpm, toneHz), amplitude);
    }
}

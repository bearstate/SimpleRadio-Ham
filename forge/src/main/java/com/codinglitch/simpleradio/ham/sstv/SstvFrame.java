package com.codinglitch.simpleradio.ham.sstv;

import java.util.UUID;

/**
 * Picture data riding along with one 20 ms frame of SSTV audio: which picture, and which of its lines the
 * frame carries (-1 while the VIS header is sent).
 */
public record SstvFrame(UUID picture, byte[] colors, int firstLine, int lastLine, boolean last) {}

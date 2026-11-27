package io.github.ada.listeditor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reading what is typed into a cell. A parse that fails throws {@link IllegalArgumentException} with the reason. */
final class Entry {
    private static final Pattern NOTE = Pattern.compile("^([A-Ga-g])([#b-]?)([0-9]+)?([#b-]?)$");

    private Entry() {
    }

    /**
     * A time in beats, in 4/4, from what is typed:
     * "2.1.480" in full, "2.1" (tick 0), "2" (beat 1); or, with a leading dot, only the last parts, keeping
     * the others from {@code current}: ".240" is the tick, ".3.480" the beat and tick.
     */
    static double time(String text, double current) {
        long total = Math.round(current * NoteList.TICKS);
        long perBar = 4L * NoteList.TICKS;
        long[] now = {Math.floorDiv(total, perBar) + 1, Math.floorMod(total, perBar) / NoteList.TICKS + 1,
            Math.floorMod(total, (long) NoteList.TICKS)};
        boolean partial = text.startsWith(".");
        String[] parts = (partial ? text.substring(1) : text).split("[.]", -1);
        if (parts.length > 3 || (!partial && parts[0].isEmpty())) {
            throw new IllegalArgumentException("time: bar.beat.tick, or .beat.tick / .tick");
        }
        long[] value = new long[3];
        for (int i = 0; i < 3; i++) {
            int at = partial ? i - (3 - parts.length) : i; // a leading dot lines the parts up with the last ones
            if (at >= 0 && at < parts.length && !parts[at].isEmpty()) {
                value[i] = Long.parseLong(parts[at]);
            } else {
                value[i] = partial ? now[i] : (i == 1 ? 1 : 0); // typed in full: what is left out is beat 1, tick 0
                if (!partial && i == 0) {
                    throw new IllegalArgumentException("time: bar.beat.tick");
                }
            }
        }
        if (value[0] < 1 || value[1] < 1 || value[1] > 4 || value[2] < 0 || value[2] >= NoteList.TICKS) {
            throw new IllegalArgumentException("time: bar.beat(1-4).tick(0-959)");
        }
        return (value[0] - 1) * 4.0 + (value[1] - 1) + (double) value[2] / NoteList.TICKS;
    }

    /**
     * "C3", "C#3", "C3#" (sharp: a semitone up), "Db3", "D-3", "D3b" (flat: a semitone down; a "b" after a note
     * letter is a flat, so "bb" is B flat), "61", or a letter alone (C, C#, Db) to keep the octave of
     * {@code current}. Bitwig's C3 is 60; octaves below 0 are typed as numbers.
     */
    static int key(String text, int current) {
        if (text.matches("[0-9]+")) {
            int key = Integer.parseInt(text);
            if (key > 127) {
                throw new IllegalArgumentException("note: 0-127");
            }
            return key;
        }
        Matcher m = NOTE.matcher(text);
        if (!m.matches()) {
            throw new IllegalArgumentException("note: C3, C#3, Db3 (b or - is flat) or 0-127");
        }
        int pitch = switch (Character.toUpperCase(m.group(1).charAt(0))) {
            case 'C' -> 0; case 'D' -> 2; case 'E' -> 4; case 'F' -> 5; case 'G' -> 7; case 'A' -> 9; default -> 11;
        };
        for (String accidental : new String[] {m.group(2), m.group(4)}) {
            if (accidental.equals("#")) {
                pitch++;
            } else if (accidental.equals("-") || accidental.equals("b")) {
                pitch--;
            }
        }
        int octave = m.group(3) == null ? Math.floorDiv(current, 12) - 2 : Integer.parseInt(m.group(3));
        int key = (octave + 2) * 12 + pitch;
        if (key < 0 || key > 127) {
            throw new IllegalArgumentException("note: out of range");
        }
        return key;
    }

    /** A whole number of ticks, at least {@code min}. */
    static long ticks(String text, long min) {
        long value = Long.parseLong(text);
        if (value < min) {
            throw new IllegalArgumentException("at least " + min);
        }
        return value;
    }

    /** A velocity, 1 to 127. */
    static int velocity(String text) {
        int value = Integer.parseInt(text);
        if (value < 1 || value > 127) {
            throw new IllegalArgumentException("velocity: 1-127");
        }
        return value;
    }
}

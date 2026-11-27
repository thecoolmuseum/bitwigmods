package io.github.ada.listeditor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reading what is typed into a cell. A parse that fails throws {@link IllegalArgumentException} with the reason. */
final class Entry {
    private static final Pattern NOTE = Pattern.compile("^([A-Ga-g])(#?)(-?[0-9]+)?(#?)$");

    private Entry() {
    }

    /** "2.1.480", "2.1" (tick 0), "2" (beat 1): a time in beats, in 4/4. */
    static double time(String text) {
        String[] parts = text.split("[.]", -1);
        if (parts.length > 3 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("time: bar.beat.tick");
        }
        long bar = Long.parseLong(parts[0]);
        long beat = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : 1;
        long tick = parts.length > 2 && !parts[2].isEmpty() ? Long.parseLong(parts[2]) : 0;
        if (bar < 1 || beat < 1 || beat > 4 || tick < 0 || tick >= NoteList.TICKS) {
            throw new IllegalArgumentException("time: bar.beat(1-4).tick(0-959)");
        }
        return (bar - 1) * 4.0 + (beat - 1) + (double) tick / NoteList.TICKS;
    }

    /** "C3", "C#3", "C3#", "61", or a letter alone (C, C#) to keep the octave of {@code current}. Bitwig's C3 is 60. */
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
            throw new IllegalArgumentException("note: C3, C#3, C3# or 0-127");
        }
        int pitch = switch (Character.toUpperCase(m.group(1).charAt(0))) {
            case 'C' -> 0; case 'D' -> 2; case 'E' -> 4; case 'F' -> 5; case 'G' -> 7; case 'A' -> 9; default -> 11;
        };
        if (!m.group(2).isEmpty() || !m.group(4).isEmpty()) {
            pitch++;
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

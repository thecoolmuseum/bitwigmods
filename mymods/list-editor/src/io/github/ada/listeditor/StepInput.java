package io.github.ada.listeditor;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * The settings of step input, after QueX's STEP REC: the step value (ST), the gate multiplier (GT) and the
 * velocity (VEL), and the keys that change them. They act only while REC is on.
 *
 * ST by the digit keys: 1-6 are whole, half, quarter, eighth, sixteenth and thirty-second notes; the same key
 * again adds; 7 adds a dot (again for a double dot); 8 makes a triplet (two thirds); 9 then a number and Enter
 * divides by that number; 0 then a number and Enter sets the ticks. GT: the key right of the dash cycles the
 * multiplier. VEL: the backslash key, then a number 1-127 and Enter, or Enter alone for KEY (the velocity played).
 * Nothing is written to the clip yet.
 */
final class StepInput {
    private static final int TICKS = NoteList.TICKS;
    /** Whole, half, quarter, eighth, sixteenth and thirty-second notes, in ticks. */
    private static final int[] BASE = {4 * TICKS, 2 * TICKS, TICKS, TICKS / 2, TICKS / 4, TICKS / 8};
    /** The gate multipliers the GT key cycles through (QueX: SHT, STC, NML, LEG, SLR). */
    private static final double[] GATES = {0.25, 0.5, 0.8, 0.95, 1.05};
    private static final int MAX_STEP = 16 * TICKS;
    private static final DecimalFormat GATE_FORMAT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private enum Typing { NONE, N_TUPLET, DIRECT, VELOCITY }

    private boolean rec;
    private int step = TICKS / 4; // a sixteenth
    private int gate = 2; // 0.8
    private int velocity = 100;
    private boolean velocityFromKey;
    private Typing typing = Typing.NONE;
    private final StringBuilder buffer = new StringBuilder();
    private int lastDigit; // the last of 1-6 pressed, with nothing else between; 0 when none
    private boolean dotChain;
    private int dotBase;
    private int dotCount;
    private String message = "";
    private double position; // where input goes, in beats from the start of the clip

    /** What a move of the input position goes by. */
    enum Unit { STEP, BEAT, BAR, TICK }

    /** Beats in a bar. 4/4 for now: Bitwig's own time signature is to be read later. */
    private static final double BEATS_PER_BAR = 4;

    double position() {
        return position;
    }

    void setPosition(double beats) {
        position = Math.max(0, Math.round(beats * TICKS) / (double) TICKS);
    }

    /** The step in beats. */
    double stepBeats() {
        return (double) step / TICKS;
    }

    /** The length of the last note of a run: the step times the gate, at least a tick. */
    double gateBeats() {
        return Math.max(1.0 / TICKS, stepBeats() * GATES[gate]);
    }

    boolean velocityFromKey() {
        return velocityFromKey;
    }

    int velocity() {
        return velocity;
    }

    /** Moves the input position on by {@code beats}. */
    void advance(double beats) {
        setPosition(position + beats);
    }

    /** Home: the start of the clip. */
    void moveToStart() {
        setPosition(0);
    }

    /** End: the start of the bar after the one {@code lastBeat} is in (the clip's last note). */
    void moveToBarAfter(double lastBeat) {
        setPosition((Math.floor(lastBeat / BEATS_PER_BAR + 1e-9) + 1) * BEATS_PER_BAR);
    }

    /** Moves the input position: by the step, to the next or previous beat or bar, or by a tick. */
    void move(int direction, Unit unit) {
        double eps = 1e-9;
        double next = switch (unit) {
            case STEP -> position + direction * (double) step / TICKS;
            case BEAT -> direction > 0 ? Math.floor(position + eps) + 1 : Math.ceil(position - eps) - 1;
            case BAR -> direction > 0 ? (Math.floor(position / BEATS_PER_BAR + eps) + 1) * BEATS_PER_BAR
                    : (Math.ceil(position / BEATS_PER_BAR - eps) - 1) * BEATS_PER_BAR;
            case TICK -> position + direction / (double) TICKS;
        };
        setPosition(next);
    }

    boolean rec() {
        return rec;
    }

    void toggleRec() {
        rec = !rec;
        typing = Typing.NONE;
        buffer.setLength(0);
        message = rec ? "REC on" : "REC off";
    }

    String message() {
        return message;
    }

    /** Whether a number is being typed (the digits then belong to it). */
    boolean typing() {
        return typing != Typing.NONE;
    }

    /** The guide for the bottom line while REC is on. */
    static String guide() {
        return "0-9:ST  ^:GT  \\:VEL  R:REST  T:TIE";
    }

    /** "ST: 1/16(240)  GT: 0.5 VEL: 127", the number being typed shown in place of the value it sets. */
    String status() {
        String st = typing == Typing.N_TUPLET ? "/" + buffer + "_"
                : typing == Typing.DIRECT ? "=" + buffer + "_" : label(step);
        String vel = typing == Typing.VELOCITY ? buffer + "_" : velocityFromKey ? "KEY" : Integer.toString(velocity);
        return "ST: " + st + "  GT: " + GATE_FORMAT.format(GATES[gate]) + " VEL: " + vel;
    }

    /** The step as a note value and its ticks: "1/16(240)", "1/8.(720)", "1/8T(320)", or just "(77)". */
    static String label(int ticks) {
        for (int n = 1; n <= 128; n *= 2) {
            int base = 4 * TICKS / n;
            String name = "1/" + n;
            if (ticks == base) {
                return name + "(" + ticks + ")";
            }
            if (ticks * 2 == base * 3) {
                return name + "." + "(" + ticks + ")";
            }
            if (ticks * 4 == base * 7) {
                return name + ".." + "(" + ticks + ")";
            }
            if (ticks * 3 == base * 2) {
                return name + "T(" + ticks + ")";
            }
        }
        return "(" + ticks + ")";
    }

    /** A digit key's number, or -1. */
    private static int digit(String key) {
        if (key.length() == 1 && key.charAt(0) >= '0' && key.charAt(0) <= '9') {
            return key.charAt(0) - '0';
        }
        if (key.startsWith("keypad_") && key.length() == 8 && Character.isDigit(key.charAt(7))) {
            return key.charAt(7) - '0';
        }
        return -1;
    }

    /**
     * A key while REC is on: {@code key} is the key as the layout names it, {@code physical} as a US keyboard
     * names the same place. Returns whether the key was used here.
     */
    boolean key(String key, String physical) {
        int digit = digit(key);
        if (typing != Typing.NONE) {
            return typed(key, digit);
        }
        if (digit >= 0) {
            digit(digit);
            return true;
        }
        if (isGateKey(key) || isGateKey(physical)) {
            gate = (gate + 1) % GATES.length; // the gate multiplier
            message = "GT " + GATE_FORMAT.format(GATES[gate]);
            return true;
        }
        if (physical.equals("backslash") || key.equals("backslash") || key.equals("oem_5") || key.equals("yen")) {
            typing = Typing.VELOCITY;
            buffer.setLength(0);
            message = "VEL: 1-127 and Enter (0, 128 or more, or nothing: KEY)";
            return true;
        }
        message = "key: " + key + " / " + physical;
        return false;
    }

    /** The key right of the dash, whatever the layout calls it: = on a US keyboard, ^ on a Japanese one. */
    private static boolean isGateKey(String id) {
        return switch (id) {
            case "equal", "circumflex_accent", "dead_circumflex_accent", "oem_7", "tilde" -> true;
            default -> false;
        };
    }

    private void digit(int digit) {
        if (digit >= 1 && digit <= 6) {
            int base = BASE[digit - 1];
            step = lastDigit == digit ? step + base : base; // the same key again adds
            lastDigit = digit;
            dotChain = false;
        } else if (digit == 7) { // a dot: half of what the last one added
            if (!dotChain) {
                dotChain = true;
                dotBase = step;
                dotCount = 0;
            }
            dotCount++;
            step += (int) Math.round(dotBase * Math.pow(0.5, dotCount));
            lastDigit = 0;
        } else if (digit == 8) { // a triplet
            step = (int) Math.round(step * 2.0 / 3.0);
            lastDigit = 0;
            dotChain = false;
        } else { // 9: divide by N, 0: the ticks
            typing = digit == 9 ? Typing.N_TUPLET : Typing.DIRECT;
            buffer.setLength(0);
            lastDigit = 0;
            dotChain = false;
            message = digit == 9 ? "N: number and Enter" : "ticks: number and Enter";
        }
        step = Math.max(1, Math.min(MAX_STEP, step));
    }

    /** A key while a number is being typed. */
    private boolean typed(String key, int digit) {
        if (digit >= 0) {
            if (buffer.length() < 5) {
                buffer.append(digit);
            }
            return true;
        }
        switch (key) {
            case "backspace" -> buffer.setLength(Math.max(0, buffer.length() - 1));
            case "escape" -> {
                typing = Typing.NONE;
                message = "";
            }
            case "enter", "keypad_enter" -> confirm();
            default -> {
                return false;
            }
        }
        return true;
    }

    private void confirm() {
        Typing was = typing;
        typing = Typing.NONE;
        String text = buffer.toString();
        buffer.setLength(0);
        message = "";
        if (was == Typing.VELOCITY) {
            if (text.isEmpty()) {
                velocityFromKey = true;
                return;
            }
            int value = Integer.parseInt(text);
            if (value < 1 || value > 127) { // 0 or more than 127: the velocity played
                velocityFromKey = true;
                return;
            }
            velocity = value;
            velocityFromKey = false;
            return;
        }
        if (text.isEmpty()) {
            return; // nothing typed: nothing changes
        }
        int value = Integer.parseInt(text);
        if (value < 1) {
            message = "at least 1";
            return;
        }
        step = Math.max(1, Math.min(MAX_STEP, was == Typing.N_TUPLET ? (int) Math.round((double) step / value) : value));
    }
}

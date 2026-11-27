package io.github.ada.listeditor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import bitwig.obf.aEx; // the computer keyboard's note input: it sends the notes to the audio engine
import com.bitwig.flt.app.Hvv; // the application

/**
 * Plays notes through Bitwig's own "computer keyboard" path, so that they sound on the tracks that listen to the
 * computer keyboard (the selected, armed or monitored ones). An experiment: the list editor's notes for a key
 * played on the virtual keyboard, and short previews of a note when the cursor lands on it or its pitch is set.
 *
 * Everything runs on Bitwig's UI thread. A preview ends by itself, from the display-frame tick.
 */
final class Sound {
    private static final long PREVIEW_NANOS = 250_000_000L;

    /** Whether previews and the virtual keyboard sound at all. */
    private static boolean enabled = true;
    /** Notes sounding from a key held, by the note. */
    private static final Set<Integer> held = new HashSet<>();
    /** Previews sounding, by the note, and the time they end. */
    private static final Map<Integer, Long> previews = new HashMap<>();

    private Sound() {
    }

    static boolean enabled() {
        return enabled;
    }

    static void toggle() {
        enabled = !enabled;
        if (!enabled) {
            allOff();
        }
    }

    private static aEx keyboard() {
        return Hvv.mEL().GwM().beI(); // the application's computer keyboard
    }

    /** What the sound goes to, for the user to read: the port number, or why there is none. */
    static String describe() {
        try {
            aEx keyboard = keyboard();
            if (keyboard == null) {
                return "no computer keyboard input";
            }
            return keyboard.ymo < 0 ? "no port (-1)" : "port " + keyboard.ymo; // ymo: the port, -1 when there is none
        } catch (RuntimeException | LinkageError e) {
            return "not reachable: " + e;
        }
    }

    /** A key is down: the note sounds until {@link #noteOff}. Returns null, or why it could not. */
    static String noteOn(int note, double velocity) {
        if (!enabled) {
            return null;
        }
        try {
            if (held.add(note)) {
                keyboard().beI(note, (float) Math.max(0.01, Math.min(1.0, velocity))); // note on
            }
            return null;
        } catch (RuntimeException | LinkageError e) {
            held.remove(note);
            return "sound failed: " + e;
        }
    }

    /** The key is up. */
    static void noteOff(int note) {
        if (held.remove(note)) {
            off(note);
        }
    }

    /** A short sound of a note, as an answer to the cursor landing on it or its pitch being set. */
    static String preview(int note, double velocity) {
        if (!enabled || held.contains(note)) {
            return null;
        }
        try {
            if (previews.containsKey(note)) {
                off(note); // sound it again from the start
            }
            keyboard().beI(note, (float) Math.max(0.01, Math.min(1.0, velocity)));
            previews.put(note, System.nanoTime() + PREVIEW_NANOS);
            return null;
        } catch (RuntimeException | LinkageError e) {
            previews.remove(note);
            return "sound failed: " + e;
        }
    }

    /** Called before each display frame: ends the previews whose time is up. */
    static void tick() {
        if (previews.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        Iterator<Map.Entry<Integer, Long>> each = previews.entrySet().iterator();
        while (each.hasNext()) {
            Map.Entry<Integer, Long> preview = each.next();
            if (now - preview.getValue() >= 0) {
                each.remove();
                off(preview.getKey());
            }
        }
    }

    /** Every note off: REC was turned off, the panel was closed, or sound was switched off. */
    static void allOff() {
        for (Integer note : new HashSet<>(held)) {
            off(note);
        }
        held.clear();
        for (Integer note : new HashSet<>(previews.keySet())) {
            off(note);
        }
        previews.clear();
    }

    private static void off(int note) {
        try {
            keyboard().Gef(note, 0f); // note off
        } catch (RuntimeException | LinkageError e) {
            ListEditorPanels.MOD.log("could not end a note", e);
        }
    }
}

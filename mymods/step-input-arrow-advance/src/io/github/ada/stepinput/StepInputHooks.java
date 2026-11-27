package io.github.ada.stepinput;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bitwig.obf.bjW; // the piano roll's step input: the held keys, the pending notes and the step cursor
import bitwig.obf.bjY; // a key held down in step input
import bitwig.obf.bjZ; // a note step input has started but not yet written
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;

/**
 * Piano Roll: Step Input Arrow Advance.
 *
 * Bitwig's step input has two ways to finish when a key is released. A plain tap (or chord) takes the
 * "collecting" path: the cursor advances one step, every pending note is stretched to the cursor and
 * written. After "move time selection one step later" with a key held, Bitwig leaves collecting mode, and
 * a release only writes that key's note: no advance, and a key pressed after the arrow gets no note.
 * This mod keeps the release of an arrow-extended group on the collecting path: keys released together
 * (within Bitwig's chord time) are written together with the advance.
 */
public final class StepInputHooks {
    static final Bml.Mod MOD = Bml.mod("step-input-arrow-advance");

    /** Bitwig's chord time: keys released within this of each other belong together (nanoseconds). */
    private static final long CHORD_NANOS = 120_000_000L;

    private enum Mode { NONE, EXTEND, BACK }

    /** What the arrows did while keys were held: step forward extends, step back erases. */
    private static Mode mode = Mode.NONE;

    /** Keys released while others were still held, and when. */
    private static final Map<Integer, Long> DEFERRED = new HashMap<>();

    private StepInputHooks() {
    }

    /** The pending note of this key, or null. */
    private static bjZ pending(bjW input, int key) {
        for (Object o : input.unz) { // the pending notes
            if (o instanceof bjZ note && note.beI() == key) { // beI(): its key
                return note;
            }
        }
        return null;
    }

    private static void reset() {
        mode = Mode.NONE;
        DEFERRED.clear();
    }

    /** "Move time selection one step earlier" with a key held: stepping back erases and writes nothing. */
    @Hook(target = bjW.class, method = "beI(Lbjv;LdWa;LdWc;)V", at = At.ENTRY, id = "note-step-back")
    public static void beforeStepBack(@This bjW input) {
        if (input.Anf()) { // a key is held
            mode = Mode.BACK;
            DEFERRED.clear();
        }
    }

    /** "Move time selection one step later" with a key held: the held keys extend. */
    @Hook(target = bjW.class, method = "Gef(Lbjv;LdWa;LdWc;)V", at = At.ENTRY, id = "note-step-forward")
    public static void beforeStepForward(@This bjW input) {
        if (input.Anf()) {
            mode = Mode.EXTEND;
        }
    }

    /** A key is released. */
    @Hook(target = bjW.class, method = "Gef(I)V", at = At.ENTRY, id = "release-after-arrow")
    public static void beforeRelease(@This bjW input, @Arg(0) int key) {
        try {
            Map<?, ?> held = input.Anf; // keys held down, by key number
            if (!held.containsKey(key)) {
                return;
            }
            boolean last = held.size() == 1;
            if (mode == Mode.NONE) { // Bitwig's own path
                return;
            }
            if (mode == Mode.BACK) { // stepping back erased: Bitwig's own release writes nothing
                if (last) {
                    reset();
                }
                return;
            }
            long now = System.nanoTime();
            if (!last) {
                // Wait for the others: it is written, with the advance, when the last key is released.
                DEFERRED.put(key, now);
                input.beI(true); // collecting mode, so Bitwig does not write this key now
                return;
            }
            // The last key. Keys released a while ago keep the length they had; keys released with it
            // (and keys pressed after the arrow, which have no note yet) are written with the advance.
            for (Map.Entry<Integer, Long> released : DEFERRED.entrySet()) {
                int k = released.getKey();
                bjZ note = pending(input, k);
                if (note != null) {
                    if (now - released.getValue() > CHORD_NANOS) {
                        input.beI(note); // write it now, as it is
                    }
                } else if (input.byf.get(k) instanceof bjY info) { // keys released since the group started
                    input.beI(info, input.sCA()); // a pending note at the cursor; sCA(): the cursor
                }
            }
            reset();
            input.beI(true); // collecting mode: Bitwig advances, stretches and writes
        } catch (Throwable t) {
            reset();
            MOD.log("release handling failed", t);
        }
    }

    /** Bitwig starts a pending note for a key that already has one: keep the one it has. */
    @Hook(target = bjW.class, method = "beI(LbjY;D)LbjZ;", at = At.REPLACE_CALL, id = "no-duplicate-pending",
          callOwner = List.class, call = "add", occurrence = 1)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean addPending(List pending, Object note) {
        if (note instanceof bjZ added) {
            for (Object o : pending) {
                if (o instanceof bjZ existing && existing.beI() == added.beI()) {
                    return true;
                }
            }
        }
        return pending.add(note);
    }
}

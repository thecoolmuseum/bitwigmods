package io.github.ada.stepinput;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * Bitwig's step input finishes a group of keys when the last one is released: the cursor advances one
 * step, every pending note is stretched to it and written. But (1) after "move time selection one step
 * later" with a key held, Bitwig skips that advance, and (2) a key released more than a chord time
 * (120 ms) before the last one is dropped.
 *
 * This mod decides by what was pressed, not by timing. A key released while others are still down ends
 * where the advance from the cursor at its release would end it. If the cursor has not moved since, it is
 * written with the last key; if it has, it is written then, at that length.
 */
public final class StepInputHooks {
    static final Bml.Mod MOD = Bml.mod("step-input-arrow-advance");

    private enum Mode { NONE, EXTEND, BACK }

    /** What the arrows did while keys were held: step forward extends, step back erases. */
    private static Mode mode = Mode.NONE;

    /** Keys released while others were still held, with the cursor at that moment. */
    private static final Map<Integer, Double> DEFERRED = new LinkedHashMap<>();

    /** Keys this mod has just written itself: Bitwig must not start them again in the same release. */
    private static final Set<Integer> WRITTEN = new HashSet<>();

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

    /** Where one advance from this cursor position ends: the next grid line (as Bitwig's own advance). */
    private static double advanced(bjW input, double cursor) {
        return input.ZRB().VNw().unz(cursor + 0.001); // ZRB(): the editor; VNw(): its grid; unz: next line
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
            if (mode == Mode.BACK) { // stepping back erased: Bitwig's own release writes nothing
                if (last) {
                    reset();
                }
                return;
            }
            double cursor = input.sCA(); // sCA(): the step cursor
            if (!last) {
                DEFERRED.put(key, cursor);
                if (mode == Mode.EXTEND) {
                    input.beI(true); // collecting mode, so Bitwig does not write this key now
                }
                return;
            }
            // The last key: settle the keys released before it.
            for (Map.Entry<Integer, Double> released : DEFERRED.entrySet()) {
                int k = released.getKey();
                double at = released.getValue();
                bjZ note = pending(input, k);
                if (Math.abs(at - cursor) < 1e-9) { // the cursor has not moved since: it joins the advance
                    if (note == null && input.byf.get(k) instanceof bjY info) {
                        input.beI(info, cursor); // a pending note at the cursor
                    }
                } else { // the cursor moved on: this key ended where its own advance would have
                    if (note == null && input.byf.get(k) instanceof bjY info) {
                        note = input.beI(info, at);
                    }
                    if (note != null) {
                        note.Gef(advanced(input, at) - note.sCA()); // Gef(d): set its length; sCA(): its start
                        input.beI(note); // write it now
                        WRITTEN.add(k);
                    }
                }
            }
            reset();
            input.beI(true); // collecting mode: Bitwig advances, stretches and writes
        } catch (Throwable t) {
            reset();
            MOD.log("release handling failed", t);
        }
    }

    /** The release is done: the keys written above no longer need to be kept out. */
    @Hook(target = bjW.class, method = "Gef(I)V", at = At.BEFORE_RETURN, allReturns = true, id = "release-done")
    public static void afterRelease(@This bjW input) {
        WRITTEN.clear();
    }

    /** Bitwig starts a pending note for a key that already has one, or that was just written: keep as is. */
    @Hook(target = bjW.class, method = "beI(LbjY;D)LbjZ;", at = At.REPLACE_CALL, id = "no-duplicate-pending",
          callOwner = List.class, call = "add", occurrence = 1)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean addPending(List pending, Object note) {
        if (note instanceof bjZ added) {
            if (WRITTEN.contains(added.beI())) {
                return true;
            }
            for (Object o : pending) {
                if (o instanceof bjZ existing && existing.beI() == added.beI()) {
                    return true;
                }
            }
        }
        return pending.add(note);
    }
}

package io.github.ada.stepinput;

import java.util.HashSet;
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
 * Bitwig's step input has two ways to finish when a key is released. A plain tap (or chord) takes the
 * "collecting" path: the cursor advances one step, every pending note is stretched to the cursor and
 * written. After "move time selection one step later" with a key held, Bitwig leaves collecting mode, and
 * the release only writes that key's note: no advance, and a key pressed after the arrow gets no note.
 * This mod sends the release of the last held key down the collecting path in that case too.
 */
public final class StepInputHooks {
    static final Bml.Mod MOD = Bml.mod("step-input-arrow-advance");

    /** Keys released while others were still held, in a mode where Bitwig would not write them. */
    private static final Set<Integer> DEFERRED = new HashSet<>();

    private StepInputHooks() {
    }

    /** Whether step input already holds a pending note for this key. */
    private static boolean hasPending(bjW input, int key) {
        for (Object o : input.unz) { // the pending notes
            if (o instanceof bjZ note && note.beI() == key) { // beI(): its key
                return true;
            }
        }
        return false;
    }

    /** A key is released: after an arrow press, route the last release through the normal advance. */
    @Hook(target = bjW.class, method = "Gef(I)V", at = At.ENTRY, id = "release-after-arrow")
    public static void beforeRelease(@This bjW input, @Arg(0) int key) {
        try {
            if (input.byf()) { // collecting mode: Bitwig's own path
                DEFERRED.clear();
                return;
            }
            Map<?, ?> held = input.Anf; // keys held down, by key number
            if (!held.containsKey(key)) {
                return;
            }
            if (held.size() > 1) { // others are still down
                if (!hasPending(input, key)) {
                    DEFERRED.add(key); // written with the others, at the last release
                }
                return;
            }
            // The last key: start notes for keys released earlier, then let Bitwig collect, advance and write.
            for (int k : DEFERRED) {
                Object released = input.byf.get(k); // keys released since the group started
                if (released instanceof bjY info && !hasPending(input, k)) {
                    input.beI(info, input.sCA()); // a pending note at the cursor; sCA(): the cursor
                }
            }
            DEFERRED.clear();
            input.beI(true); // back to collecting mode
        } catch (Throwable t) {
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

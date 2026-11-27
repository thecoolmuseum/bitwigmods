package io.github.ada.listeditor;

import bitwig.obf.aHd; // the piano roll: the note editor of the Detail Editor
import bitwig.obf.Hub; // a short MIDI message (status and two data bytes, packed)
import bitwig.obf.P2Z; // the thread that reads incoming MIDI from the audio engine
import bitwig.obf.ohx; // the receivers of one MIDI input port
import bitwig.obf.qPh; // a MIDI message
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;

/**
 * The list editor's two ways of listening to Bitwig, neither of which changes what Bitwig does: the notes
 * played on a MIDI keyboard, and the clip the Detail Editor is given to show.
 */
public final class ListEditorHooks {
    private ListEditorHooks() {
    }

    /** Every short message from a MIDI input port, as Bitwig's reader hands it to the port's receivers. */
    @Hook(target = P2Z.class, method = "Anf()V", at = At.REPLACE_CALL, callOwner = ohx.class, call = "beI(LqPh;)V",
          occurrence = 1, id = "midi-in")
    public static void midiIn(ohx port, qPh message) {
        try {
            if (message instanceof Hub shortMessage) {
                ListEditorWidget.midi(shortMessage.beI); // the packed bytes
            }
        } catch (Throwable ignored) {
            // listening never stands in the way of the MIDI
        }
        port.beI(message);
    }

    /**
     * The piano roll is given the clips to show (the arranger's or the launcher's selection) and the one to
     * look at. Runs on Bitwig's UI thread, before Bitwig's own method.
     */
    @Hook(target = aHd.class, method = "beI(Ljava/util/List;LBVS;LbjH;)V", at = At.ENTRY, id = "editor-clip")
    public static void editorClip(@Arg(1) Object clip) {
        try {
            ListEditorWidget.editorShows(clip);
        } catch (Throwable ignored) {
            // listening never stands in the way of Bitwig
        }
    }
}

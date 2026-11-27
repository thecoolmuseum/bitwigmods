package io.github.ada.listeditor;

import bitwig.obf.aNi; // a Detail Editor: the piano roll and the clip launcher's editor are both one
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
     * A Detail Editor (the piano roll, or the clip launcher's editor: both are an aNi) is given the clips to
     * show (the arranger's or the launcher's selection) and the one to look at. Runs on Bitwig's UI thread,
     * before Bitwig's own method. This happens only while a Detail Editor is open.
     */
    @Hook(target = aNi.class, method = "beI(Ljava/util/List;LBVS;ZLbke;)V", at = At.ENTRY, id = "editor-clip")
    public static void editorClip(@Arg(1) Object clip) {
        try {
            ListEditorWidget.editorShows(clip, "detail editor");
        } catch (Throwable ignored) {
            // listening never stands in the way of Bitwig
        }
    }
}

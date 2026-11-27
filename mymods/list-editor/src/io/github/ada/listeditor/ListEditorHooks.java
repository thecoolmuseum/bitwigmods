package io.github.ada.listeditor;

import bitwig.obf.aHd;
import bitwig.obf.baN; // the Detail Editor panel: the arranger and the launcher hand it clips // the piano roll: the note editor of the Detail Editor
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
            ListEditorWidget.editorShows(clip, "piano roll");
        } catch (Throwable ignored) {
            // listening never stands in the way of Bitwig
        }
    }

    // The arranger and the launcher hand clips to the Detail Editor panel (a click, a new clip, a double-click).
    // These four hooks listen where it receives them, ahead of the piano roll, which is only reached while the
    // Detail Editor is open.

    @Hook(target = baN.class, method = "beI(Ljava/util/List;LBVS;DLbjH;Z)V", at = At.ENTRY, id = "panel-clip-arranger")
    public static void panelClipArranger(@Arg(1) Object clip) {
        handed(clip, "arranger");
    }

    @Hook(target = baN.class, method = "beI(Ljava/util/List;LBVS;LbkI;)V", at = At.ENTRY, id = "panel-clip-launcher")
    public static void panelClipLauncher(@Arg(1) Object clip) {
        handed(clip, "launcher");
    }

    @Hook(target = baN.class, method = "beI(Ljava/util/List;LBVS;LbjH;Lo3S;)V", at = At.ENTRY, id = "panel-clip-arranger-mode")
    public static void panelClipArrangerMode(@Arg(1) Object clip) {
        handed(clip, "arranger 2");
    }

    @Hook(target = baN.class, method = "beI(Ljava/util/List;LBVS;LbkI;Lo3S;)V", at = At.ENTRY, id = "panel-clip-launcher-mode")
    public static void panelClipLauncherMode(@Arg(1) Object clip) {
        handed(clip, "launcher 2");
    }

    private static void handed(Object clip, String source) {
        try {
            ListEditorWidget.editorShows(clip, source);
        } catch (Throwable ignored) {
            // listening never stands in the way of Bitwig
        }
    }
}

package io.github.ada.listeditor;

import bitwig.obf.DrA; // a note clip
import bitwig.obf.aCZ; // a window's project view
import bitwig.obf.aHd; // the piano roll: the note editor of the Detail Editor
import bitwig.obf.baN; // the Detail Editor panel
import bitwig.obf.bke; // the Detail Editor's controller: the layers it shows
import bitwig.obf.bpF; // a clip's note layer in the editor
import bitwig.obf.bpn; // a layer of the editor

/**
 * The clip the Detail Editor shows now, read from the editor's own state (not from the call that hands it a
 * clip), so that it is right however the clip was chosen: in the arranger or in the clip launcher.
 */
final class ClipSource {
    private ClipSource() {
    }

    /** Bitwig's note clip that the Detail Editor shows now, or null when it shows none. Call on the UI thread. */
    static Object current(Object window) {
        try {
            baN panel = ((aCZ) window).xpL(); // the Detail Editor of the view showing now
            if (panel == null) {
                return null;
            }
            aHd pianoRoll = panel.MvA();
            bke editor = pianoRoll == null ? null : pianoRoll.yX3();
            if (editor == null) {
                return null;
            }
            bpn focused = editor.GwM(); // the layer with the focus
            if (focused instanceof bpF lane && lane.mW_() instanceof DrA clip) {
                return clip;
            }
            bpF only = null; // otherwise the one clip the editor shows
            for (Object layer : editor.jTT()) {
                if (!(layer instanceof bpF lane)) {
                    continue;
                }
                if (only != null && only.mW_() != lane.mW_()) {
                    return null; // more than one clip: none is meant
                }
                only = lane;
            }
            return only != null && only.mW_() instanceof DrA clip ? clip : null;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}

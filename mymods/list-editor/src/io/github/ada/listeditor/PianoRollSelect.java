package io.github.ada.listeditor;

import java.util.Collection;
import java.util.List;

import bitwig.obf.Pb; // a clip
import bitwig.obf.Pws; // a clip in the document: it holds its editor's selection commands
import bitwig.obf.TKQ; // a selected note in the note editor
import bitwig.obf.YLc; // the selection commands of a note editor
import bitwig.obf.c1N; // a note
import bitwig.obf.ikn; // what selects an item of a given kind
import bml.pianoroll.Note;
import bml.pianoroll.internal.NoteEditors;

/** Selects a note in the piano roll's note editor, as clicking it there does. */
final class PianoRollSelect {
    private PianoRollSelect() {
    }

    /**
     * Makes {@code target} the only selected note of the note editor that has notes selected. Returns null when
     * it worked, or why it did not: the note editor is found by a note that is selected in it already.
     */
    static String select(Note target) {
        List<?> items;
        try {
            items = NoteEditors.items(null); // the selected items of the one note editor with notes selected
        } catch (RuntimeException e) {
            return "select a note in the piano roll to link it";
        }
        TKQ source = null;
        for (Object item : items) {
            if (item instanceof TKQ selected && selected.beI() instanceof c1N) {
                source = selected;
                break;
            }
        }
        if (source == null || !(target.bitwigNote() instanceof c1N event)) {
            return "select a note in the piano roll to link it";
        }
        Pws editorClip = source.ccv(); // the item's place: the clip that holds its editor's commands
        YLc commands = editorClip.XUO();
        Pb owner = source.UIe(); // the clip the note is in
        if (commands == null || owner == null) {
            return "the piano roll's selection can't be reached";
        }
        TKQ added = TKQ.beI(event, owner, source.ymo(), editorClip); // the same place and editor
        ikn handler = commands.byf(added, false);
        if (handler == null || !handler.sCA(commands, added)) {
            return "Bitwig won't select that note";
        }
        Collection<Object> only = List.of(added);
        commands.beI(only); // select it, and nothing else
        commands.iEE(true); // and show it
        return null;
    }
}

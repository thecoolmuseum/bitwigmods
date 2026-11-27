package io.github.ada.listeditor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import bml.pianoroll.Clip;
import bml.pianoroll.Listening;
import bml.pianoroll.Note;
import bml.pianoroll.NoteSelection;
import bml.pianoroll.PianoRoll;

/** The notes of one clip, in time order, kept up to date from Framework: Piano Roll. */
final class NoteList {
    /** Ticks in a beat, for showing times and lengths. */
    static final int TICKS = 960;

    private final Runnable changed;
    private Listening onSelection;
    private Listening onChange;
    private Clip clip;
    private List<Note> notes = List.of();
    private int cursorAfterLoad = -1; // the row of the first selected note, after a selection change

    NoteList(Runnable changed) {
        this.changed = changed;
    }

    void start() {
        onSelection = PianoRoll.onSelectionChange(this::selectionChanged);
        onChange = PianoRoll.onChange(this::reload);
        try {
            selectionChanged(PianoRoll.selection(null));
        } catch (RuntimeException e) {
            // no editor with a selection yet: the list stays empty until one is selected
        }
    }

    void stop() {
        if (onSelection != null) {
            onSelection.stop();
        }
        if (onChange != null) {
            onChange.stop();
        }
    }

    List<Note> notes() {
        return notes;
    }

    /** The row of the first selected note after the last selection change, or -1. */
    int takeSelectedRow() {
        int row = cursorAfterLoad;
        cursorAfterLoad = -1;
        return row;
    }

    private void selectionChanged(NoteSelection selection) {
        try {
            List<Note> selected = selection.notes();
            if (selected.isEmpty()) {
                return;
            }
            Clip next = selected.get(0).clip();
            if (next != null) {
                clip = next;
            }
            load(selected.get(0));
        } catch (RuntimeException e) {
            ListEditorPanels.MOD.log("could not read the selection", e);
        }
    }

    private void reload() {
        try {
            load(null);
        } catch (RuntimeException e) {
            ListEditorPanels.MOD.log("could not read the clip", e);
        }
    }

    private void load(Note focus) {
        if (clip == null) {
            return;
        }
        List<Note> all = new ArrayList<>(clip.notesBetween(-1, 1.0e7));
        all.sort(Comparator.comparingDouble(Note::start).thenComparingInt(Note::key));
        notes = all;
        cursorAfterLoad = focus == null ? -1 : all.indexOf(focus);
        changed.run();
    }

    // ---------- Showing values ----------

    /** Bar.beat.tick, all from 1 for bars and beats and 0 for ticks, in 4/4. */
    static String time(double beats) {
        long ticks = Math.round(beats * TICKS);
        long perBar = 4L * TICKS;
        long bar = Math.floorDiv(ticks, perBar) + 1;
        long beat = Math.floorMod(ticks, perBar) / TICKS + 1;
        long tick = Math.floorMod(ticks, (long) TICKS);
        return String.format("%03d.%d.%03d", bar, beat, tick);
    }

    private static final String[] NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    /** A key as Bitwig names it: 60 is C3. */
    static String noteName(int key) {
        return NAMES[Math.floorMod(key, 12)] + (Math.floorDiv(key, 12) - 2);
    }

    static String gate(Note note) {
        return Long.toString(Math.round((note.end() - note.start()) * TICKS));
    }

    static String velocity(Note note) {
        return Long.toString(Math.round(note.velocity() * 127));
    }
}

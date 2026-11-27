package io.github.ada.listeditor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import bml.pianoroll.Clip;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.PianoRoll;

/**
 * Step input's writing, after QueX's STEP REC. A key played starts its note at the input position. While keys are
 * held, REST (or TIE) is a tie: the input position moves on by a step and the held notes grow by it. A key
 * released fixes its length: the steps passed since it began, plus the step times the gate. When the last key
 * is released the chord is written as one Undo step and the input position moves on by a step. With no key held,
 * REST moves on, leaving a gap; TIE lengthens the notes of the last chord by a step and moves on.
 *
 * Everything runs on Bitwig's UI thread.
 */
final class StepWriter {
    private static final String UNDO = "List Editor: Step Input";

    /** A key held down or released, with the note it will make. */
    private static final class Played {
        final int key;
        final double start;
        final double velocity; // 0 to 1
        double end; // set when it is released
        boolean released;

        Played(int key, double start, double velocity) {
            this.key = key;
            this.start = start;
            this.velocity = velocity;
        }
    }

    /** What one step of input did, to take it back with Erase. */
    private static final class Done {
        final double before; // the input position before it
        final List<double[]> added; // {key, start} of the notes it wrote
        final List<double[]> extended; // {key, start, oldEnd} of the notes it lengthened
        final List<double[]> lastBefore; // what "the last chord" was before it

        Done(double before, List<double[]> added, List<double[]> extended, List<double[]> lastBefore) {
            this.before = before;
            this.added = added;
            this.extended = extended;
            this.lastBefore = lastBefore;
        }
    }

    private static final int HISTORY = 200;

    private final StepInput step;
    private final NoteList list;
    private final Consumer<String> report; // tells the user what happened, and repaints
    private final Map<Integer, Played> chord = new LinkedHashMap<>();
    private int down; // keys of the chord not released yet
    private final List<double[]> last = new ArrayList<>(); // {key, start, end} of the notes written last
    private final ArrayDeque<Done> history = new ArrayDeque<>();
    private double chordStart; // the input position when the first key of the chord was played

    StepWriter(StepInput step, NoteList list, Consumer<String> report) {
        this.step = step;
        this.list = list;
        this.report = report;
    }

    /** Drops what is pending (REC was turned off). */
    void reset() {
        chord.clear();
        down = 0;
        last.clear();
        history.clear();
    }

    private void remember(Done done) {
        history.push(done);
        while (history.size() > HISTORY) {
            history.removeLast();
        }
    }

    private List<double[]> copyOfLast() {
        List<double[]> copy = new ArrayList<>();
        for (double[] each : last) {
            copy.add(each.clone());
        }
        return copy;
    }

    /** A key was played. {@code velocity} is 1 to 127. */
    void noteOn(int key, int velocity) {
        if (list.clip() == null) {
            report.accept("no clip to write to: open one in the Detail Editor");
            return;
        }
        if (chord.isEmpty()) {
            chordStart = step.position();
        }
        Played before = chord.get(key);
        if (before != null && !before.released) {
            down--; // played again without a release
        }
        double v = step.velocityFromKey() ? velocity / 127.0 : step.velocity() / 127.0;
        chord.put(key, new Played(key, step.position(), v));
        down++;
        report.accept("");
    }

    /** A key was released. */
    void noteOff(int key) {
        Played played = chord.get(key);
        if (played == null || played.released) {
            return;
        }
        played.released = true;
        played.end = step.position() + step.gateBeats(); // the steps passed so far, and the gate of this one
        down--;
        if (down <= 0) {
            commit();
        }
    }

    /** REST: with keys held a tie, otherwise a gap of one step. */
    void rest() {
        if (down > 0) {
            tieHeld();
        } else {
            remember(new Done(step.position(), List.of(), List.of(), copyOfLast()));
            step.advance(step.stepBeats());
            report.accept("rest");
        }
    }

    /** TIE: with keys held, the same; otherwise the last chord lasts a step longer. */
    void tie() {
        if (down > 0) {
            tieHeld();
            return;
        }
        if (last.isEmpty() || list.clip() == null) {
            remember(new Done(step.position(), List.of(), List.of(), copyOfLast()));
            step.advance(step.stepBeats());
            report.accept("rest (nothing to tie)");
            return;
        }
        try {
            Edit edit = PianoRoll.edit(UNDO);
            List<Note> notes = list.notes();
            List<double[]> lastBefore = copyOfLast();
            List<double[]> extended = new ArrayList<>();
            for (double[] each : last) {
                for (Note note : notes) {
                    if (note.key() == (int) each[0] && Math.abs(note.start() - each[1]) < 1e-6) {
                        extended.add(new double[] {each[0], each[1], each[2]});
                        each[2] += step.stepBeats(); // a whole step: the gate counts only for the last one
                        edit.setEnd(note, each[2]);
                    }
                }
            }
            edit.apply();
            remember(new Done(step.position(), List.of(), extended, lastBefore));
            step.advance(step.stepBeats());
            list.reload(null);
            report.accept("tie");
        } catch (RuntimeException e) {
            report.accept(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private void tieHeld() {
        step.advance(step.stepBeats());
        report.accept("tie");
    }

    /** The last key was released: write the chord, as one Undo step, and move on. */
    private void commit() {
        Clip clip = list.clip();
        if (clip == null || chord.isEmpty()) {
            reset();
            return;
        }
        try {
            Edit edit = PianoRoll.edit(UNDO);
            List<double[]> written = new ArrayList<>();
            List<double[]> added = new ArrayList<>();
            for (Played played : chord.values()) {
                double length = Math.max(1.0 / NoteList.TICKS, played.end - played.start);
                edit.add(clip, 0, played.key, played.start, length, played.velocity, null);
                written.add(new double[] {played.key, played.start, played.start + length});
                added.add(new double[] {played.key, played.start});
            }
            edit.apply();
            remember(new Done(chordStart, added, List.of(), copyOfLast()));
            last.clear();
            last.addAll(written);
            step.advance(step.stepBeats()); // on to the next step
            list.reload(null);
            report.accept("");
        } catch (RuntimeException e) {
            report.accept(e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            chord.clear();
            down = 0;
        }
    }

    /** Erase: takes back the last step of input (or, with keys held, the chord being played) and goes back to it. */
    void erase() {
        if (down > 0 || !chord.isEmpty()) {
            step.setPosition(chordStart); // the chord in the making is dropped
            chord.clear();
            down = 0;
            report.accept("erased the chord being played");
            return;
        }
        Done done = history.poll();
        if (done == null) {
            report.accept("nothing to erase");
            return;
        }
        try {
            if (!done.added.isEmpty() || !done.extended.isEmpty()) {
                Edit edit = PianoRoll.edit("List Editor: Erase Step");
                List<Note> notes = list.notes();
                for (double[] each : done.added) {
                    for (Note note : notes) {
                        if (note.key() == (int) each[0] && Math.abs(note.start() - each[1]) < 1e-6) {
                            edit.remove(note);
                        }
                    }
                }
                for (double[] each : done.extended) {
                    for (Note note : notes) {
                        if (note.key() == (int) each[0] && Math.abs(note.start() - each[1]) < 1e-6) {
                            edit.setEnd(note, each[2]); // back to the length it had
                        }
                    }
                }
                edit.apply();
            }
            last.clear();
            last.addAll(done.lastBefore);
            step.setPosition(done.before);
            list.reload(null);
            report.accept("erased");
        } catch (RuntimeException e) {
            report.accept(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}

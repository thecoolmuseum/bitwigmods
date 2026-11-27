package io.github.ada.listeditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import bitwig.obf.dLf; // a mouse press, as a gesture receives it
import bitwig.obf.doe; // a callback before each display frame
import bitwig.obf.dre; // a display frame
import bitwig.obf.drf; // Bitwig's UI event loop
import bitwig.obf.dsB; // a subscription to the display frames
import bitwig.obf.dtt; // a mouse gesture (press, drag, release)
import bitwig.obf.dqZ; // whether a widget has the keyboard focus
import bitwig.obf.dwF; // a widget
import bitwig.obf.eqq; // a keyboard event, pressed or released
import bitwig.obf.eqr; // a key pressed
import bitwig.obf.eqs; // a key released
import bitwig.obf.eqv; // a set of held modifier keys
import bitwig.obf.eqw; // a mouse button
import bitwig.obf.SZB; // a colour
import bml.panels.ViewContext;
import bml.pianoroll.Edit;
import bml.pianoroll.Note;
import bml.pianoroll.PianoRoll;
import com.bitwig.base.geom.uXa; // a widget size
import com.bitwig.graphics.ijY; // Bitwig's drawing surface

/**
 * The notes of the current clip as a table (Time, Note, Gate, Vel) edited from the keyboard.
 *
 * Arrow keys move the cursor. Typing starts an entry in the cell under the cursor; Tab or Enter ends it
 * (Tab goes on to the cell on the right, Enter to the cell below), Esc drops it. With no entry open,
 * Alt+Up/Down changes the value by one and Shift+Up/Down by a big step (an octave for notes, 10 otherwise).
 * Delete removes the note, Insert adds a copy after it. Every change is one Undo step.
 * The clip is the one of the last note selection in the piano roll.
 */
final class ListEditorWidget extends dwF {
    private static final String[] COLUMNS = {"TIME", "NOTE", "STEP", "GATE", "VEL"};
    private static final int TIME = 0, NOTE = 1, STEP = 2, GATE = 3, VEL = 4;
    private static final double[] COLUMN_X = {8, 78, 142, 192, 242}; // layout units: narrow, for a side panel
    private static final double ROW = 18;
    private static final long MIN_WIDTH = 280; // layout units: the VEL column ends near 275
    private static final double STATUS = 18; // the step input line above the column headings
    private static final double HEADER = 30 + STATUS;
    private static final double FOOTER = 42; // room under the rows for the position, the message and the guide

    /** The widgets on screen, for the notes played on a MIDI keyboard to find the one with the focus. */
    private static final Set<ListEditorWidget> WIDGETS = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));

    private ViewContext context; // what the framework built this for
    private Object lastClip; // the clip the Detail Editor showed at the last frame
    private dsB frames; // the subscription to the display frames
    private boolean subscribing;

    /** Before each display frame: follows the clip the Detail Editor shows now. */
    private final doe tick = new doe() {
        @Override
        public void aboutToPaint(dre frame) {
            onFrame();
        }
    };

    private final NoteList list = new NoteList(this::repaint);
    private int row;
    private int column;
    private int top; // the first row shown
    private StringBuilder entry; // what is being typed into the cell, null when nothing is
    private final StepInput step = new StepInput();
    private final StepWriter writer = new StepWriter(step, list, this::told);
    private String message = "click the panel to focus";
    private int octave; // the virtual keyboard's octave shift, -5 to 5
    private final java.util.Map<String, Integer> virtualDown = new java.util.HashMap<>(); // keys held: place -> note

    private ListEditorWidget() {
    }

    static ListEditorWidget create(ViewContext context) {
        ListEditorWidget widget = new ListEditorWidget();
        widget.context = context;
        widget.I_(); // Bitwig's initialisation
        widget.list.start();
        WIDGETS.add(widget);
        return widget;
    }

    /** Whether it is on screen: attached to a window, and its view shown there. */
    private boolean shown() {
        return TUb() && context.shown(); // TUb(): attached to a window
    }

    private void startFrames() {
        if (frames != null || subscribing) {
            return;
        }
        subscribing = true;
        drf.EO().asyncExec(() -> { // after the current paint
            subscribing = false;
            if (frames == null && shown()) {
                frames = dsB.beI(tick, this); // this window's display frames
            }
        });
    }

    private void stopFrames() {
        Sound.allOff(); // no frame would end a preview any more
        if (frames != null) {
            frames.beI(); // unsubscribes
            frames = null;
        }
    }

    private void onFrame() {
        Sound.tick(); // ends the previews whose time is up
        try {
            if (!shown()) {
                stopFrames();
                return;
            }
            Object clip = ClipSource.current(context.window());
            if (clip != null && clip != lastClip) { // the Detail Editor shows another clip
                lastClip = clip;
                list.follow(PianoRoll.clip(clip));
            }
        } catch (Throwable failure) {
            stopFrames();
            ListEditorPanels.MOD.log("could not follow the clip", failure);
        }
    }

    /** Whether this widget has the keyboard focus. */
    private boolean focused() {
        return CdC != dqZ.beI; // CdC: its focus state; dqZ.beI: not focused
    }

    /** A message from a MIDI input, on Bitwig's MIDI thread: a note played or released goes to the widget with the focus. */
    static void midi(int packed) {
        int status = packed & 0xf0;
        int key = (packed >>> 8) & 0x7f;
        int velocity = (packed >>> 16) & 0x7f;
        boolean on = status == 0x90 && velocity > 0;
        boolean off = status == 0x80 || (status == 0x90 && velocity == 0);
        if (!on && !off) {
            return;
        }
        List<ListEditorWidget> widgets;
        synchronized (WIDGETS) {
            widgets = new ArrayList<>(WIDGETS);
        }
        for (ListEditorWidget widget : widgets) {
            if (widget.focused()) {
                PianoRoll.later(() -> widget.midiNote(key, velocity, on)); // on Bitwig's UI thread, in order
            }
        }
    }

    /** A note played ({@code on}) or released on a MIDI keyboard: step input while REC is on, else a cell's note. */
    private void midiNote(int key, int velocity, boolean on) {
        try {
            if (step.rec()) {
                if (on) {
                    writer.noteOn(key, velocity);
                } else {
                    writer.noteOff(key);
                }
            } else if (on) {
                played(key);
            }
        } catch (RuntimeException e) {
            told(reason(e));
        }
    }

    /** Step input says what happened: shown at the bottom, and the piano roll follows the input position. */
    private void told(String text) {
        message = text.isEmpty() ? "input at " + NoteList.time(step.position()) : text + "   input at " + NoteList.time(step.position());
        PianoRollView.reveal(this, step.position(), step.position());
        OCx();
    }

    /**
     * A key played on a MIDI keyboard, with the panel focused and the cursor in the Note cell of a row: that
     * is the row's note. Then on to the next row, for the next note. Anywhere else the key is ignored.
     */
    private void played(int key) {
        Note note = current();
        if (note == null || column != NOTE) {
            return;
        }
        try {
            entry = null;
            change(note, edit -> edit.setKey(note, key));
            moveRow(1);
            showInPianoRoll();
        } catch (RuntimeException e) {
            message = reason(e);
        }
        OCx(); // repaint
    }

    /** The Detail Editor was given a clip, on Bitwig's UI thread: every list follows it. */
    static void editorShows(Object bitwigClip, String source) {
        bml.pianoroll.Clip clip = PianoRoll.clip(bitwigClip); // null when it is not a note clip
        List<ListEditorWidget> widgets;
        synchronized (WIDGETS) {
            widgets = new ArrayList<>(WIDGETS);
        }
        for (ListEditorWidget widget : widgets) {
            PianoRoll.later(() -> widget.list.follow(clip));
        }
    }

    private void repaint() {
        if (list.takeClipChanged()) {
            row = 0;
            top = 0;
            entry = null;
        }
        int selected = list.takeSelectedRow();
        if (selected >= 0) {
            row = selected;
        }
        row = Math.max(0, Math.min(row, list.notes().size() - 1));
        OCx(); // repaint
    }

    @Override
    public void iz_() {
        stopFrames();
        WIDGETS.remove(this);
        list.stop(); // disposed: the window closes or is rebuilt
        super.iz_();
    }

    @Override
    public uXa mM_() {
        return new uXa(wGt(MIN_WIDTH), 0L); // wide enough for every column, VEL included
    }

    @Override
    public uXa nk_() {
        return uXa.Gef(); // unbounded
    }

    @Override
    public dwF neH() {
        return this;
    }

    // ---------- Painting ----------

    @Override
    public void beI(ijY g) {
        try {
            paint(g);
        } catch (Throwable t) {
            ListEditorPanels.MOD.log("could not draw", t);
        }
    }

    private void paint(ijY g) {
        startFrames(); // (the first paint starts following the Detail Editor's clip)
        double u = wGt(1000L) / 1000.0; // layout units to pixels
        SZB text = mWG().beI(this); // the theme's text colour
        double width = rtK();
        double height = cBV();
        List<Note> notes = list.notes();
        int visible = Math.max(1, (int) ((height - (HEADER + FOOTER) * u) / (ROW * u)));
        int marker = -1; // the row the input position is in front of
        if (step.rec()) {
            marker = 0;
            while (marker < notes.size() && notes.get(marker).start() < step.position() - 1e-9) {
                marker++;
            }
            if (marker < top) {
                top = marker;
            } else if (marker > top + visible - 1) {
                top = marker - visible + 1;
            }
        } else if (row < top) {
            top = row;
        } else if (row >= top + visible) {
            top = row - visible + 1;
        }

        paintStatus(g, u, text);
        for (int c = 0; c < COLUMNS.length; c++) {
            g.beI(COLUMN_X[c] * u, (HEADER - 10) * u, COLUMNS[c], text.beI(0.6F));
        }
        g.Gef(0, (HEADER - 4) * u, width, 1); // the line under the header
        g.sCA(text.beI(0.2F));
        g.scr();

        if (notes.isEmpty()) {
            g.beI(COLUMN_X[0] * u, (HEADER + ROW) * u, "No notes: select a note clip (it follows the Detail Editor).", text.beI(0.6F));
        }
        for (int i = 0; i < visible && top + i < notes.size(); i++) {
            int r = top + i;
            Note note = notes.get(r);
            double y = (HEADER + ROW * (i + 1)) * u;
            String[] cells = cells(note, r);
            if (r == row) {
                g.Gef(0, y - (ROW - 4) * u, width, ROW * u); // the cursor's row
                g.sCA(text.beI(0.10F));
                g.scr();
                double cellWidth = column == VEL ? 34 : COLUMN_X[column + 1] - COLUMN_X[column] - 4;
                g.Gef((COLUMN_X[column] - 6) * u, y - (ROW - 4) * u, cellWidth * u, ROW * u);
                g.sCA(text.beI(entry == null ? 0.30F : 0.55F)); // the cursor's cell, brighter while typing
                g.scr();
                if (entry != null) {
                    cells[column] = entry + "_";
                }
            }
            for (int c = 0; c < cells.length; c++) {
                g.beI(COLUMN_X[c] * u, y, cells[c], text);
            }
        }
        if (marker >= 0) {
            double y = (HEADER + ROW * (marker - top)) * u + 4 * u; // the top of the row it is in front of
            g.Gef(0, y - u, width, 2 * u); // a red line where input goes
            g.sCA(SZB.beI(214, 40, 40));
            g.scr();
        }
        String position = (notes.isEmpty() ? 0 : row + 1) + " / " + notes.size();
        g.beI(COLUMN_X[0] * u, height - (step.rec() ? 30 : 6) * u, position + "   " + message, text.beI(0.5F));
        if (step.rec()) {
            g.beI(COLUMN_X[0] * u, height - 18 * u, StepInput.guide(), text.beI(0.6F)); // the keys of step input
            g.beI(COLUMN_X[0] * u, height - 6 * u, StepInput.guide2(), text.beI(0.6F));
        }
    }

    /** The REC button and the settings of step input, above the column headings. */
    private void paintStatus(ijY g, double u, SZB text) {
        double baseline = (STATUS - 4) * u;
        SZB ink = SZB.beI(22, 38, 20); // dark green characters, as the QueX LCD
        g.Gef(0, 0, rtK(), STATUS * u); // the LCD: a green backlit panel
        g.sCA(SZB.beI(111, 169, 85));
        g.scr();
        g.Gef(0, 0, rtK(), u); // its top edge, in shade
        g.sCA(SZB.beI(76, 120, 60));
        g.scr();
        if (step.rec()) {
            g.Gef(COLUMN_X[0] * u - 3 * u, 2 * u, 36 * u, (STATUS - 3) * u); // a red button
            g.sCA(SZB.beI(214, 40, 40)); // red (red, green, blue: 0-255)
            g.scr();
            g.beI(COLUMN_X[0] * u, baseline, "REC", SZB.beI(255, 255, 255));
        } else {
            g.beI(COLUMN_X[0] * u, baseline, "REC", SZB.beI(70, 105, 58)); // faint: off
        }
        g.beI((COLUMN_X[0] + 40) * u, baseline, step.status(), step.rec() ? ink : SZB.beI(70, 105, 58));
    }

    private String[] cells(Note note, int r) {
        long step = list.stepTicks(r);
        return new String[] {
            NoteList.time(note.start()), NoteList.noteName(note.key()) + " (" + note.key() + ")",
            step < 0 ? "-" : Long.toString(step), NoteList.gate(note), NoteList.velocity(note)};
    }

    // ---------- Mouse: a click takes the keyboard focus ----------

    @Override
    public dtt Gef(eqw button, double x, double y, eqv mods, int clicks) {
        if (button != eqw.beI) { // the main button
            return null;
        }
        return new Press(x, y);
    }

    /** Asks Bitwig to give this widget the keyboard focus (from here: Xo is protected in Bitwig's widget). */
    private void takeFocus() {
        Xo(); // focus this widget
    }

    private final class Press extends dtt {
        private final double x;
        private final double y;

        Press(double x, double y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public void beI(dLf press) {
            try {
                takeFocus();
                message = "";
                pressed(x, y);
                OCx();
            } catch (Throwable t) {
                ListEditorPanels.MOD.log("could not handle a click", t);
            }
        }
    }

    private void toggleRec() {
        entry = null;
        step.toggleRec();
        writer.reset(); // what was pending is dropped
        virtualDown.clear();
        Sound.allOff();
        Note note = current();
        if (step.rec() && note != null) {
            step.setPosition(note.start()); // input starts where the cursor row is
        }
        message = step.message();
    }

    /** Moves where step input goes, and shows it. */
    private void moveInput(int direction, StepInput.Unit unit) {
        step.move(direction, unit);
        showInput();
    }

    private void showInput() {
        message = "input at " + NoteList.time(step.position());
        PianoRollView.reveal(this, step.position(), step.position()); // the piano roll follows
    }

    /** A click on a cell puts the cursor there (the row and the column) and selects its note in the piano roll. */
    private void pressed(double x, double y) {
        double u = wGt(1000L) / 1000.0; // layout units to pixels, as in the painting
        if (y / u < STATUS && x / u < COLUMN_X[0] + 40) { // the REC button
            toggleRec();
            return;
        }
        int onScreen = (int) Math.floor((y / u - HEADER - 4) / ROW); // the rows start under the header
        int clicked = top + onScreen;
        if (onScreen < 0 || clicked >= list.notes().size()) {
            return; // the header, or below the last row
        }
        int cell = 0;
        for (int c = 0; c < COLUMNS.length; c++) {
            if (x / u >= COLUMN_X[c] - 6) {
                cell = c;
            }
        }
        commit(); // what was being typed goes in first
        boolean moved = clicked != row;
        row = clicked;
        column = cell;
        if (moved) {
            previewCurrent();
        }
        if (step.rec()) {
            step.setPosition(list.notes().get(clicked).start()); // input starts at that note
            message = "input at " + NoteList.time(step.position());
        }
        if (moved) {
            showInPianoRoll();
        }
    }

    // ---------- Keys ----------

    /** The id Bitwig gives the key, in lower case: "up", "enter", "a", "page_up". It prints as "down(Down)". */
    private static String keyName(eqr event) {
        String s = String.valueOf(event.BBg());
        int paren = s.indexOf('(');
        return (paren > 0 ? s.substring(0, paren) : s).toLowerCase().replace(' ', '_');
    }

    /** The semitones above C of the virtual keyboard's key (by its place on a US keyboard), or -1: Z to M white, S D G H J black. */
    private static int pitchOf(String place) {
        return switch (place) {
            case "z" -> 0;  // C
            case "s" -> 1;  // C#
            case "x" -> 2;  // D
            case "d" -> 3;  // D#
            case "c" -> 4;  // E
            case "v" -> 5;  // F
            case "g" -> 6;  // F#
            case "b" -> 7;  // G
            case "h" -> 8;  // G#
            case "n" -> 9;  // A
            case "j" -> 10; // A#
            case "m" -> 11; // B
            case "comma" -> 12; // the next C
            default -> -1;
        };
    }

    /** A key released: ends the note of the virtual keyboard it played. */
    @Override
    public boolean Gef(eqs event) {
        try {
            Integer note = virtualDown.remove(physicalName(event));
            if (note == null) {
                return false;
            }
            writer.noteOff(note);
            Sound.noteOff(note);
            OCx();
            return true;
        } catch (RuntimeException e) {
            message = "error: " + e;
            return true;
        }
    }

    /** The id of the key at the same place on a US keyboard, in lower case: the same whatever the layout. */
    private static String physicalName(eqq event) {
        String s = String.valueOf(event.scr());
        int paren = s.indexOf('(');
        return (paren > 0 ? s.substring(0, paren) : s).toLowerCase().replace(' ', '_');
    }

    /** The character a key types into a cell, or 0. */
    private static char typed(String key, boolean shift) {
        if (key.length() == 1) {
            char c = key.charAt(0);
            if (c >= '0' && c <= '9') {
                if (shift) {
                    return c == '3' ? '#' : 0; // Shift+3 is # on a US keyboard
                }
                return c;
            }
            if (c >= 'a' && c <= 'g' && !shift) {
                return c;
            }
            return 0;
        }
        if (key.startsWith("keypad_") && key.length() == 8 && Character.isDigit(key.charAt(7))) {
            return key.charAt(7);
        }
        return switch (key) {
            case "period", "keypad_decimal" -> '.';
            case "number_sign", "sharp_s", "keypad_plus", "plus" -> '#';
            case "minus", "keypad_minus" -> '-';
            default -> 0;
        };
    }

    /** Whether the cell under the cursor takes this character. */
    private boolean accepts(char c) {
        if (c == 0) {
            return false;
        }
        return switch (column) {
            case TIME -> Character.isDigit(c) || c == '.';
            case NOTE -> Character.isDigit(c) || (c >= 'a' && c <= 'g') || c == '#' || c == '-';
            default -> Character.isDigit(c);
        };
    }

    @Override
    public boolean Gef(eqr event) {
        try {
            return keyPressed(event);
        } catch (RuntimeException e) {
            message = "error: " + e;
            ListEditorPanels.MOD.log("key failed", e);
            OCx();
            return true;
        }
    }

    private boolean keyPressed(eqr event) {
        String key = keyName(event);
        String modifiers = String.valueOf(event.Heb()).toLowerCase();
        boolean shift = event.Heb().beI();
        boolean alt = modifiers.contains("alt");
        boolean ctrl = modifiers.contains("ctrl") || modifiers.contains("control");
        boolean command = modifiers.contains("cmd") || modifiers.contains("meta") || modifiers.contains("super");
        boolean inputMove = step.rec() && !step.typing() && (key.equals("up") || key.equals("down"));
        if (command || (ctrl && !inputMove)) {
            return false; // Bitwig's own shortcuts (undo, copy...); Ctrl+Up/Down move the input by a bar
        }
        if (step.rec() && !step.typing() && (key.equals("home") || key.equals("end"))) {
            List<Note> notes = list.notes();
            if (key.equals("home") || notes.isEmpty()) {
                step.moveToStart(); // the start of the clip
            } else {
                step.moveToBarAfter(notes.get(notes.size() - 1).start()); // the bar after the last note
            }
            showInput();
            OCx();
            return true;
        }
        if (inputMove) {
            StepInput.Unit unit = ctrl ? StepInput.Unit.BAR : shift ? StepInput.Unit.BEAT
                    : alt ? StepInput.Unit.TICK : StepInput.Unit.STEP;
            moveInput(key.equals("up") ? -1 : 1, unit); // up is earlier, down is later
            OCx();
            return true;
        }
        if (key.equals("w") && !shift && !alt) { // W: REC on and off, as in QueX
            toggleRec();
            OCx();
            return true;
        }
        if (step.rec() && !step.typing() && !alt) {
            String place = physicalName(event);
            int pitch = pitchOf(place);
            if (pitch >= 0) { // the virtual keyboard
                if (!event.iMb() && !virtualDown.containsKey(place)) { // not a repeat of a key held
                    int note = Math.max(0, Math.min(127, 60 + octave * 12 + pitch));
                    virtualDown.put(place, note);
                    writer.noteOn(note, 100);
                    String problem = Sound.noteOn(note, step.velocityFromKey() ? 100 / 127.0 : step.velocity() / 127.0);
                    if (problem != null) {
                        message = problem;
                    }
                }
                OCx();
                return true;
            }
            if (key.equals("a") || key.equals("l")) { // the octave of the virtual keyboard
                octave = Math.max(-5, Math.min(5, octave + (key.equals("l") ? 1 : -1)));
                message = "octave " + (octave > 0 ? "+" : "") + octave + " (C = "
                        + Math.max(0, Math.min(127, 60 + octave * 12)) + ")";
                OCx();
                return true;
            }
            if (key.equals("e")) { // erase: back to before the last step
                writer.erase();
                OCx();
                return true;
            }
        }
        if (step.rec() && !step.typing() && (key.equals("r") || key.equals("t"))) {
            if (key.equals("r")) { // REST: a tie while keys are held
                writer.rest();
            } else {
                writer.tie();
            }
            OCx();
            return true;
        }
        if (step.rec()) {
            if (step.key(key, physicalName(event))) { // ST, GT, VEL
                message = step.message();
                OCx();
                return true;
            }
            message = step.message();
        }
        if (key.equals("p") && !shift && !alt && !step.typing() && entry == null) { // P: preview sound on and off
            Sound.toggle();
            message = "preview sound " + (Sound.enabled() ? "on (" + Sound.describe() + ")" : "off");
            OCx();
            return true;
        }
        int rows = list.notes().size();
        int rowBefore = row;
        switch (key) {
            case "up", "down" -> {
                int direction = key.equals("up") ? -1 : 1;
                commit();
                if (alt || shift) {
                    nudge(-direction, shift);  // Up raises the value
                } else {
                    moveRow(direction);
                }
            }
            case "left" -> {
                commit();
                column = Math.max(0, column - 1);
            }
            case "right" -> {
                commit();
                column = Math.min(COLUMNS.length - 1, column + 1);
            }
            case "home" -> {
                commit();
                row = 0;
            }
            case "end" -> {
                commit();
                row = Math.max(0, rows - 1);
            }
            case "page_up" -> {
                commit();
                row = Math.max(0, row - 10);
            }
            case "page_down" -> {
                commit();
                row = Math.min(Math.max(0, rows - 1), row + 10);
            }
            case "tab" -> {
                commit();
                next(shift ? -1 : 1);
            }
            case "enter", "keypad_enter" -> {
                commit();
                moveRow(shift ? -1 : 1);
            }
            case "escape" -> {
                if (entry == null) {
                    return false;
                }
                entry = null;
                message = "";
            }
            case "backspace" -> {
                if (entry == null) {
                    return false;
                }
                entry.setLength(Math.max(0, entry.length() - 1));
            }
            case "delete", "keypad_delete" -> {
                if (entry != null) {
                    entry = null;
                } else {
                    deleteNote();
                }
            }
            case "insert", "keypad_insert" -> {
                commit();
                insertNote();
            }
            default -> {
                char c = typed(key, shift);
                if (step.rec() || !accepts(c) || rows == 0) {
                    return false; // while REC is on, the digits are step input's, not a cell's
                }
                if (entry == null) {
                    entry = new StringBuilder();
                }
                entry.append(c);
                message = "";
            }
        }
        if (row != rowBefore && entry == null) {
            showInPianoRoll();
            previewCurrent();
        }
        OCx(); // repaint
        return true;
    }

    /** A short sound of the note under the cursor. */
    private void previewCurrent() {
        Note note = current();
        if (note != null) {
            String problem = Sound.preview(note.key(), note.velocity());
            if (problem != null) {
                message = problem;
            }
        }
    }

    /** A short sound of a pitch just set, for the note it was set on. */
    private void previewPitch(int key, double velocity) {
        String problem = Sound.preview(key, velocity);
        if (problem != null) {
            message = problem;
        }
    }

    /** Selects the note under the cursor in the piano roll too. */
    private void showInPianoRoll() {
        Note note = current();
        if (note == null) {
            return;
        }
        try {
            String problem = PianoRollSelect.select(note);
            if (problem == null) {
                problem = PianoRollView.reveal(this, note.start(), note.end()); // scroll to it
            }
            if (problem != null) {
                message = problem;
            }
        } catch (RuntimeException e) {
            message = "could not select in the piano roll: " + reason(e);
            ListEditorPanels.MOD.log("could not select in the piano roll", e);
        }
    }

    private void moveRow(int by) {
        row = Math.max(0, Math.min(Math.max(0, list.notes().size() - 1), row + by));
    }

    /** Tab: to the next cell, and on to the next row at the end of one. */
    private void next(int by) {
        column += by;
        if (column >= COLUMNS.length) {
            column = 0;
            moveRow(1);
        } else if (column < 0) {
            column = COLUMNS.length - 1;
            moveRow(-1);
        }
    }

    // ---------- Editing ----------

    private Note current() {
        List<Note> notes = list.notes();
        return row >= 0 && row < notes.size() ? notes.get(row) : null;
    }

    private static String reason(RuntimeException e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    /** Applies what was typed, if anything. */
    private void commit() {
        if (entry == null) {
            return;
        }
        String text = entry.toString();
        entry = null;
        Note note = current();
        if (note == null || text.isEmpty()) {
            return;
        }
        try {
            switch (column) {
                case TIME -> {
                    double start = Entry.time(text, note.start());
                    change(note, edit -> edit.move(note, start));
                }
                case NOTE -> {
                    int key = Entry.key(text, note.key());
                    change(note, edit -> edit.setKey(note, key));
                    previewPitch(key, note.velocity());
                }
                case STEP -> setStep(note, Entry.ticks(text, 0));
                case GATE -> {
                    double end = note.start() + (double) Entry.ticks(text, 1) / NoteList.TICKS;
                    change(note, edit -> edit.setEnd(note, end));
                }
                default -> {
                    double velocity = Entry.velocity(text) / 127.0;
                    change(note, edit -> edit.setVelocity(note, velocity));
                }
            }
        } catch (RuntimeException e) {
            message = reason(e);
        }
    }

    /** Alt: by one. Shift: by a big step. {@code direction} is +1 to raise the value, -1 to lower it. */
    private void nudge(int direction, boolean big) {
        Note note = current();
        if (note == null) {
            return;
        }
        try {
            switch (column) {
                case TIME -> {
                    long ticks = Math.round(note.start() * NoteList.TICKS) + (long) direction * (big ? 10 : 1);
                    double start = Math.max(0, ticks) / (double) NoteList.TICKS;
                    change(note, edit -> edit.move(note, start));
                }
                case NOTE -> {
                    int key = Math.max(0, Math.min(127, note.key() + direction * (big ? 12 : 1)));
                    change(note, edit -> edit.setKey(note, key));
                    previewPitch(key, note.velocity());
                }
                case STEP -> {
                    long now = list.stepTicks(row);
                    if (now >= 0) {
                        setStep(note, Math.max(0, now + (long) direction * (big ? 10 : 1)));
                    }
                }
                case GATE -> {
                    long ticks = Math.max(1, Math.round((note.end() - note.start()) * NoteList.TICKS)
                            + (long) direction * (big ? 10 : 1));
                    double end = note.start() + ticks / (double) NoteList.TICKS;
                    change(note, edit -> edit.setEnd(note, end));
                }
                default -> {
                    long velocity = Math.max(1, Math.min(127, Math.round(note.velocity() * 127)
                            + (long) direction * (big ? 10 : 1)));
                    change(note, edit -> edit.setVelocity(note, velocity / 127.0));
                }
            }
        } catch (RuntimeException e) {
            message = reason(e);
        }
    }

    /** STEP: the notes after this one's step move with it, by how much it changed. Others keep their STEP. */
    private void setStep(Note note, long ticks) {
        long now = list.stepTicks(row);
        if (now < 0) {
            message = "last note: no STEP";
            return;
        }
        long delta = ticks - now;
        if (delta == 0) {
            return;
        }
        Edit edit = PianoRoll.edit("List Editor: Step");
        List<Note> all = list.notes();
        for (int i = row + 1; i < all.size(); i++) { // every later row
            Note each = all.get(i);
            edit.move(each, Math.max(0, each.start() + (double) delta / NoteList.TICKS));
        }
        edit.apply();
        list.reload(note);
        message = "";
    }

    /** One Undo step; the cursor follows the note to its new row. */
    private void change(Note note, Consumer<Edit> plan) {
        Edit edit = PianoRoll.edit("List Editor");
        plan.accept(edit);
        edit.apply();
        list.reload(note);
        message = "";
    }

    private void deleteNote() {
        Note note = current();
        if (note == null) {
            return;
        }
        try {
            Edit edit = PianoRoll.edit("List Editor: Delete Note");
            edit.remove(note);
            edit.apply();
            list.reload(null);
            message = "deleted";
        } catch (RuntimeException e) {
            message = reason(e);
        }
    }

    /** A copy of the current note, straight after it. */
    private void insertNote() {
        Note note = current();
        if (note == null || list.clip() == null) {
            return;
        }
        try {
            double start = note.end();
            Edit edit = PianoRoll.edit("List Editor: Insert Note");
            edit.add(list.clip(), note.channel(), note.key(), start, note.end() - note.start(), note.velocity(), null);
            edit.apply();
            list.reload(null);
            List<Note> notes = list.notes();
            for (int i = 0; i < notes.size(); i++) {
                if (Math.abs(notes.get(i).start() - start) < 1e-9 && notes.get(i).key() == note.key()) {
                    row = i;
                    break;
                }
            }
            message = "inserted";
        } catch (RuntimeException e) {
            message = reason(e);
        }
    }
}

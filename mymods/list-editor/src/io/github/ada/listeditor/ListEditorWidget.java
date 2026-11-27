package io.github.ada.listeditor;

import java.util.List;

import bitwig.obf.dLf; // a mouse press, as a gesture receives it
import bitwig.obf.dtt; // a mouse gesture (press, drag, release)
import bitwig.obf.dwF; // a widget
import bitwig.obf.eqr; // a keyboard event
import bitwig.obf.eqv; // a set of held modifier keys
import bitwig.obf.eqw; // a mouse button
import bitwig.obf.SZB; // a colour
import bml.pianoroll.Note;
import bml.panels.ViewContext;
import com.bitwig.base.geom.uXa; // a widget size
import com.bitwig.graphics.ijY; // Bitwig's drawing surface

/**
 * M2: the notes of the current clip as a table (Time, Note, Gate, Vel), with a cursor that the arrow
 * keys move. Read-only. The clip is the one of the last note selection in the piano roll.
 */
final class ListEditorWidget extends dwF {
    private static final String[] COLUMNS = {"Time", "Note", "Gate", "Vel"};
    private static final double[] COLUMN_X = {14, 130, 220, 310}; // layout units
    private static final double ROW = 18;
    private static final double HEADER = 34;

    private final NoteList list = new NoteList(this::repaint);
    private int row;
    private int column;
    private int top; // the first row shown
    private String status = "click the panel to focus";
    private int keyCount;

    private ListEditorWidget() {
    }

    static ListEditorWidget create(ViewContext context) {
        ListEditorWidget widget = new ListEditorWidget();
        widget.I_(); // Bitwig's initialisation
        widget.list.start();
        return widget;
    }

    private void repaint() {
        int selected = list.takeSelectedRow();
        if (selected >= 0) {
            row = selected;
        }
        row = Math.max(0, Math.min(row, list.notes().size() - 1));
        OCx(); // repaint
    }

    @Override
    public void iz_() {
        list.stop(); // disposed: the window closes or is rebuilt
        super.iz_();
    }

    @Override
    public uXa mM_() {
        return uXa.beI(); // zero: no minimum
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
        double u = wGt(1000L) / 1000.0; // layout units to pixels
        SZB text = mWG().beI(this); // the theme's text colour
        double width = rtK();
        double height = cBV();
        List<Note> notes = list.notes();
        int visible = Math.max(1, (int) ((height - HEADER * u) / (ROW * u)));
        if (row < top) {
            top = row;
        } else if (row >= top + visible) {
            top = row - visible + 1;
        }

        for (int c = 0; c < COLUMNS.length; c++) {
            g.beI(COLUMN_X[c] * u, 20 * u, COLUMNS[c], text.beI(0.6F));
        }
        g.Gef(0, 26 * u, width, 1); // the line under the header
        g.sCA(text.beI(0.2F));
        g.scr();

        if (notes.isEmpty()) {
            g.beI(COLUMN_X[0] * u, (HEADER + ROW) * u, "Select notes in the piano roll to list its clip.", text.beI(0.6F));
        }
        for (int i = 0; i < visible && top + i < notes.size(); i++) {
            int r = top + i;
            Note note = notes.get(r);
            double y = (HEADER + ROW * (i + 1)) * u;
            if (r == row) {
                g.Gef(0, y - (ROW - 4) * u, width, ROW * u); // the cursor's row
                g.sCA(text.beI(0.10F));
                g.scr();
                g.Gef((COLUMN_X[column] - 6) * u, y - (ROW - 4) * u, (COLUMN_X[Math.min(column + 1, 3)] - COLUMN_X[column] + (column == 3 ? 60 : 0) - 4) * u, ROW * u);
                g.sCA(text.beI(0.30F)); // the cursor's cell
                g.scr();
            }
            String[] cells = {
                NoteList.time(note.start()), NoteList.noteName(note.key()) + " (" + note.key() + ")",
                NoteList.gate(note), NoteList.velocity(note)};
            for (int c = 0; c < cells.length; c++) {
                g.beI(COLUMN_X[c] * u, y, cells[c], text);
            }
        }
        g.beI(COLUMN_X[0] * u, height - 6 * u, (notes.isEmpty() ? 0 : row + 1) + " / " + notes.size() + "   " + status, text.beI(0.5F));
    }

    // ---------- Mouse: a click takes the keyboard focus ----------

    @Override
    public dtt Gef(eqw button, double x, double y, eqv mods, int clicks) {
        if (button != eqw.beI) { // the main button
            return null;
        }
        return new Press();
    }

    /** Asks Bitwig to give this widget the keyboard focus (from here: Xo is protected in Bitwig's widget). */
    private void takeFocus() {
        Xo(); // focus this widget
        status = "focus asked";
        OCx();
    }

    private final class Press extends dtt {
        @Override
        public void beI(dLf press) {
            try {
                takeFocus();
            } catch (Throwable t) {
                ListEditorPanels.MOD.log("could not take the focus", t);
            }
        }
    }

    // ---------- Keys ----------

    /** The name Bitwig gives the key: "Up", "Enter", "A", "Numpad 4". */
    private static String keyName(eqr event) {
        String s = String.valueOf(event.BBg());
        int paren = s.indexOf('(');
        return paren > 0 ? s.substring(0, paren) : s;
    }

    @Override
    public boolean Gef(eqr event) {
        String key = keyName(event);
        keyCount++;
        status = "keys=" + keyCount + " name=[" + key + "] raw=[" + event.BBg() + "] row=" + row + " col=" + column;
        int rows = list.notes().size();
        boolean handled = true;
        switch (key) {
            case "Up" -> row = Math.max(0, row - 1);
            case "Down" -> row = Math.min(Math.max(0, rows - 1), row + 1);
            case "Left" -> column = Math.max(0, column - 1);
            case "Right" -> column = Math.min(COLUMNS.length - 1, column + 1);
            case "Home" -> row = 0;
            case "End" -> row = Math.max(0, rows - 1);
            case "Page Up" -> row = Math.max(0, row - 10);
            case "Page Down" -> row = Math.min(Math.max(0, rows - 1), row + 10);
            default -> handled = false; // not ours: Bitwig's own shortcuts (play, undo...) keep working
        }
        status += handled ? " handled" : " not-handled";
        OCx(); // repaint
        return handled;
    }
}

package io.github.ada.listeditor;

import bitwig.obf.dLf; // a mouse press, as a gesture receives it
import bitwig.obf.dtt; // a mouse gesture (press, drag, release)
import bitwig.obf.dwF; // a widget
import bitwig.obf.eqr; // a keyboard event
import bitwig.obf.eqv; // a set of held modifier keys
import bitwig.obf.eqw; // a mouse button
import bitwig.obf.SZB; // a colour
import bml.panels.ViewContext;
import com.bitwig.base.geom.uXa; // a widget size
import com.bitwig.graphics.ijY; // Bitwig's drawing surface

/** M1: a panel that draws what it receives (mouse, focus, keys), to find out how input reaches a widget. */
final class ListEditorWidget extends dwF {
    private int paints;
    private int hitTests;
    private int presses;
    private int keys;
    private String lastKey = "-";
    private String focus = "-";

    private ListEditorWidget() {
    }

    static ListEditorWidget create(ViewContext context) {
        ListEditorWidget widget = new ListEditorWidget();
        widget.I_(); // Bitwig's initialisation
        return widget;
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

    @Override
    public void beI(ijY g) {
        paints++;
        try {
            SZB text = mWG().beI(this); // the theme's text colour
            String[] lines = {
                "List Editor M1 (diagnostics)",
                "paints=" + paints + "  mouse hit-tests=" + hitTests + "  presses=" + presses + "  keys=" + keys,
                "focus: " + focus,
                "last key: " + lastKey,
                "click the panel, then press keys",
            };
            for (int i = 0; i < lines.length; i++) {
                g.beI(12, 24 + 18 * i, lines[i], text);
            }
        } catch (Throwable t) {
            ListEditorPanels.MOD.log("could not draw", t);
        }
    }

    /** A click on the panel takes the keyboard focus, so that the keys go to it. */
    @Override
    public dtt Gef(eqw button, double x, double y, eqv mods, int clicks) {
        hitTests++;
        if (button != eqw.beI) { // the main button
            return null;
        }
        return new Press();
    }

    /** Asks Bitwig to give this widget the keyboard focus (from here: Xo is protected in Bitwig's widget). */
    private String takeFocus() {
        boolean focusable = kT(); // whether it can take the focus
        Xo(); // focus this widget
        return "asked (focusable=" + focusable + ")";
    }

    private final class Press extends dtt {
        @Override
        public void beI(dLf press) {
            presses++;
            try {
                focus = takeFocus();
            } catch (Throwable t) {
                focus = "failed: " + t;
                ListEditorPanels.MOD.log("could not take the focus", t);
            }
            OCx(); // repaint
        }
    }

    @Override
    public boolean Gef(eqr event) {
        keys++;
        lastKey = String.valueOf(event);
        OCx(); // repaint
        return true;
    }
}

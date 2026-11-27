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

/** M1: a panel that draws the last key event it received. */
final class ListEditorWidget extends dwF {
    private String lastKey = "(click here, then press a key)";

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
        try {
            SZB text = mWG().beI(this); // the theme's text colour
            g.beI(12, 24, lastKey, text);
        } catch (Throwable t) {
            ListEditorPanels.MOD.log("could not draw", t);
        }
    }

    /** A click on the panel takes the keyboard focus, so that the keys go to it. */
    @Override
    public dtt Gef(eqw button, double x, double y, eqv mods, int clicks) {
        if (button != eqw.beI) { // the main button
            return null;
        }
        return new Press();
    }

    private final class Press extends dtt {
        @Override
        public void beI(dLf press) {
            try {
                Xo(); // focus this widget
                lastKey = "(focused: press a key)";
                OCx(); // repaint
            } catch (Throwable t) {
                ListEditorPanels.MOD.log("could not take the focus", t);
            }
        }
    }

    @Override
    public boolean Gef(eqr event) {
        lastKey = String.valueOf(event);
        OCx(); // repaint
        return true;
    }
}

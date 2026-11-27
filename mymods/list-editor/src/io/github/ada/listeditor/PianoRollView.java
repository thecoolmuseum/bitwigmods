package io.github.ada.listeditor;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import bitwig.obf.aNn; // a panel with a timeline: it holds the scroll position and the zoom
import bitwig.obf.dwF; // a widget

/**
 * The piano roll's view: scrolls it so that a note can be seen. A timeline panel keeps its scroll as a whole
 * number of layout units from an origin, and its zoom as the time (in beats) one unit covers; time 0 of the
 * clip is {@link #ORIGIN} units from the origin of the scroll.
 */
final class PianoRollView {
    /** The scroll position at which the left edge of the view is at time 0 of the clip. */
    private static final long ORIGIN = 1_000_000L;

    private PianoRollView() {
    }

    /** The timeline panel that is on screen with the widest view (the piano roll), or null. */
    private static aNn find(dwF from) {
        dwF top = from;
        while (top.oRH() != null) {
            top = top.oRH();
        }
        List<aNn> found = new ArrayList<>();
        collect(top, found, 0, new IdentityHashMap<>());
        aNn widest = null;
        for (aNn timeline : found) {
            if (timeline.rtK() > 0 && (widest == null || timeline.rtK() > widest.rtK())) {
                widest = timeline;
            }
        }
        return widest;
    }

    private static void collect(dwF widget, List<aNn> found, int depth, IdentityHashMap<Object, Boolean> seen) {
        if (widget == null || depth > 80 || seen.put(widget, Boolean.TRUE) != null) {
            return;
        }
        if (widget instanceof aNn timeline) {
            found.add(timeline);
        }
        List<?> children = widget.jn_();
        if (children != null) {
            for (Object child : new ArrayList<>(children)) {
                if (child instanceof dwF next) {
                    collect(next, found, depth + 1, seen);
                }
            }
        }
    }

    /** Scrolls the piano roll sideways if the note's start is out of view. Returns null, or why not. */
    static String reveal(dwF from, double start, double end) {
        aNn view = find(from);
        if (view == null) {
            return "the piano roll's view wasn't found";
        }
        double perUnit = view.sSO();
        if (!(perUnit > 0)) {
            return "the piano roll's zoom isn't known";
        }
        long width = view.rtK();
        double viewLeft = (view.VsA() - ORIGIN) * perUnit; // beats at the left edge
        double viewRight = viewLeft + width * perUnit;
        double margin = 0.08 * width * perUnit; // keep the note off the very edge
        if (start >= viewLeft + margin && start <= viewRight - 2 * margin) {
            return null; // already in view
        }
        long position = ORIGIN + Math.round(start / perUnit - 0.25 * width); // a quarter in from the left
        view.sCA(position); // sCA(long): the scroll position
        return null;
    }
}

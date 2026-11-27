package io.github.ada.listeditor;

import bml.api.Bml;
import bml.panels.PanelProvider;
import bml.panels.Panels;

/** Registers the list editor's view with Framework: Panels. */
public final class ListEditorPanels implements PanelProvider {
    static final Bml.Mod MOD = Bml.mod("list-editor");

    @Override
    public void declare(Panels panels) {
        panels.view("list_editor", "List Editor")
              .icon("list_editor_icon")
              .content(ListEditorWidget::create)
              .side("browser")      // on the browser side, not a tab of the lower panel
              .sideColumn(true);    // a full-height column of its own beside the browser side
    }
}

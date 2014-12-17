package org.lobobrowser.html.style;

import java.awt.Cursor;
import java.util.Optional;

public class CursorRenderState extends RenderStateDelegator {
  private final Optional<Cursor> cursorOpt;

  public CursorRenderState(final RenderState prevRenderState, final Optional<Cursor> cursor) {
    super(prevRenderState);
    this.cursorOpt = cursor;
  }

  @Override
  public Optional<Cursor> getCursor() {
    return this.cursorOpt;
  }
}

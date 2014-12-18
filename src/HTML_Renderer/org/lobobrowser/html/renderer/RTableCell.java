/*
    GNU LESSER GENERAL PUBLIC LICENSE
    Copyright (C) 2006 The Lobo Project

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Contact info: lobochief@users.sourceforge.net
 */
/*
 * Created on Dec 3, 2005
 */
package org.lobobrowser.html.renderer;

import java.awt.Dimension;

import org.lobobrowser.html.HtmlRendererContext;
import org.lobobrowser.html.domimpl.HTMLTableCellElementImpl;
import org.lobobrowser.html.style.RenderState;
import org.lobobrowser.ua.UserAgentContext;

class RTableCell extends RBlock {
  private final HTMLTableCellElementImpl cellElement;
  private VirtualCell topLeftVirtualCell;

  // private int cellPadding;

  /**
   * @param element
   */
  public RTableCell(final HTMLTableCellElementImpl element, final UserAgentContext pcontext, final HtmlRendererContext rcontext,
      final FrameContext frameContext,
      final RenderableContainer tableAsContainer) {
    super(element, 0, pcontext, rcontext, frameContext, tableAsContainer);
    this.cellElement = element;
  }

  protected Dimension doCellLayout(final int width, final int height, final boolean expandWidth, final boolean expandHeight,
      final boolean sizeOnly) {
    return this.doCellLayout(width, height, expandWidth, expandHeight, sizeOnly, true);
  }

  /**
   * @param width
   *          The width available, including insets.
   * @param height
   *          The height available, including insets.
   * @param useCache
   *          Testing parameter. Should always be true.
   */
  protected Dimension doCellLayout(final int width, final int height, final boolean expandWidth, final boolean expandHeight,
      final boolean sizeOnly, final boolean useCache) {
    try {
      this.doLayout(width, height, expandWidth, expandHeight, null, RenderState.OVERFLOW_NONE, RenderState.OVERFLOW_NONE, sizeOnly,
          useCache);
      this.layout(width, height, expandWidth, expandHeight, null, sizeOnly);
      return new Dimension(this.width, this.height);
    } finally {
      this.layoutUpTreeCanBeInvalidated = true;
      this.layoutDeepCanBeInvalidated = true;
    }
  }

  void clearLayoutCache() {
    // test method
    this.cachedLayout.clear();
  }

  // public void setCellPadding(int value) {
  // this.cellPadding = value;
  // }

  @Override
  protected Integer getDeclaredHeight(final RenderState renderState, final int availHeight) {
    // Overridden since height declaration is handled by table.
    return null;
  }

  @Override
  protected Integer getDeclaredWidth(final RenderState renderState, final int availWidth) {
    // Overridden since width declaration is handled by table.
    return null;
  }

  @Override
  public void finalize() throws Throwable {
    super.finalize();
  }

  public void setTopLeftVirtualCell(final VirtualCell vc) {
    this.topLeftVirtualCell = vc;
  }

  public VirtualCell getTopLeftVirtualCell() {
    return this.topLeftVirtualCell;
  }

  private int colSpan = -1;
  private int rowSpan = -1;

  /**
   * @return Returns the virtualColumn.
   */
  public int getVirtualColumn() {
    final VirtualCell vc = this.topLeftVirtualCell;
    return vc == null ? 0 : vc.getColumn();
  }

  /**
   * @return Returns the virtualRow.
   */
  public int getVirtualRow() {
    final VirtualCell vc = this.topLeftVirtualCell;
    return vc == null ? 0 : vc.getRow();
  }

  public int getColSpan() {
    int cs = this.colSpan;
    if (cs == -1) {
      cs = this.cellElement.getColSpan();
      if (cs < 1) {
        cs = 1;
      }
      this.colSpan = cs;
    }
    return cs;
  }

  public int getRowSpan() {
    int rs = this.rowSpan;
    if (rs == -1) {
      rs = this.cellElement.getRowSpan();
      if (rs < 1) {
        rs = 1;
      }
      this.rowSpan = rs;
    }
    return rs;
  }

  public void setRowSpan(final int rowSpan) {
    this.rowSpan = rowSpan;
  }

  public String getHeightText() {
    return this.cellElement.getHeight();
  }

  public String getWidthText() {
    return this.cellElement.getWidth();
  }

  // public Dimension layoutMinWidth() {
  //
  // return this.panel.layoutMinWidth();
  //
  // }
  //
  //

  public void setCellBounds(final TableMatrix.SizeInfo[] colSizes, final TableMatrix.SizeInfo[] rowSizes, final int hasBorder,
      final int cellSpacingX,
      final int cellSpacingY) {
    final int vcol = this.getVirtualColumn();
    final int vrow = this.getVirtualRow();
    final TableMatrix.SizeInfo colSize = colSizes[vcol];
    final TableMatrix.SizeInfo rowSize = rowSizes[vrow];
    final int x = colSize.offset;
    final int y = rowSize.offset;
    int width;
    int height;
    final int colSpan = this.getColSpan();
    if (colSpan > 1) {
      width = 0;
      for (int i = 0; i < colSpan; i++) {
        final int vc = vcol + i;
        width += colSizes[vc].actualSize;
        if ((i + 1) < colSpan) {
          width += cellSpacingX + (hasBorder * 2);
        }
      }
    } else {
      width = colSizes[vcol].actualSize;
    }
    final int rowSpan = this.getRowSpan();
    if (rowSpan > 1) {
      height = 0;
      for (int i = 0; i < rowSpan; i++) {
        final int vr = vrow + i;
        height += rowSizes[vr].actualSize;
        if ((i + 1) < rowSpan) {
          height += cellSpacingY + (hasBorder * 2);
        }
      }
    } else {
      height = rowSizes[vrow].actualSize;
    }
    this.setBounds(x, y, width, height);
  }

  @Override
  protected boolean isMarginBoundary() {
    return true;
  }
}

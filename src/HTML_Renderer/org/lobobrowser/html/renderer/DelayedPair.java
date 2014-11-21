/*
    GNU LESSER GENERAL PUBLIC LICENSE
    Copyright (C) 2006 The XAMJ Project

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
package org.lobobrowser.html.renderer;

import org.lobobrowser.html.style.HtmlValues;
import org.lobobrowser.html.style.RenderState;

public class DelayedPair {
  public final RenderableContainer containingBlock;
  public final RenderableContainer immediateContainingBlock;
  public final BoundableRenderable child;
  private final String left;
  private final String top;
  private final String bottom;
  private final String right;
  private final RenderState rs;
  public final int currY;

  public DelayedPair(final RenderableContainer immediateContainingBlock, final RenderableContainer containingBlock, final BoundableRenderable child, final String left, final String right, final String top, final String bottom, final RenderState rs, final int currY) {
    super();
    this.immediateContainingBlock = immediateContainingBlock;
    this.containingBlock = containingBlock;
    this.child = child;
    this.left = left;
    this.right = right;
    this.top = top;
    this.bottom = bottom;
    this.rs = rs;
    this.currY = currY;
  }

  private static Integer helperGetPixelSize(final String spec, final RenderState rs, final int errorValue, final int avail) {
    if (spec != null) {
      return HtmlValues.getPixelSize(spec, rs, errorValue, avail);
    } else {
      return null;
    }
  }

  public Integer getLeft() {
    return helperGetPixelSize(left, rs, 0, containingBlock.getInnerWidth());
  }

  public Integer getRight() {
    return helperGetPixelSize(right, rs, 0, containingBlock.getInnerWidth());
  }

  public Integer getTop() {
    return helperGetPixelSize(top, rs, 0, containingBlock.getInnerHeight());
  }

  public Integer getBottom() {
    return helperGetPixelSize(bottom, rs, 0, containingBlock.getInnerHeight());
  }
}

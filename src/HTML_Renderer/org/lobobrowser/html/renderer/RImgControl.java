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

import java.awt.Insets;

import org.lobobrowser.html.domimpl.ModelNode;
import org.lobobrowser.ua.UserAgentContext;

public class RImgControl extends RUIControl {
  public RImgControl(final ModelNode me, final UIControl widget, final RenderableContainer container, final FrameContext frameContext,
      final UserAgentContext ucontext) {
    super(me, widget, container, frameContext, ucontext);
  }

  // TODO: This is a hack. RUIControl excludes border insets from the UI control. Images need to exclude padding as well.
  // Hence, we are returing getInsets() from getBorderInsets().
  // A better way would be to create two methods: one for excluded space and one for included space and implement as per convenience.
  // Yet another idea: check if RImgControl really needs to sub-class RUIControl or it can directly sub-class BaseElementRenderable.
  @Override
  public Insets getBorderInsets() {
    return getInsets(false, false);
  }
}

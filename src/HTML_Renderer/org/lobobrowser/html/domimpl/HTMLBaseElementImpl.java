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
package org.lobobrowser.html.domimpl;

import org.w3c.dom.UserDataHandler;

public class HTMLBaseElementImpl extends HTMLElementImpl {
  public HTMLBaseElementImpl(final String name) {
    super(name, true);
  }

  public Object setUserData(final String key, final Object data, final UserDataHandler handler) {
    if (org.lobobrowser.html.parser.HtmlParser.MODIFYING_KEY.equals(key) && data != Boolean.TRUE) {
      this.processBaseTag();
    }
    return super.setUserData(key, data, handler);
  }

  private final void processBaseTag() {
    final HTMLDocumentImpl doc = (HTMLDocumentImpl) this.document;
    if (doc != null) {
      doc.setBaseURI(this.getAttribute("href"));
      doc.setDefaultTarget(this.getAttribute("target"));
    }
  }
}

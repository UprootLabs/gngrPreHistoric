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
 * Created on Nov 27, 2005
 */
package org.lobobrowser.html.domimpl;

import org.lobobrowser.html.style.CSSUtilities;
import org.lobobrowser.ua.UserAgentContext;
import org.w3c.css.sac.InputSource;
import org.w3c.dom.UserDataHandler;
import org.w3c.dom.css.CSSStyleSheet;
import org.w3c.dom.html2.HTMLStyleElement;

import com.steadystate.css.dom.CSSStyleSheetImpl;
import com.steadystate.css.parser.CSSOMParser;

public class HTMLStyleElementImpl extends HTMLElementImpl implements HTMLStyleElement {
  private CSSStyleSheet styleSheet;

  public HTMLStyleElementImpl() {
    super("STYLE", true);
  }

  public HTMLStyleElementImpl(final String name) {
    super(name, true);
  }

  private boolean disabled;

  public boolean getDisabled() {
    return this.disabled;
  }

  public void setDisabled(final boolean disabled) {
    this.disabled = disabled;
    final CSSStyleSheet sheet = this.styleSheet;
    if (sheet != null) {
      sheet.setDisabled(disabled);
    }
  }

  public String getMedia() {
    return this.getAttribute("media");
  }

  public void setMedia(final String media) {
    this.setAttribute("media", media);
  }

  public String getType() {
    return this.getAttribute("type");
  }

  public void setType(final String type) {
    this.setAttribute("type", type);
  }

  public Object setUserData(final String key, final Object data, final UserDataHandler handler) {
    if (org.lobobrowser.html.parser.HtmlParser.MODIFYING_KEY.equals(key) && data != Boolean.TRUE) {
      ((HTMLDocumentImpl) document).addJob(() -> processStyle());
      this.processStyle();
    }
    // else
    // if(com.steadystate.css.dom.CSSStyleSheetImpl.KEY_DISABLED_CHANGED.equals(key))
    // {
    // this.informDocumentInvalid();
    // }
    return super.setUserData(key, data, handler);
  }

  protected void processStyle() {
    this.styleSheet = null;
    final UserAgentContext uacontext = this.getUserAgentContext();
    if (uacontext.isInternalCSSEnabled()) {
      if (CSSUtilities.matchesMedia(this.getMedia(), this.getUserAgentContext())) {
        final String text = this.getRawInnerText(true);
        if (text != null && !"".equals(text)) {
          final String processedText = CSSUtilities.preProcessCss(text);
          final HTMLDocumentImpl doc = (HTMLDocumentImpl) this.getOwnerDocument();
          final CSSOMParser parser = CSSUtilities.mkParser();
          final String baseURI = doc.getBaseURI();
          final InputSource is = CSSUtilities.getCssInputSourceForStyleSheet(processedText, baseURI);
          try {
            final CSSStyleSheetImpl sheet = (CSSStyleSheetImpl) parser.parseStyleSheet(is, this, baseURI);
            doc.addStyleSheet(sheet);
            this.styleSheet = sheet;
            if (sheet instanceof CSSStyleSheetImpl) {
              final CSSStyleSheetImpl sheetImpl = (CSSStyleSheetImpl) sheet;
              sheetImpl.setDisabled(this.disabled);
            } else {
              sheet.setDisabled(this.disabled);
            }
          } catch (final Throwable err) {
            this.warn("Unable to parse style sheet", err);
          }
        }
      }
    }
  }

  protected void appendInnerTextImpl(final StringBuffer buffer) {
    // nop
  }
}

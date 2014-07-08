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
package org.lobobrowser.html.domimpl;

import java.awt.Cursor;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lobobrowser.html.HtmlRendererContext;
import org.lobobrowser.html.style.CSSUtilities;
import org.lobobrowser.html.style.ColorRenderState;
import org.lobobrowser.html.style.CursorRenderState;
import org.lobobrowser.html.style.RenderState;
import org.lobobrowser.html.style.TextDecorationRenderState;
import org.lobobrowser.ua.UserAgentContext;
import org.lobobrowser.util.gui.ColorFactory;
import org.w3c.dom.UserDataHandler;
import org.w3c.dom.css.CSSStyleSheet;
import org.w3c.dom.html.HTMLBodyElement;
import org.w3c.dom.html.HTMLDocument;
import org.w3c.dom.html.HTMLLinkElement;

import com.steadystate.css.dom.CSSStyleSheetImpl;

public class HTMLLinkElementImpl extends HTMLAbstractUIElement implements HTMLLinkElement {
  private static final Logger logger = Logger.getLogger(HTMLLinkElementImpl.class.getName());
  private static final boolean loggableInfo = logger.isLoggable(Level.INFO);
  private CSSStyleSheet styleSheet;

  public HTMLLinkElementImpl(final String name) {
    super(name);
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

  public String getHref() {
    final String href = this.getAttribute("href");
    return href == null ? "" : href;
  }

  public void setHref(final String href) {
    this.setAttribute("href", href);
  }

  public String getHreflang() {
    return this.getAttribute("hreflang");
  }

  public void setHreflang(final String hreflang) {
    this.setAttribute("hreflang", hreflang);
  }

  public String getMedia() {
    return this.getAttribute("media");
  }

  public void setMedia(final String media) {
    this.setAttribute("media", media);
  }

  public String getRel() {
    return this.getAttribute("rel");
  }

  public void setRel(final String rel) {
    this.setAttribute("rel", rel);
  }

  public String getRev() {
    return this.getAttribute("rev");
  }

  public void setRev(final String rev) {
    this.setAttribute("rev", rev);
  }

  public String getTarget() {
    final String target = this.getAttribute("target");
    if (target != null) {
      return target;
    }
    final HTMLDocumentImpl doc = (HTMLDocumentImpl) this.document;
    return doc == null ? null : doc.getDefaultTarget();
  }

  public void setTarget(final String target) {
    this.setAttribute("target", target);
  }

  public String getType() {
    return this.getAttribute("type");
  }

  public void setType(final String type) {
    this.setAttribute("type", type);
  }

  public Object setUserData(final String key, final Object data, final UserDataHandler handler) {
    if (org.lobobrowser.html.parser.HtmlParser.MODIFYING_KEY.equals(key) && data != Boolean.TRUE) {
      ((HTMLDocumentImpl) document).addJob(() -> processLink());
      // this.processLink();
    }
    // else
    // if(com.steadystate.css.dom.CSSStyleSheetImpl.KEY_DISABLED_CHANGED.equals(key))
    // {
    // this.informDocumentInvalid();
    // }
    return super.setUserData(key, data, handler);
  }

  /**
   * If the LINK refers to a stylesheet document, this method loads and parses
   * it.
   */
  protected void processLink() {
    this.styleSheet = null;
    final String rel = this.getAttribute("rel");
    if (rel != null) {
      final String cleanRel = rel.trim().toLowerCase();
      final boolean isStyleSheet = cleanRel.equals("stylesheet");
      final boolean isAltStyleSheet = cleanRel.equals("alternate stylesheet");
      if (isStyleSheet || isAltStyleSheet) {
        final UserAgentContext uacontext = this.getUserAgentContext();
        if (uacontext.isExternalCSSEnabled()) {
          final String media = this.getMedia();
          if (CSSUtilities.matchesMedia(media, uacontext)) {
            final HTMLDocumentImpl doc = (HTMLDocumentImpl) this.getOwnerDocument();
            try {
              final boolean liflag = loggableInfo;
              final long time1 = liflag ? System.currentTimeMillis() : 0;
              try {
                final CSSStyleSheet sheet = CSSUtilities.parse(this, this.getHref(), doc, doc.getBaseURI(), false);
                if (sheet != null) {
                  this.styleSheet = sheet;
                  if (sheet instanceof CSSStyleSheetImpl) {
                    final CSSStyleSheetImpl sheetImpl = (CSSStyleSheetImpl) sheet;
                    if (isAltStyleSheet) {
                      sheetImpl.setDisabled(true);
                    } else {
                      sheetImpl.setDisabled(this.disabled);
                    }
                  } else {
                    if (isAltStyleSheet) {
                      sheet.setDisabled(true);
                    } else {
                      sheet.setDisabled(this.disabled);
                    }
                  }
                  doc.addStyleSheet(sheet);
                }
              } finally {
                if (liflag) {
                  final long time2 = System.currentTimeMillis();
                  logger.info("processLink(): Loaded and parsed CSS (or attempted to) at URI=[" + this.getHref() + "] in "
                      + (time2 - time1) + " ms.");
                }
              }

            } catch (final MalformedURLException mfe) {
              this.warn("Will not parse CSS. URI=[" + this.getHref() + "] with BaseURI=[" + doc.getBaseURI()
                  + "] does not appear to be a valid URI.");
            } catch (final Throwable err) {
              this.warn("Unable to parse CSS. URI=[" + this.getHref() + "].", err);
            }
          }
        }
      }
    }
  }

  public String getAbsoluteHref() {
    final HtmlRendererContext rcontext = this.getHtmlRendererContext();
    if (rcontext != null) {
      final String href = this.getHref();
      if (href != null && href.length() > 0) {
        final String target = this.getTarget();
        try {
          final URL url = this.getFullURL(href);
          return url == null ? null : url.toExternalForm();
        } catch (final MalformedURLException mfu) {
          this.warn("Malformed URI: [" + href + "].", mfu);
        }
      }
    }
    return null;
  }

  public void navigate() {
    if (this.disabled) {
      return;
    }
    final HtmlRendererContext rcontext = this.getHtmlRendererContext();
    if (rcontext != null) {
      final String href = this.getHref();
      if (href != null && href.length() > 0) {
        final String target = this.getTarget();
        try {
          final URL url = this.getFullURL(href);
          if (url == null) {
            this.warn("Unable to resolve URI: [" + href + "].");
          } else {
            rcontext.linkClicked(this, url, target);
          }
        } catch (final MalformedURLException mfu) {
          this.warn("Malformed URI: [" + href + "].", mfu);
        }
      }
    }
  }

  private java.awt.Color getLinkColor() {
    final HTMLDocument doc = (HTMLDocument) this.document;
    if (doc != null) {
      final HTMLBodyElement body = (HTMLBodyElement) doc.getBody();
      if (body != null) {
        final String vlink = body.getVLink();
        final String link = body.getLink();
        if (vlink != null || link != null) {
          final HtmlRendererContext rcontext = this.getHtmlRendererContext();
          if (rcontext != null) {
            final boolean visited = rcontext.isVisitedLink(this);
            final String colorText = visited ? vlink : link;
            if (colorText != null) {
              return ColorFactory.getInstance().getColor(colorText);
            }
          }
        }
      }
    }
    return java.awt.Color.BLUE;
  }

  protected RenderState createRenderState(RenderState prevRenderState) {
    if (this.hasAttribute("href")) {
      prevRenderState = new TextDecorationRenderState(prevRenderState, RenderState.MASK_TEXTDECORATION_UNDERLINE);
      prevRenderState = new ColorRenderState(prevRenderState, this.getLinkColor());
      prevRenderState = new CursorRenderState(prevRenderState, Optional.of(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)));
    }
    return super.createRenderState(prevRenderState);
  }

  public String toString() {
    // Javascript code often depends on this being exactly href. See js9.html.
    // To change, perhaps add method to AbstractScriptableDelegate.
    return this.getHref();
  }
}

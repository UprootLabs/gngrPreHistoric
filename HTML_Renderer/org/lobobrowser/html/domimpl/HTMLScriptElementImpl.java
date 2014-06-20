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
 * Created on Oct 8, 2005
 */
package org.lobobrowser.html.domimpl;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lobobrowser.html.js.Executor;
import org.lobobrowser.ua.NetworkRequest;
import org.lobobrowser.ua.UserAgentContext;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.Scriptable;
import org.w3c.dom.Document;
import org.w3c.dom.UserDataHandler;
import org.w3c.dom.html2.HTMLScriptElement;

public class HTMLScriptElementImpl extends HTMLElementImpl implements HTMLScriptElement {
  private static final Logger logger = Logger.getLogger(HTMLScriptElementImpl.class.getName());
  private static final boolean loggableInfo = logger.isLoggable(Level.INFO);

  public HTMLScriptElementImpl() {
    super("SCRIPT", true);
  }

  public HTMLScriptElementImpl(final String name) {
    super(name, true);
  }

  private String text;

  public String getText() {
    final String t = this.text;
    if (t == null) {
      return this.getRawInnerText(true);
    } else {
      return t;
    }
  }

  public void setText(final String text) {
    this.text = text;
  }

  public String getHtmlFor() {
    return this.getAttribute("htmlFor");
  }

  public void setHtmlFor(final String htmlFor) {
    this.setAttribute("htmlFor", htmlFor);
  }

  public String getEvent() {
    return this.getAttribute("event");
  }

  public void setEvent(final String event) {
    this.setAttribute("event", event);
  }

  private boolean defer;

  public boolean getDefer() {
    return this.defer;
  }

  public void setDefer(final boolean defer) {
    this.defer = defer;
  }

  public String getSrc() {
    return this.getAttribute("src");
  }

  public void setSrc(final String src) {
    this.setAttribute("src", src);
  }

  public String getType() {
    return this.getAttribute("type");
  }

  public void setType(final String type) {
    this.setAttribute("type", type);
  }

  public Object setUserData(final String key, final Object data, final UserDataHandler handler) {
    if (org.lobobrowser.html.parser.HtmlParser.MODIFYING_KEY.equals(key) && data != Boolean.TRUE) {
      this.processScript();
    }
    return super.setUserData(key, data, handler);
  }

  protected final void processScript() {
    final UserAgentContext bcontext = this.getUserAgentContext();
    if (bcontext == null) {
      throw new IllegalStateException("No user agent context.");
    }
    if (bcontext.isScriptingEnabled()) {
      String text;
      final String scriptURI;
      int baseLineNumber;
      final String src = this.getSrc();
      final Document doc = this.document;
      if (!(doc instanceof HTMLDocumentImpl)) {
        throw new IllegalStateException("no valid document");
      }
      final boolean liflag = loggableInfo;
      if (src == null) {
        if (bcontext.isRequestPermitted(new UserAgentContext.InlineScriptRequest())) {
          text = this.getText();
          scriptURI = doc.getBaseURI();
          baseLineNumber = 1; // TODO: Line number of inner text??
        } else {
          text = null;
          scriptURI = null;
          baseLineNumber = -1;
        }
      } else {
        this.informExternalScriptLoading();
        final java.net.URL scriptURL = ((HTMLDocumentImpl) doc).getFullURL(src);
        scriptURI = scriptURL == null ? src : scriptURL.toExternalForm();
        if (bcontext.isRequestPermitted(new UserAgentContext.ScriptRequest(scriptURL))) {
          final long time1 = liflag ? System.currentTimeMillis() : 0;
          try {
            final NetworkRequest request = bcontext.createHttpRequest();
            // Perform a synchronous request
            final SecurityManager sm = System.getSecurityManager();
            if (sm == null) {
              try {
                request.open("GET", scriptURI, false);
                request.send(null);
              } catch (final java.io.IOException thrown) {
                logger.log(Level.WARNING, "processScript()", thrown);
              }
            } else {
              AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                  // Code might have restrictions on accessing
                  // items from elsewhere.
                  try {
                    request.open("GET", scriptURI, false);
                    request.send(null);
                  } catch (final java.io.IOException thrown) {
                    logger.log(Level.WARNING, "processScript()", thrown);
                  }
                  return null;
                }
              });
            }
            final int status = request.getStatus();
            if (status != 200 && status != 0) {
              this.warn("Script at [" + scriptURI + "] failed to load; HTTP status: " + status + ".");
              return;
            }
            text = request.getResponseText();
          } finally {
            if (liflag) {
              final long time2 = System.currentTimeMillis();
              logger.info("processScript(): Loaded external Javascript from URI=[" + scriptURI + "] in " + (time2 - time1) + " ms.");
            }
          }
          baseLineNumber = 1;
        } else {
          text = null;
          baseLineNumber = -1;
        }
      }
      final Context ctx = Executor.createContext(this.getDocumentURL(), bcontext);
      try {
        final Scriptable scope = (Scriptable) doc.getUserData(Executor.SCOPE_KEY);
        if (scope == null) {
          throw new IllegalStateException("Scriptable (scope) instance was expected to be keyed as UserData to document using "
              + Executor.SCOPE_KEY);
        }
        try {
          final long time1 = liflag ? System.currentTimeMillis() : 0;
          if (text != null) {
            ctx.evaluateString(scope, text, scriptURI, baseLineNumber, null);
          if (liflag) {
            final long time2 = System.currentTimeMillis();
            logger.info("addNotify(): Evaluated (or attempted to evaluate) Javascript in " + (time2 - time1) + " ms.");
          }
          }
        } catch (final EcmaError ecmaError) {
          logger.log(Level.WARNING,
              "Javascript error at " + ecmaError.sourceName() + ":" + ecmaError.lineNumber() + ": " + ecmaError.getMessage(),
              ecmaError);
        } catch (final Throwable err) {
          logger.log(Level.WARNING, "Unable to evaluate Javascript code", err);
        }
      } finally {
        Context.exit();
      }
    }
  }

  protected void appendInnerTextImpl(final StringBuffer buffer) {
    // nop
  }
}

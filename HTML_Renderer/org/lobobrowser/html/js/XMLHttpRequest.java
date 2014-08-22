package org.lobobrowser.html.js;

import java.io.IOException;
import java.net.SocketPermission;
import java.net.URL;
import java.net.URLPermission;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lobobrowser.js.AbstractScriptableDelegate;
import org.lobobrowser.js.JavaScript;
import org.lobobrowser.ua.NetworkRequest;
import org.lobobrowser.ua.UserAgentContext;
import org.lobobrowser.ua.UserAgentContext.Request;
import org.lobobrowser.ua.UserAgentContext.RequestKind;
import org.lobobrowser.util.Urls;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;

public class XMLHttpRequest extends AbstractScriptableDelegate {
  // TODO: See reference:
  // http://www.xulplanet.com/references/objref/XMLHttpRequest.html

  private static final Logger logger = Logger.getLogger(XMLHttpRequest.class.getName());
  private final NetworkRequest request;
  private final UserAgentContext pcontext;
  private final Scriptable scope;
  private final java.net.URL codeSource;

  public XMLHttpRequest(final UserAgentContext pcontext, final java.net.URL codeSource, final Scriptable scope) {
    this.request = pcontext.createHttpRequest();
    this.pcontext = pcontext;
    this.scope = scope;
    this.codeSource = codeSource;
  }

  public void abort() {
    request.abort();
  }

  @NotGetterSetter
  public String getAllResponseHeaders() {
    return request.getAllResponseHeaders();
  }

  public int getReadyState() {
    return request.getReadyState();
  }

  public byte[] getResponseBytes() {
    return request.getResponseBytes();
  }

  public String getResponseHeader(final String headerName) {
    return request.getResponseHeader(headerName);
  }

  public String getResponseText() {
    return request.getResponseText();
  }

  public Document getResponseXML() {
    return request.getResponseXML();
  }

  public int getStatus() {
    return request.getStatus();
  }

  public String getStatusText() {
    return request.getStatusText();
  }

  private URL getFullURL(final String relativeUrl) throws java.net.MalformedURLException {
    return Urls.createURL(this.codeSource, relativeUrl);
  }

  public void open(final String method, final String url, final boolean asyncFlag, final String userName, final String password) throws java.io.IOException {
    request.open(method, this.getFullURL(url), asyncFlag, userName, password);
  }

  public void open(final String method, final String url, final boolean asyncFlag, final String userName) throws java.io.IOException {
    request.open(method, this.getFullURL(url), asyncFlag, userName);
  }

  public void open(final String method, final String url, final boolean asyncFlag) throws java.io.IOException {
    request.open(method, this.getFullURL(url), asyncFlag);
  }

  public void open(final String method, final String url) throws java.io.IOException {
    request.open(method, this.getFullURL(url));
  }

  public void send(final String content) throws IOException {
    final Optional<URL> urlOpt = request.getURL();
    if (urlOpt.isPresent()) {
      final URL url = urlOpt.get();
      if (isSameOrigin(url, codeSource)) {
        final URLPermission urlPermission = new URLPermission(url.toExternalForm());
        final SocketPermission socketPermission = new SocketPermission(url.getHost()+":"+Urls.getPort(url), "connect,resolve");
        final PrivilegedExceptionAction<Object> action = () -> {
          request.send(content, new Request(url, RequestKind.XHR));
          return null;
        };
        try {
          AccessController.doPrivileged(action, null, urlPermission, socketPermission);
        } catch (PrivilegedActionException e) {
          throw (IOException) e.getCause();
        }
      } else {
        final String msg = String.format("Failed to execute 'send' on 'XMLHttpRequest': Failed to load '%s'", url.toExternalForm());
        // TODO: The code 19 is being hard-coded here to match Chromium's code. Better to declare a static constant in a subclass of DOMException.
        throw new DOMException((short) 19, msg);
      }
    }
  }

  private static boolean isSameOrigin(final URL url1, final URL url2) {
    return
        url1.getHost().equals(url2.getHost()) &&
        url1.getPort() == (url2.getPort()) &&
        url1.getProtocol().equals(url2.getProtocol());

  }

  private Function onreadystatechange;
  private boolean listenerAdded;

  public Function getOnreadystatechange() {
    synchronized (this) {
      return this.onreadystatechange;
    }
  }

  public void setOnreadystatechange(final Function value) {
    synchronized (this) {
      this.onreadystatechange = value;
      if (value != null && !this.listenerAdded) {
        this.request.addNetworkRequestListener(netEvent -> executeReadyStateChange());
        this.listenerAdded = true;
      }
    }
  }

  private void executeReadyStateChange() {
    // Not called in GUI thread to ensure consistency of readyState.
    try {
      final Function f = XMLHttpRequest.this.getOnreadystatechange();
      if (f != null) {
        final Context ctx = Executor.createContext(this.codeSource, this.pcontext);
        try {
          final Scriptable newScope = (Scriptable) JavaScript.getInstance().getJavascriptObject(XMLHttpRequest.this, this.scope);
          f.call(ctx, newScope, newScope, new Object[0]);
        } finally {
          Context.exit();
        }
      }
    } catch (final Exception err) {
      logger.log(Level.WARNING, "Error processing ready state change.", err);
    }
  }

}

package org.lobobrowser.ua;

import java.net.URL;

import org.lobobrowser.ua.NetworkRequest;

/**
 * Provides information about the user agent (browser) driving the parser and/or
 * renderer.
 * <p>
 * A simple implementation of this interface is provided in
 * {@link org.lobobrowser.html.test.SimpleUserAgentContext}.
 * 
 * @see HtmlRendererContext#getUserAgentContext()
 * @see org.lobobrowser.html.parser.DocumentBuilderImpl#DocumentBuilderImpl(UserAgentContext)
 */
public interface UserAgentContext {
  static public abstract class Request {
    final public URL url;
    final public int id;

    Request(final URL url, final int id) {
      this.url = url;
      this.id = id;
    }

    @Override
    public String toString() {
      return this.getClass().getSimpleName() + " : " + url;
    }
  }

  static public class CookieRequest extends Request {
    public CookieRequest(final URL url) {
      super(url, 0);
    }
  }

  static public abstract class NetRequest extends Request {
    public NetRequest(final URL url, final int id) {
      super(url, id);
    }
  }

  static public class ImageRequest extends NetRequest {
    public ImageRequest(final URL url) {
      super(url, 1);
    }
  }

  static public class ScriptRequest extends NetRequest {

    public ScriptRequest(final URL url) {
      super(url, 2);
    }
  }

  static public class InlineScriptRequest extends Request {

    public InlineScriptRequest(final URL url) {
      super(url, 3);
    }
  }

  static public class XHRRequest extends NetRequest {

    public XHRRequest(final URL url) {
      super(url, 4);
    }
  }

  static public class CSSRequest extends NetRequest {

    public CSSRequest(final URL url) {
      super(url, 5);
    }
  }

  static public class FrameRequest extends NetRequest {
    public FrameRequest(final URL url) {
      super(url, 6);
    }
  }
  static public int COUNT_REQUEST_ID = 7;

  public boolean isRequestPermitted(final Request request);

  /**
   * Creates an instance of {@link org.lobobrowser.html.HttpRequest} which can
   * be used by the renderer to load images, scripts, external style sheets, and
   * implement the Javascript XMLHttpRequest class (AJAX).
   */
  public NetworkRequest createHttpRequest();

  /**
   * Gets browser "code" name.
   */
  public String getAppCodeName();

  /**
   * Gets browser application name.
   */
  public String getAppName();

  /**
   * Gets browser application version.
   */
  public String getAppVersion();

  /**
   * Gets browser application minor version.
   */
  public String getAppMinorVersion();

  /**
   * Gets browser language code. See <a
   * href="http://en.wikipedia.org/wiki/List_of_ISO_639-1_codes">ISO 639-1
   * codes</a>.
   */
  public String getBrowserLanguage();

  /**
   * Returns a boolean value indicating whether cookies are enabled in the user
   * agent. This value is used for reporting purposes only.
   * TODO: Remove
   */
  public boolean isCookieEnabled();

  /**
   * Returns a boolean value indicating whether scripting is enabled in the user
   * agent. If this value is <code>false</code>, the parser will not process
   * scripts and Javascript element attributes will have no effect.
   * TODO: Remove
   */
  public boolean isScriptingEnabled();

  /**
   * Returns a boolean value indicating whether remote (non-inline) CSS
   * documents should be loaded.
   * TODO: Remove
   */
  public boolean isExternalCSSEnabled();

  /**
   * Returns a boolean value indicating whether STYLE tags should be processed.
   * TODO: Remove
   */
  public boolean isInternalCSSEnabled();

  /**
   * Gets the name of the user's operating system.
   */
  public String getPlatform();

  /**
   * Should return the string used in the User-Agent header.
   */
  public String getUserAgent();

  /**
   * Method used to implement Javascript <code>document.cookie</code> property.
   */
  public String getCookie(java.net.URL url);

  /**
   * Method used to implement <code>document.cookie</code> property.
   * 
   * @param cookieSpec
   *          Specification of cookies, as they would appear in the Set-Cookie
   *          header value of HTTP.
   */
  public void setCookie(java.net.URL url, String cookieSpec);

  /**
   * Gets the security policy for scripting. Return <code>null</code> if
   * JavaScript code is trusted.
   */
  public java.security.Policy getSecurityPolicy();

  /**
   * Gets the scripting optimization level, which is a value equivalent to
   * Rhino's optimization level.
   */
  public int getScriptingOptimizationLevel();

  /**
   * Returns true if the current media matches the name provided.
   * 
   * @param mediaName
   *          Media name, which may be <code>screen</code>, <code>tty</code>,
   *          etc. (See <a href=
   *          "http://www.w3.org/TR/REC-html40/types.html#type-media-descriptors"
   *          >HTML Specification</a>).
   */
  public boolean isMedia(String mediaName);

  public String getVendor();

  public String getProduct();
}

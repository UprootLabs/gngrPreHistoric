package org.lobobrowser.primary.clientlets.html;

import org.lobobrowser.html.HttpRequest;
import org.lobobrowser.html.UserAgentContext;
import org.lobobrowser.request.RequestEngine;
import org.lobobrowser.request.UserAgentImpl;
import org.lobobrowser.ua.NavigatorFrame;

import java.security.Policy;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SilentUserAgentContextImpl implements UserAgentContext {
	private static final Logger logger = Logger.getLogger(SilentUserAgentContextImpl.class.getName());
	private static final Set<String> mediaNames = new HashSet<String>();
	private final NavigatorFrame frame;
		
	static {
		// Media names supported here
		Set<String> mn = mediaNames;
	    mn.add("screen");	
	    mn.add("tv");	
	    mn.add("tty");	
	    mn.add("all");	
	}
	
	public SilentUserAgentContextImpl(final NavigatorFrame frame) {
		this.frame = frame;
	}

	public boolean isMedia(String mediaName) {
		return mediaNames.contains(mediaName.toLowerCase());
	}

	public void warn(String message, Throwable throwable) {
		logger.log(Level.WARNING, message, throwable);
	}

	public void error(String message, Throwable throwable) {
		logger.log(Level.SEVERE, message, throwable);
	}

	public void warn(String message) {
		logger.warning(message);
	}

	public void error(String message) {
		logger.log(Level.SEVERE, message);
	}

	public HttpRequest createHttpRequest() {
		return new HttpRequestImpl(this.frame.createNetworkRequest());
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.HtmlParserContext#getAppCodeName()
	 */
	public String getAppCodeName() {
		return "";
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.HtmlParserContext#getAppMinorVersion()
	 */
	public String getAppMinorVersion() {
		return "";
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.HtmlParserContext#getAppName()
	 */
	public String getAppName() {
		return "";
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.HtmlParserContext#getAppVersion()
	 */
	public String getAppVersion() {
		return "";
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.HtmlParserContext#getBrowserLanguage()
	 */
	public String getBrowserLanguage() {
		return "EN"; //TODO
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.HtmlParserContext#getPlatform()
	 */
	public String getPlatform() {
		return "";
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.HtmlParserContext#getUserAgent()
	 */
	public String getUserAgent() {
		return "";
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.HtmlParserContext#isCookieEnabled()
	 */
	public boolean isCookieEnabled() {
		//TODO: Settings
		return true; 
	}

	public String getCookie(java.net.URL url) {
		// Requires privileges.
		return RequestEngine.getInstance().getCookie(url);
	}

	public boolean isScriptingEnabled() {
		//TODO: Settings
		return true;
	}

	public void setCookie(java.net.URL url, String cookieSpec) {
		// Requires privileges.
		RequestEngine.getInstance().setCookie(url, cookieSpec);
	}

	public Policy getSecurityPolicy() {
		return org.lobobrowser.security.LocalSecurityPolicy.getInstance();
	}

	public int getScriptingOptimizationLevel() {
		//TODO: Settings
		return -1;
	}
	
	public String getVendor() {
		return "";
	}
	
	public String getProduct() {
		return this.getAppName();
	}

	public boolean isExternalCSSEnabled() {
		return true;
	}

    public boolean isInternalCSSEnabled() {
        return true;
    }
}

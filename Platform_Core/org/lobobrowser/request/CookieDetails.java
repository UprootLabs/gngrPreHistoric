package org.lobobrowser.request;

import java.net.URI;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lobobrowser.util.Strings;

final class CookieDetails {
  private static final Logger logger = Logger.getLogger(CookieDetails.class.getName());
  private static final DateFormat EXPIRES_FORMAT;
  private static final DateFormat EXPIRES_FORMAT_BAK1;
  private static final DateFormat EXPIRES_FORMAT_BAK2;
  static {
    // Note: Using yy in case years are given as two digits.
    // Note: Must use US locale for cookie dates.
    final Locale locale = Locale.US;
    final SimpleDateFormat ef1 = new SimpleDateFormat("EEE, dd MMM yy HH:mm:ss 'GMT'", locale);
    final SimpleDateFormat ef2 = new SimpleDateFormat("EEE, dd-MMM-yy HH:mm:ss 'GMT'", locale);
    final SimpleDateFormat ef3 = new SimpleDateFormat("EEE MMM dd HH:mm:ss yy 'GMT'", locale);
    final TimeZone gmtTimeZone = TimeZone.getTimeZone("GMT");
    ef1.setTimeZone(gmtTimeZone);
    ef2.setTimeZone(gmtTimeZone);
    ef3.setTimeZone(gmtTimeZone);
    EXPIRES_FORMAT = ef1;
    EXPIRES_FORMAT_BAK1 = ef2;
    EXPIRES_FORMAT_BAK2 = ef3;
  }

  public CookieDetails(URI requestURL, String name, String value, String domain, String path, String expires, String maxAge, final boolean secure, final boolean httpOnly) {
    this.requestURL = requestURL;
    this.name = name;
    this.value = value;
    this.domain = domain;
    this.path = path;
    this.expires = expires;
    this.maxAge = maxAge;
    this.secure = secure;
    this.httpOnly = httpOnly;
    this.requestHostName = requestURL.getHost();
  }

  final URI requestURL;
  final String requestHostName;
  final String name;
  final String value;
  final String domain;
  private final String path;
  final String expires;
  final String maxAge;
  final boolean secure, httpOnly;

  final String getEffectivePath() {
    if (path == null || path.length() == 0 || path.charAt(0) != '/') {
      return getDefaultPath();
    } else {
      return path;
    }
  }

  /* As per section 5.1.4 of RFC 6265 */
  private String getDefaultPath() {
    String urlPath = requestURL.getPath();
    if(urlPath == null || urlPath.length() == 0 || urlPath.charAt(0) != '/') {
      return "/";
    } else if (Strings.countChars(urlPath, '/') == 1) {
      return "/";
    } else {
      return urlPath.substring(0, urlPath.lastIndexOf('/'));
    }
  }

  final String getEffectiveDomain() {
    if (domain == null) {
      return requestHostName;
    } else if (domain.startsWith(".")) {
      return domain.substring(1);
    } else {
      return domain;
    }
  }

  final Optional<java.util.Date> getExpiresDate() {
    Optional<java.util.Date> expiresDate = Optional.empty();
    if (maxAge != null) {
      try {
        final int maxAgeSeconds = Integer.parseInt(maxAge);
        expiresDate = Optional.of(new java.util.Date(System.currentTimeMillis() + maxAgeSeconds * 1000));
      } catch (final java.lang.NumberFormatException nfe) {
        logger.log(Level.WARNING, "saveCookie(): Max-age is not formatted correctly: " + maxAge + ".");
      }
    } else if (expires != null) {
      synchronized (EXPIRES_FORMAT) {
        try {
          expiresDate = Optional.of(EXPIRES_FORMAT.parse(expires));
        } catch (final Exception pe) {
          if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "saveCookie(): Bad date format: " + expires + ". Will try again.", pe);
          }
          try {
            expiresDate = Optional.of(EXPIRES_FORMAT_BAK1.parse(expires));
          } catch (final Exception pe2) {
            try {
              expiresDate = Optional.of(EXPIRES_FORMAT_BAK2.parse(expires));
            } catch (final ParseException pe3) {
              logger.log(Level.WARNING, "saveCookie(): Giving up on cookie date format: " + expires, pe3);
            }
          }
        }
      }
    }
    return expiresDate;
  }

  boolean isValidDomain() {
    if (domain != null) {
      if (expires == null && maxAge == null && logger.isLoggable(Level.INFO)) {
        // TODO: Check if this is true:
        // One of the RFCs says transient cookies should not have
        // a domain specified, but websites apparently rely on that,
        // specifically Paypal.
        logger.log(Level.INFO, "Not rejecting transient cookie that specifies domain '" + domain + "'.");
      }
      // if (!Domains.isValidCookieDomain(domain, urlHostName)) {
      if (!DomainValidation.isValidCookieDomain(domain, requestHostName)) {
        logger.log(Level.WARNING, "saveCookie(): Rejecting cookie with invalid domain '" + domain + "' for host '"
            + requestHostName + "'.");
        return false;
      }
    }
    return true;
  }

  static Optional<CookieDetails> parseCookieSpec(final URI requestURL, final String cookieSpec) {
    final StringTokenizer tok = new StringTokenizer(cookieSpec, ";");
    String cookieName = null;
    String cookieValue = null;
    String domain = null;
    String path = null;
    String expires = null;
    String maxAge = null;
    boolean secure = false;
    boolean httpOnly = false;
    boolean hasCookieName = false;
    while (tok.hasMoreTokens()) {
      final String token = tok.nextToken();
      final int idx = token.indexOf('=');
      final String name = idx == -1 ? token.trim() : token.substring(0, idx).trim();
      final String value = idx == -1 ? "" : token.substring(idx + 1).trim();
      if (!hasCookieName) {
        if ((idx == -1) || (name.length() == 0)) {
          return Optional.empty();
        } else {
          cookieName = name;
          cookieValue = value;
          hasCookieName = true;
        }
      } else {
        if ("max-age".equalsIgnoreCase(name)) {
          maxAge = value;
        } else if ("path".equalsIgnoreCase(name)) {
          path = value;
        } else if ("domain".equalsIgnoreCase(name)) {
          domain = value;
        } else if ("expires".equalsIgnoreCase(name)) {
          expires = value;
        } else if ("secure".equalsIgnoreCase(name)) {
          secure = true;
        } else if ("httponly".equalsIgnoreCase(name)) {
          httpOnly = true;
        }
      }
    }
    return Optional.of(new CookieDetails(requestURL, cookieName, cookieValue, domain, path, expires, maxAge, secure, httpOnly));
  }

  @Override
  public String toString() {
    final String expiresDateStr = Optional.ofNullable(getExpiresDate()).toString();
    return "CookieDetails [name=" + name + ", value=" + value + ", domain=" + domain + ", path=" + path + ", expires=" + expires
        + ", maxAge=" + maxAge + ", effectivePath=" + getEffectivePath() + ", expiresDate=" + expiresDateStr + "]";
  }

  
}
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

final class CookieParsing {
  private static final Logger logger = Logger.getLogger(CookieParsing.class.getName());
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

  static Optional<java.util.Date> parseExpires(final String expiresStr) {
    Optional<java.util.Date> expiresDate = Optional.empty();
    synchronized (EXPIRES_FORMAT) {
      try {
        expiresDate = Optional.of(EXPIRES_FORMAT.parse(expiresStr));
      } catch (final Exception pe) {
        if (logger.isLoggable(Level.INFO)) {
          logger.log(Level.INFO, "saveCookie(): Bad date format: " + expiresStr + ". Will try again.", pe);
        }
        try {
          expiresDate = Optional.of(EXPIRES_FORMAT_BAK1.parse(expiresStr));
        } catch (final Exception pe2) {
          try {
            expiresDate = Optional.of(EXPIRES_FORMAT_BAK2.parse(expiresStr));
          } catch (final ParseException pe3) {
            logger.log(Level.SEVERE, "saveCookie(): Giving up on cookie date format: " + expiresStr, pe3);
          }
        }
      }
    }
    return expiresDate;
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

}

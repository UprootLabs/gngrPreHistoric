/*
    GNU GENERAL PUBLIC LICENSE
    Copyright (C) 2006 The Lobo Project

    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public
    License as published by the Free Software Foundation; either
    verion 2 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    General Public License for more details.

    You should have received a copy of the GNU General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Contact info: lobochief@users.sourceforge.net
 */
/*
 * Created on Jun 1, 2005
 */
package org.lobobrowser.request;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lobobrowser.store.RestrictedStore;
import org.lobobrowser.store.StorageManager;
import org.lobobrowser.util.Domains;

/**
 * @author J. H. S.
 */
public class CookieStore {
  private static final String COOKIE_PATH_PREFIX = ".W$Cookies/";
  private static final String COOKIE_PATH_PATTERN = "\\.W\\$Cookies/.*";
  private static final CookieStore instance = new CookieStore();

  private static final Logger logger = Logger.getLogger(CookieStore.class.getName());

  private final Map<String, Map<String, CookieValue>> transientMapByHost = new HashMap<>();

  private CookieStore() {
  }

  public static CookieStore getInstance() {
    return instance;
  }

  public void saveCookie(final URL url, final String cookieSpec) {
    this.saveCookie(url.getHost(), cookieSpec);
  }

  public void saveCookie(final String urlHostName, final String cookieSpec) {
    try {
      // TODO: SECURITY
      if (logger.isLoggable(Level.INFO)) {
        logger.info("saveCookie(): host=" + urlHostName + ",cookieSpec=[" + cookieSpec + "]");
      }
      final CookieDetails cookieDetails = CookieDetails.parseCookieSpec(cookieSpec);

      if (cookieDetails.name == null) {
        logger.log(Level.SEVERE, "saveCookie(): Invalid name in cookie spec from '" + urlHostName + "'");
        return;
      }

      if (!cookieDetails.isValidDomain(urlHostName)) {
        logger.log(Level.SEVERE, "saveCookie(): Invalid domain in cookie spec from '" + urlHostName + "'");
        return;
      }

      final String effectiveDomain = cookieDetails.getEffectiveDomain(urlHostName);
      final java.util.Date expiresDate = cookieDetails.getExpiresDate();
      // TODO: Secure
      this.saveCookie(effectiveDomain, cookieDetails.getEffectivePath(), cookieDetails.name, expiresDate, cookieDetails.value);
    } catch (final ParseException pe3) {
      logger.log(Level.SEVERE, "saveCookie(): Giving up on cookie date format: " + cookieSpec, pe3);
      return;
    }
  }

  private void saveCookie(final String domain, final String path, final String name, final java.util.Date expires, final String value) {
    // TODO: SECURITY
    if (logger.isLoggable(Level.INFO)) {
      logger.info("saveCookie(): domain=" + domain + ",name=" + name + ",expires=" + expires + ",value=[" + value + "].");
    }
    final Long expiresLong = expires == null ? null : expires.getTime();
    final CookieValue cookieValue = new CookieValue(value, path, expiresLong);
    synchronized (this) {
      // Always save a transient cookie. It acts as a cache.
      Map<String, CookieValue> hostMap = this.transientMapByHost.get(domain);
      if (hostMap == null) {
        hostMap = new HashMap<>(2);
        this.transientMapByHost.put(domain, hostMap);
      }
      hostMap.put(name, cookieValue);
    }
    if (expiresLong != null) {
      try {
        final RestrictedStore store = StorageManager.getInstance().getRestrictedStore(domain, true);
        store.saveObject(getPathFromCookieName(name), cookieValue);
      } catch (final IOException ioe) {
        logger.log(Level.WARNING, "saveCookie(): Unable to save cookie named '" + name + "' with domain '" + domain + "'", ioe);
      }
    }
  }

  private static String getPathFromCookieName(final String cookieName) {
    return COOKIE_PATH_PREFIX + cookieName;
  }

  private static String getCookieNameFromPath(final String path) {
    if (!path.startsWith(COOKIE_PATH_PREFIX)) {
      throw new IllegalArgumentException("Invalid path: " + path);
    }
    return path.substring(COOKIE_PATH_PREFIX.length());
  }

  /**
   * Gets cookies belonging exactly to the host name given, not to a broader
   * domain.
   */
  private Collection<Cookie> getCookiesStrict(final String hostName, String path) {
    if (path == null || path.length() == 0) {
      path = "/";
    }
    final boolean liflag = logger.isLoggable(Level.INFO);
    final Collection<Cookie> cookies = new LinkedList<>();
    final Set<String> transientCookieNames = new HashSet<>();
    synchronized (this) {
      final Map<String, CookieValue> hostMap = this.transientMapByHost.get(hostName);
      if (hostMap != null) {
        final Iterator<Map.Entry<String, CookieValue>> i = hostMap.entrySet().iterator();
        while (i.hasNext()) {
          final Map.Entry<String, CookieValue> entry = i.next();
          final CookieValue cookieValue = entry.getValue();
          if (cookieValue.isExpired()) {
            if (liflag) {
              logger.info("getCookiesStrict(): Cookie " + entry.getKey() + " from " + hostName + " expired: " + cookieValue.getExpires());
            }
          } else {
            if (path.startsWith(cookieValue.getPath())) {
              final String cookieName = entry.getKey();
              transientCookieNames.add(cookieName);
              cookies.add(new Cookie(cookieName, cookieValue.getValue()));
            } else {
              if (liflag) {
                logger.info("getCookiesStrict(): Skipping cookie " + cookieValue + " since it does not match path " + path);
              }
            }
          }
        }
      }
    }
    try {
      final RestrictedStore store = StorageManager.getInstance().getRestrictedStore(hostName, false);
      if (store != null) {
        Collection<String> paths;
        paths = store.getPaths(COOKIE_PATH_PATTERN);
        final Iterator<String> pathsIterator = paths.iterator();
        while (pathsIterator.hasNext()) {
          final String filePath = pathsIterator.next();
          final String cookieName = getCookieNameFromPath(filePath);
          if (!transientCookieNames.contains(cookieName)) {
            final CookieValue cookieValue = (CookieValue) store.retrieveObject(filePath);
            if (cookieValue != null) {
              if (cookieValue.isExpired()) {
                if (logger.isLoggable(Level.INFO)) {
                  logger.info("getCookiesStrict(): Cookie " + cookieName + " from " + hostName + " expired: " + cookieValue.getExpires());
                }
                store.removeObject(filePath);
              } else {
                if (path.startsWith(cookieValue.getPath())) {
                  // Found one that is not in main memory. Cache it.
                  synchronized (this) {
                    Map<String, CookieValue> hostMap = this.transientMapByHost.get(hostName);
                    if (hostMap == null) {
                      hostMap = new HashMap<>();
                      this.transientMapByHost.put(hostName, hostMap);
                    }
                    hostMap.put(cookieName, cookieValue);
                  }
                  // Now add cookie to the collection.
                  cookies.add(new Cookie(cookieName, cookieValue.getValue()));
                } else {
                  if (logger.isLoggable(Level.INFO)) {
                    logger.info("getCookiesStrict(): Skipping cookie " + cookieValue + " since it does not match path " + path);
                  }
                }
              }
            } else {
              logger.warning("getCookiesStrict(): Expected to find cookie named " + cookieName + " but file is missing.");
            }
          }
        }
      }
    } catch (final IOException ioe) {
      logger.log(Level.SEVERE, "getCookiesStrict()", ioe);
    } catch (final ClassNotFoundException cnf) {
      logger.log(Level.SEVERE, "getCookiesStrict(): Possible engine versioning error.", cnf);
    }
    return cookies;
  }

  public Collection<Cookie> getCookies(final String hostName, final String path) {
    // Security provided by RestrictedStore.
    final Collection<String> possibleDomains = DomainValidation.getPossibleDomains(hostName);
    final Collection<Cookie> cookies = new LinkedList<>();
    for (final String domain : possibleDomains) {
      cookies.addAll(this.getCookiesStrict(domain, path));
    }
    if (logger.isLoggable(Level.INFO)) {
      logger.info("getCookies(): For host=" + hostName + ", found " + cookies.size() + " cookies: " + cookies);
    }
    return cookies;
  }
}

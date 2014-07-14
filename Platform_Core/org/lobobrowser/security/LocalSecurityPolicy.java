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
 * Created on May 31, 2005
 */
package org.lobobrowser.security;

import java.security.*;
import java.lang.reflect.ReflectPermission;
import java.net.*;
import java.util.*;
import java.io.*;
import java.awt.*;

import javax.net.ssl.SSLPermission;

import org.lobobrowser.main.ExtensionManager;
import org.lobobrowser.store.StorageManager;
import org.lobobrowser.util.*;
import org.lobobrowser.util.io.Files;

public class LocalSecurityPolicy extends Policy {
  /**
   * Directory where Lobo should save files. Any files saved here have
   * privileges of a remote file.
   */
  public static final File STORE_DIRECTORY;

  private static final String DEFAULT_PROFILE = "default";
  private static final String STORE_DIR_NAME = ".gngr";
  private static final String STORE_DIRECTORY_CANONICAL;
  private static final LocalSecurityPolicy instance = new LocalSecurityPolicy();
  private static final Collection<Permission> BASE_PRIVILEGE = new LinkedList<>();

  static {
    final File homeDir = new File(System.getProperty("user.home"));
    final File settingsDir = Files.joinPaths(homeDir, STORE_DIR_NAME, DEFAULT_PROFILE);
    STORE_DIRECTORY = settingsDir;
    String settingsCanonical = "";
    try {
      settingsCanonical = settingsDir.getCanonicalPath();
    } catch (final IOException ioe) {
      ioe.printStackTrace(System.err);
    }
    STORE_DIRECTORY_CANONICAL = settingsCanonical;

    final Collection<Permission> permissions = BASE_PRIVILEGE;

    // //Note: This means extensions have access to private field values at the moment.
    // //Required by JavaFX runtime at the time of this writing.
    // permissions.add(new java.lang.reflect.ReflectPermission("suppressAccessChecks"));

    permissions.add(new PropertyPermission("*", "read,write"));
    permissions.add(new AWTPermission("*"));
    permissions.add(new HistoryPermission());
    permissions.add(new SocketPermission("*", "connect,resolve,listen,accept"));
    permissions.add(new RuntimePermission("createClassLoader"));
    permissions.add(new RuntimePermission("getClassLoader"));
    permissions.add(new RuntimePermission("exitVM"));
    permissions.add(new RuntimePermission("setIO"));
    permissions.add(new RuntimePermission("setContextClassLoader"));
    permissions.add(new RuntimePermission("enableContextClassLoaderOverride"));
    permissions.add(new RuntimePermission("setFactory"));
    permissions.add(new RuntimePermission("accessClassInPackage.*"));
    permissions.add(new RuntimePermission("defineClassInPackage.*"));
    permissions.add(new RuntimePermission("accessDeclaredMembers"));
    permissions.add(new RuntimePermission("getStackTrace"));
    permissions.add(new RuntimePermission("preferences"));
    permissions.add(new RuntimePermission("modifyThreadGroup"));
    permissions.add(new RuntimePermission("getProtectionDomain"));
    permissions.add(new RuntimePermission("shutdownHooks"));
    permissions.add(new RuntimePermission("modifyThread"));
    permissions.add(new RuntimePermission("com.sun.media.jmc.accessMedia"));
    // loadLibrary necessary in Java 6, in particular loadLibrary.sunmscapi.
    permissions.add(new RuntimePermission("loadLibrary.*"));
    permissions.add(new NetPermission("setDefaultAuthenticator"));
    permissions.add(new NetPermission("setCookieHandler"));
    permissions.add(new NetPermission("specifyStreamHandler"));
    permissions.add(new SSLPermission("setHostnameVerifier"));
    permissions.add(new SSLPermission("getSSLSessionContext"));
    permissions.add(new SecurityPermission("putProviderProperty.*"));
    permissions.add(new SecurityPermission("insertProvider.*"));
    permissions.add(new SecurityPermission("removeProvider.*"));
    permissions.add(new java.util.logging.LoggingPermission("control", null));
    permissions.add(GenericLocalPermission.EXT_GENERIC);

    final String recursiveSuffix = File.separator + "-";
    // Note: execute needed to launch external browser.
    // permissions.add(new FilePermission("<<ALL FILES>>", "read,write,delete,execute"));

    System.out.println("System protection domain: " + System.class.getProtectionDomain());

    // This is to allow native libraries to be loaded by JDK classes.
    // TODO: This could be perhaps reduced to paths found in "java.library.path"
    permissions.add(new FilePermission(System.getProperty("java.home") + recursiveSuffix, "read,execute"));

    Arrays.stream(ExtensionManager.getExtDirs()).forEach(f -> {
      permissions.add(new FilePermission(f.getAbsolutePath() + recursiveSuffix, "read"));
    });
    Arrays.stream(ExtensionManager.getExtFiles()).forEach(f -> {
      permissions.add(new FilePermission(f.getAbsolutePath() + recursiveSuffix, "read"));
    });
    permissions.add(new FilePermission(STORE_DIRECTORY_CANONICAL + recursiveSuffix, "read, write, delete"));
  }

  /**
   * Adds permissions to the base set of permissions assigned to privileged
   * code, i.e. code loaded from the local system rather than a remote location.
   * This method must be called before a security manager has been set.
   * 
   * @param permission
   *          A <code>Permission<code> instance.
   */
  public static void addPrivilegedPermission(final Permission permission) {
    final SecurityManager sm = System.getSecurityManager();
    if (sm != null) {
      throw new java.lang.SecurityException("Call this method before the sercurity manager is set.");
    }
    BASE_PRIVILEGE.add(permission);
  }

  /**
	 * 
	 */
  private LocalSecurityPolicy() {
  }

  public static LocalSecurityPolicy getInstance() {
    return instance;
  }

  public static boolean hasHost(final java.net.URL url) {
    final String host = url.getHost();
    return host != null && !"".equals(host);
  }

  public static boolean isLocal(final java.net.URL url) {
    // Should return true only if we are sure
    // the file has either been downloaded by
    // the user, was distributed with the OS,
    // or was distributed with the browser.
    if (url == null) {
      return false;
    }
    final String scheme = url.getProtocol();
    if ("http".equalsIgnoreCase(scheme)) {
      return false;
    } else if ("file".equalsIgnoreCase(scheme)) {
      if (hasHost(url)) {
        return false;
      }
      // Files under the settings directory (e.g. cached JARs)
      // are considered remote.
      final String filePath = url.getPath();
      final Boolean result = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
        public Boolean run() {
          final File file = new File(filePath);
          try {
            final String canonical = file.getCanonicalPath();
            return !canonical.startsWith(STORE_DIRECTORY_CANONICAL);
          } catch (final java.io.IOException ioe) {
            ioe.printStackTrace(System.err);
            return false;
          }
        }
      });
      return result.booleanValue();
    } else if ("jar".equalsIgnoreCase(scheme)) {
      final String path = url.getPath();
      final int emIdx = path.lastIndexOf('!');
      final String subUrlString = emIdx == -1 ? path : path.substring(0, emIdx);
      try {
        final URL subUrl = new URL(subUrlString);
        return isLocal(subUrl);
      } catch (final java.net.MalformedURLException mfu) {
        return false;
      }
    } else {
      return false;
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.security.Policy#getPermissions(java.security.CodeSource)
   */
  public PermissionCollection getPermissions(final CodeSource codesource) {
    // TODO: Important: This was required after switching to JDK Rhino. This
    // method gets called twice:
    // once with proper codesource and once with null. The second call needs
    // accessClassInPackage.sun.org.mozilla.javascript.internal.
    if (codesource == null) {
      final Permissions permissions = new Permissions();
      // Update: We are using Mozilla rhino latest version, and this is not required anymore
      // But some permission has to be returned.
      // permissions.add(new RuntimePermission("accessClassInPackage.sun.org.mozilla.javascript.internal"));
      // System.err.println("No Codesource:");
      // Thread.dumpStack();
      //permissions.add(new RuntimePermission("setContextClassLoader"));
      for (final Permission p : BASE_PRIVILEGE) {
        permissions.add(p);
      }
      permissions.add(StoreHostPermission.forHost("localhost"));
      return permissions;
    }

    if (codesource.getLocation().getPath().endsWith("jooq-3.4.0.jar")) {
      final Permissions permissions = new Permissions();
      // TODO: This permission is very dangerous. See https://github.com/jOOQ/jOOQ/issues/3392
      permissions.add(new ReflectPermission("suppressAccessChecks"));
      return permissions;
    }

    if (codesource.getLocation().getPath().endsWith("h2-1.4.179.jar")) {
      final Permissions permissions = new Permissions();
      try {
        final String userDBPath = StorageManager.getInstance().userDBPath;
        permissions.add(new FilePermission(STORE_DIRECTORY_CANONICAL, "read"));
        permissions.add(new FilePermission(userDBPath + ".mv.db", "read, write, delete"));
        permissions.add(new FilePermission(userDBPath + ".trace.db", "read, write, delete"));
        return permissions;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    final URL location = codesource.getLocation();
    if (location == null) {
      throw new AccessControlException("No location for codesource=" + codesource);
    }
    final boolean isLocal = isLocal(location);
    final Permissions permissions = new Permissions();
    if (isLocal) {
      for (final Permission p : BASE_PRIVILEGE) {
        permissions.add(p);
      }
      // Custom permissions
      permissions.add(StoreHostPermission.forURL(location));
      permissions.add(new RuntimePermission("com.sun.media.jmc.accessMedia"));
    } else {
      permissions.add(new PropertyPermission("java.version", "read"));
      permissions.add(new PropertyPermission("os.name", "read"));
      permissions.add(new PropertyPermission("line.separator", "read"));
      permissions.add(new SocketPermission(location.getHost(), "connect,resolve"));

      // TODO: Security: This permission should not be given, but it's required
      // by compiled JavaFX runtime at the moment (2/20/2008).
      // permissions.add(new AWTPermission("accessEventQueue"));

      final String hostName = location.getHost();
      // Get possible cookie domains for current location
      // and allow managed store access there.
      final Collection<String> domains = Domains.getPossibleDomains(hostName);
      domains.forEach(domain -> permissions.add(StoreHostPermission.forHost(domain)));
    }
    return permissions;
  }

}

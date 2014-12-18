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
 * Created on Mar 5, 2005
 */
package org.lobobrowser.main;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLStreamHandler;
import java.security.AccessController;
import java.security.Permission;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EventObject;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocketFactory;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.lobobrowser.gui.ConsoleModel;
import org.lobobrowser.gui.DefaultWindowFactory;
import org.lobobrowser.gui.FramePanel;
import org.lobobrowser.request.AuthenticatorImpl;
import org.lobobrowser.request.DomainValidation;
import org.lobobrowser.request.NOPCookieHandlerImpl;
import org.lobobrowser.security.LocalSecurityManager;
import org.lobobrowser.security.LocalSecurityPolicy;
import org.lobobrowser.store.StorageManager;
import org.lobobrowser.util.GenericEventListener;
import org.lobobrowser.util.SimpleThreadPool;
import org.lobobrowser.util.SimpleThreadPoolTask;
import org.lobobrowser.util.Urls;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;

;
/**
 * A singleton class that is used to initialize a browser session in the current
 * JVM. It can also be used to open a browser window.
 *
 * @see #getInstance()
 */
public class PlatformInit {
  private static final String NATIVE_DIR_NAME = "native";
  private static final long THIRTY_DAYS_MILLIS = 30 * 24 * 60 * 60 * 1000L;
  private final SimpleThreadPool threadExecutor;

  // private final GeneralSettings generalSettings;

  private PlatformInit() {
    // TODO: Research a better way to configure the thread pool
    // TODO: Use thread pools available in JDK?
    this.threadExecutor = new SimpleThreadPool("MainThreadPool", 2, 3, 60 * 1000);

    // One way to avoid a security exception.
    // this.generalSettings = GeneralSettings.getInstance();
  }

  /**
   * Intializes security by installing a security policy and a security manager.
   * Programs that use the browser API should invoke this method (or
   * {@link #init(boolean, boolean) init}) to prevent web content from having
   * full access to the user's computer.
   *
   * @see #addPrivilegedPermission(Permission)
   */
  public void initSecurity() {
    // Set security policy and manager (essential)
    Policy.setPolicy(LocalSecurityPolicy.getInstance());
    System.setSecurityManager(new LocalSecurityManager());
  }

  /**
   * Initializes the global URLStreamHandlerFactory.
   * <p>
   * This method is invoked by {@link #init(boolean, boolean)}.
   */
  public void initProtocols(final SSLSocketFactory sslSocketFactory) {
    // Configure URL protocol handlers
    final PlatformStreamHandlerFactory factory = PlatformStreamHandlerFactory.getInstance();
    URL.setURLStreamHandlerFactory(factory);
    final OkHttpClient okHttpClient = new OkHttpClient();
    // HttpsURLConnection.setDefaultSSLSocketFactory(sslSocketFactory);
    okHttpClient.setSslSocketFactory(sslSocketFactory);
    okHttpClient.setFollowRedirects(false);
    factory.addFactory(new OkUrlFactory(okHttpClient));
    factory.addFactory(new LocalStreamHandlerFactory());
  }

  /**
   * Initializes the HTTP authenticator and the cookie handler. This is
   * essential for the browser to work properly.
   * <p>
   * This method is invoked by {@link #init(boolean, boolean)}.
   */
  public void initHTTP() {
    // Configure authenticator
    Authenticator.setDefault(new AuthenticatorImpl());
    // Configure cookie handler
    // CookieHandler.setDefault(new CookieHandlerImpl());
    CookieHandler.setDefault(new NOPCookieHandlerImpl());
  }

  /**
   * Initializes the Swing look & feel.
   */
  public void initLookAndFeel() throws Exception {
    // Set appropriate Swing L&F
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
  }

  public boolean isCodeLocationDirectory() {
    final URL codeLocation = this.getClass().getProtectionDomain().getCodeSource().getLocation();
    return Urls.isLocalFile(codeLocation) && codeLocation.getPath().endsWith("/");
  }

  /**
   * Resets standard output and error streams so they are redirected to the
   * browser console.
   *
   * @see ConsoleModel
   */
  public void initConsole() {
    final java.io.PrintStream oldOut = System.out;
    final ConsoleModel standard = ConsoleModel.getStandard();
    final java.io.PrintStream ps = standard.getPrintStream();
    System.setOut(ps);
    System.setErr(ps);
    if (this.isCodeLocationDirectory()) {
      // Should only be shown when running from Eclipse.
      oldOut
      .println("WARNING: initConsole(): Switching standard output and standard error to application console. If running EntryPoint, pass -debug to avoid this.");
    }
  }

  public boolean debugOn = false;

  /**
   * Initializes platform logging. Note that this method is not implicitly
   * called by {@link #init(boolean, boolean)}.
   *
   * @param debugOn
   *          Debugging mode. This determines which one of two different logging
   *          configurations is used.
   */
  public void initLogging(final boolean debugOn) throws Exception {
    this.debugOn = debugOn;

    // Set up debugging & console
    final String loggingToken = debugOn ? "logging-debug" : "logging";
    java.io.InputStream in = this.getClass().getResourceAsStream("/properties/" + loggingToken + ".properties");
    if (in == null) {
      in = this.getClass().getResourceAsStream("properties/" + loggingToken + ".properties");
      if (in == null) {
        throw new java.io.IOException("Unable to locate logging properties file.");
      }
    }
    try {
      java.util.logging.LogManager.getLogManager().readConfiguration(in);
    } finally {
      in.close();
    }
    // Configure log4j
    final Logger logger = Logger.getLogger(PlatformInit.class.getName());
    if (logger.isLoggable(Level.INFO)) {
      logger.warning("Entry(): Logger INFO level is enabled.");
      System.getProperties().forEach((k, v) -> logger.info("main(): " + k + "=" + v));
    }
  }

  /**
   * Initializes browser extensions. Invoking this method is essential to enable
   * the primary extension and all basic browser functionality. This method is
   * invoked by {@link #init(boolean, boolean)}.
   */
  public void initExtensions() {
    ExtensionManager.getInstance().initExtensions();
  }

  /**
   * Initializes the default window factory such that the JVM exits when all
   * windows created by the factory are closed by the user.
   */
  public void initWindowFactory(final boolean exitWhenAllWindowsAreClosed) {
    DefaultWindowFactory.getInstance().setExitWhenAllWindowsAreClosed(exitWhenAllWindowsAreClosed);
  }

  /**
   * Initializers the <code>java.library.path</code> property.
   * <p>
   * This method is called by {@link #init(boolean, boolean)}.
   *
   * @param dirName
   *          A directory name relative to the browser application directory.
   */
  public void initNative(final String dirName) {
    // TODO: What is the purpose of this function?
    final Optional<File> appDirOpt = this.getApplicationDirectory();
    if (appDirOpt.isPresent()) {
      final File nativeDir = new File(appDirOpt.get(), dirName);
      System.setProperty("java.library.path", nativeDir.getAbsolutePath());
    }
  }

  /**
   * Initializes some Java properties required by the browser.
   * <p>
   * This method is called by {@link #init(boolean, boolean)}.
   */
  public void initOtherProperties() {
    // Required for array serialization in Java 6.
    System.setProperty("sun.lang.ClassLoader.allowArraySyntax", "true");
    // Don't cache host lookups for ever
    System.setProperty("networkaddress.cache.ttl", "3600");
    System.setProperty("networkaddress.cache.negative.ttl", "1");

  }

  /**
   * Initializes security, protocols, look & feel, console, the default window
   * factory, extensions and <code>java.library.path</code>. This method should
   * be invoked before using other functionality in the browser API. If this
   * method is not called, at the very least {@link #initOtherProperties()},
   * {@link #initProtocols()} and {@link #initExtensions()} should be called.
   * <p>
   * Applications that need to install their own security manager and policy
   * should not call this method.
   *
   * @param exitWhenAllWindowsAreClosed
   *          Whether the JVM should exit when all windows created by the
   *          default window factory are closed.
   * @param initConsole
   *          If this parameter is <code>true</code>, standard output is
   *          redirected to a browser console. See
   *          {@link org.lobobrowser.gui.ConsoleModel}.
   * @see #initSecurity()
   * @see #initProtocols()
   * @see #initExtensions()
   */
  public void init(final boolean exitWhenAllWindowsAreClosed, final boolean initConsole, final SSLSocketFactory sslSocketFactory)
      throws Exception {
    checkReleaseDate();

    initOtherProperties();

    initNative(NATIVE_DIR_NAME);
    initSecurity();
    initProtocols(sslSocketFactory);
    initHTTP();
    initLookAndFeel();
    if (initConsole) {
      initConsole();
    }
    initWindowFactory(exitWhenAllWindowsAreClosed);
    initExtensions();
  }

  private void checkReleaseDate() {
    final InputStream relStream = getClass().getResourceAsStream("/properties/release.properties");
    final Properties relProps = new Properties();
    try {
      relProps.load(relStream);
      final String dateStr = relProps.getProperty("version.releaseDate");
      final SimpleDateFormat yyyyMMDDFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
      final Date releaseDate = yyyyMMDDFormat.parse(dateStr);
      final Date releaseDatePlus30Days = new Date(releaseDate.getTime() + THIRTY_DAYS_MILLIS);
      final Date currDate = new Date(System.currentTimeMillis());
      if (releaseDatePlus30Days.before(currDate)) {
        final String version = relProps.getProperty("version.string");
        final String checkForUpdatesMessage = "<html><h3><center>This version of gngr is old</center></h3><p>gngr "
            + version
            + "</p><p>Released on: "
            + releaseDate
            + "</p><p>This version is more than 30 days old and was not intended for long-time use.</p><p>Please check if a newer version is available on https://gngr.info</p></html>";
        JOptionPane.showMessageDialog(null, checkForUpdatesMessage);
      }
    } catch (IOException | ParseException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  /**
   * Opens a window and attempts to render the URL or path given.
   *
   * @param urlOrPath
   *          A URL or file path.
   * @throws MalformedURLException
   */
  public void launch(final String urlOrPath) throws MalformedURLException {
    final URL url = DomainValidation.guessURL(urlOrPath);
    FramePanel.openWindow(null, url, null, new Properties(), "GET", null);
  }

  /**
   * Opens as many browser windows as there are startup URLs in general
   * settings.
   *
   * @see org.lobobrowser.settings.GeneralSettings#getStartupURLs()
   * @throws MalformedURLException
   */
  public void launch() throws MalformedURLException {
    final SecurityManager sm = System.getSecurityManager();
    if (sm == null) {
      final Logger logger = Logger.getLogger(PlatformInit.class.getName());
      logger.warning("launch(): Security manager not set!");
    }
    /*
     * String[] startupURLs = this.generalSettings.getStartupURLs(); for(String
     * url : startupURLs) { this.launch(url); }
     */
    this.launch("about:welcome");
    // this.launch("http://localhost:8000/");
    // this.launch("http://localhost:8000/test_link.html");
    // this.launch("http://localhost:8000/request_permissions.html");
  }

  private boolean windowHasBeenShown = false;

  /**
   * Starts the browser by opening the URLs specified in the command-line
   * arguments provided. Non-option arguments are assumed to be URLs and opened
   * in separate windows. If no arguments are found, the method launches URLs
   * from general settings. This method will not return until at least one
   * window has been shown.
   *
   * @see org.lobobrowser.settings.GeneralSettings#getStartupURLs()
   */
  public void start(final String[] args) throws MalformedURLException {
    DefaultWindowFactory.getInstance().evtWindowShown.addListener(new GenericEventListener() {
      public void processEvent(final EventObject event) {
        synchronized (PlatformInit.this) {
          windowHasBeenShown = true;
          PlatformInit.this.notifyAll();
        }
      }
    });
    boolean launched = false;
    for (final String arg : args) {
      final String url = arg;
      if (!url.startsWith("-")) {
        try {
          launched = true;
          this.launch(url);
        } catch (final Exception err) {
          err.printStackTrace(System.err);
        }
      }
    }
    if (!launched) {
      this.launch();
    }
    synchronized (this) {
      while (!this.windowHasBeenShown) {
        try {
          this.wait();
        } catch (final InterruptedException ie) {
          // Ignore
        }
      }
    }
  }

  private static final PlatformInit instance = new PlatformInit();

  /**
   * Gets the singleton instance.
   */
  public static PlatformInit getInstance() {
    return instance;
  }

  /**
   * Performs some cleanup and then exits the JVM.
   */
  public static void shutdown() {
    AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
      try {
        ReuseManager.getInstance().shutdown();
        StorageManager.getInstance().shutdown();
      } catch (final Exception err) {
        err.printStackTrace(System.err);
      }
      System.exit(0);
      return null;
    });
  }

  /**
   * Adds one permission to the base set of permissions assigned to privileged
   * code, i.e. code loaded from the local system rather than a remote location.
   * This method must be called before a security manager has been set, that is,
   * before {@link #init(boolean, boolean)} or {@link #initSecurity()} are
   * invoked. The purpose of the method is to add permissions otherwise missing
   * from the security policy installed by this facility.
   *
   * @param permission
   *          A <code>Permission<code> instance.
   */
  public void addPrivilegedPermission(final Permission permission) {
    LocalSecurityPolicy.addPrivilegedPermission(permission);
  }

  public void scheduleTask(final SimpleThreadPoolTask task) {
    this.threadExecutor.schedule(task);
  }

  private File applicationDirectory;

  public Optional<File> getApplicationDirectory() {
    File appDir = this.applicationDirectory;
    if (appDir == null) {
      final java.security.ProtectionDomain pd = this.getClass().getProtectionDomain();
      final java.security.CodeSource cs = pd.getCodeSource();
      final java.net.URL url = cs.getLocation();
      if (url.getProtocol().equals("zipentry")) {
        return Optional.empty();
      }
      final String jarPath = url.getPath();
      File jarFile;
      try {
        jarFile = new File(url.toURI());
      } catch (final java.net.URISyntaxException use) {
        throw new IllegalStateException(use);
      } catch (final java.lang.IllegalArgumentException iae) {
        throw new IllegalStateException("Application code source apparently not a local JAR file: " + url
            + ". Only local JAR files are supported at the moment.", iae);
      }
      final File installDir = jarFile.getParentFile();
      if (installDir == null) {
        throw new IllegalStateException("Installation directory is missing. Startup JAR path is " + jarPath + ".");
      }
      if (!installDir.exists()) {
        throw new IllegalStateException("Installation directory not found. Startup JAR path is " + jarPath + ". Directory path is "
            + installDir.getAbsolutePath() + ".");
      }
      appDir = installDir;
      this.applicationDirectory = appDir;

      // Static logger should not be created in this class.
      final Logger logger = Logger.getLogger(this.getClass().getName());
      if (logger.isLoggable(Level.INFO)) {
        logger.info("getApplicationDirectory(): url=" + url + ",appDir=" + appDir);
      }
    }
    return Optional.of(appDir);
  }

  private static class LocalStreamHandlerFactory implements java.net.URLStreamHandlerFactory {
    public URLStreamHandler createURLStreamHandler(final String protocol) {
      if (protocol.equals("res")) {
        return new org.lobobrowser.protocol.res.Handler();
      } else if (protocol.equals("vc")) {
        return new org.lobobrowser.protocol.vc.Handler();
      } else {
        return null;
      }
    }
  }

}

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
package org.lobobrowser.main;

import java.awt.EventQueue;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.lobobrowser.clientlet.Clientlet;
import org.lobobrowser.clientlet.ClientletRequest;
import org.lobobrowser.clientlet.ClientletResponse;
import org.lobobrowser.ua.NavigationEvent;
import org.lobobrowser.ua.NavigationVetoException;
import org.lobobrowser.ua.NavigatorEventType;
import org.lobobrowser.ua.NavigatorExceptionEvent;
import org.lobobrowser.ua.NavigatorFrame;
import org.lobobrowser.ua.NavigatorWindow;
import org.lobobrowser.ua.RequestType;
import org.lobobrowser.util.JoinableTask;

/**
 * Manages platform extensions.
 */
public class ExtensionManager {
  private static final Logger logger = Logger.getLogger(ExtensionManager.class.getName());
  private static final ExtensionManager instance = new ExtensionManager();
  private static final String EXT_DIR_NAME = "ext";

  // Note: We do not synchronize around the extensions collection,
  // given that it is fully built in the constructor.
  private final Map<String, Extension> extensionById = new HashMap<>();
  private final SortedSet<Extension> extensions = new TreeSet<>();
  private final ArrayList<Extension> libraries = new ArrayList<>();

  private ExtensionManager() {
    this.createExtensions();
  }

  public static ExtensionManager getInstance() {
    // This security check should be enough, provided
    // ExtensionManager instances are not retained.
    final SecurityManager sm = System.getSecurityManager();
    if (sm != null) {
      sm.checkPermission(org.lobobrowser.security.GenericLocalPermission.EXT_GENERIC);
    }
    return instance;
  }

  private void createExtensions() {
    this.createExtensions(getExtDirs(), getExtFiles());
  }

  public static File[] getExtDirs() {
    File[] extDirs;
    final String extDirsProperty = System.getProperty("ext.dirs");
    if (extDirsProperty == null) {
      final File appDir = PlatformInit.getInstance().getApplicationDirectory();
      extDirs = new File[] { new File(appDir, EXT_DIR_NAME) };
    } else {
      final StringTokenizer tok = new StringTokenizer(extDirsProperty, ",");
      final ArrayList<File> extDirsList = new ArrayList<>();
      while (tok.hasMoreTokens()) {
        final String token = tok.nextToken();
        extDirsList.add(new File(token.trim()));
      }
      extDirs = extDirsList.toArray(new File[0]);
    }
    return extDirs;
  }

  public static File[] getExtFiles() {
    File[] extFiles;
    final String extFilesProperty = System.getProperty("ext.files");
    if (extFilesProperty == null) {
      extFiles = new File[0];
    } else {
      final StringTokenizer tok = new StringTokenizer(extFilesProperty, ",");
      final ArrayList<File> extFilesList = new ArrayList<>();
      while (tok.hasMoreTokens()) {
        final String token = tok.nextToken();
        extFilesList.add(new File(token.trim()));
      }
      extFiles = extFilesList.toArray(new File[0]);
    }
    return extFiles;
  }

  private void addExtension(final File file) throws java.io.IOException {
    if (!file.exists()) {
      logger.warning("addExtension(): File " + file + " does not exist.");
      return;
    }
    addExtension(new Extension(file));
  }

  private void addExtension(final Extension ei) {
    this.extensionById.put(ei.getId(), ei);
    if (ei.isLibraryOnly()) {
      if (logger.isLoggable(Level.FINE)) {
        logger.fine("createExtensions(): Loaded library (no lobo-extension.properties): " + ei);
      }
      libraries.add(ei);
    } else {
      if (logger.isLoggable(Level.FINE)) {
        logger.fine("createExtensions(): Loaded extension: " + ei);
      }
      extensions.add(ei);
    }
  }

  private void createExtensions(final File[] extDirs, final File[] extFiles) {
    final Collection<Extension> extensions = this.extensions;
    final Collection<Extension> libraries = this.libraries;
    final Map<String, Extension> extensionById = this.extensionById;
    extensions.clear();
    libraries.clear();
    extensionById.clear();
    for (final File extDir : extDirs) {
      if (!extDir.exists()) {
        logger.warning("createExtensions(): Directory '" + extDir + "' not found.");
        if (PlatformInit.getInstance().isCodeLocationDirectory()) {
          logger
              .warning("createExtensions(): The application code location is a directory, which means the application is probably being run from an IDE. Additional setup is required. Please refer to README.txt file.");
        }
        continue;
      }
      final File[] extRoots = extDir.listFiles(new ExtFileFilter());
      if (extRoots == null || extRoots.length == 0) {
        logger.warning("createExtensions(): No potential extensions found in " + extDir + " directory.");
        continue;
      }
      addAllFileExtensions(extRoots);
    }
    addAllFileExtensions(extFiles);

    if (this.extensionById.size() == 0) {
      logger.warning("createExtensions(): No extensions found. This is indicative of a setup error. Extension directories scanned are: "
          + Arrays.asList(extDirs) + ".");
    }

    // Create class loader for extension "libraries"
    final ArrayList<URL> libraryURLCollection = new ArrayList<>();
    for (final Extension ei : libraries) {
      try {
        libraryURLCollection.add(ei.getCodeSource());
      } catch (final java.net.MalformedURLException thrown) {
        logger.log(Level.SEVERE, "createExtensions()", thrown);
      }
    }
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("createExtensions(): Creating library class loader with URLs=[" + libraryURLCollection + "].");
    }
    loadExtensions(extensions, libraryURLCollection);
  }

  private void loadExtensions(final Collection<Extension> extensions,
      final ArrayList<URL> libraryURLCollection) {
    // Get the system class loader
    final ClassLoader rootClassLoader = this.getClass().getClassLoader();

    final URLClassLoader librariesCL = new URLClassLoader(libraryURLCollection.toArray(new URL[0]), rootClassLoader);

    // Initialize class loader in each extension, using librariesCL as
    // the parent class loader. Extensions are initialized in parallel.
    final Collection<JoinableTask> tasks = new ArrayList<>();
    final PlatformInit pm = PlatformInit.getInstance();
    for (final Extension ei : extensions) {
      final ClassLoader pcl = librariesCL;
      final Extension fei = ei;
      // Initialize rest of them in parallel.
      final JoinableTask task = new JoinableTask() {
        public void execute() {
          try {
            fei.initClassLoader(pcl);
          } catch (final Exception err) {
            logger.log(Level.WARNING, "Unable to create class loader for " + fei + ".", err);
          }
        }

        public String toString() {
          return "createExtensions:" + fei;
        }
      };
      tasks.add(task);
      pm.scheduleTask(task);
    }

    // Join tasks to make sure all extensions are initialized at this point.
    for (final JoinableTask task : tasks) {
      try {
        task.join();
      } catch (final InterruptedException ie) {
        // TODO
        // ignore
      }
    }
  }

  private void addAllFileExtensions(final File[] extRoots) {
    for (final File file : extRoots) {
      try {
        this.addExtension(file);
      } catch (final IOException ioe) {
        logger.log(Level.WARNING, "createExtensions(): Unable to load '" + file + "'.", ioe);
      }
    }
  }

  public ClassLoader getClassLoader(final String extensionId) {
    final Extension ei = this.extensionById.get(extensionId);
    if (ei != null) {
      return ei.getClassLoader();
    } else {
      return null;
    }
  }

  public void initExtensions() {
    final Collection<JoinableTask> tasks = new ArrayList<>();
    final PlatformInit pm = PlatformInit.getInstance();
    for (final Extension ei : this.extensions) {
      final Extension fei = ei;
      final JoinableTask task = new JoinableTask() {
        public void execute() {
          fei.initExtension();
        }

        public String toString() {
          return "initExtensions:" + fei;
        }
      };
      tasks.add(task);
      pm.scheduleTask(task);
    }
    // Join all tasks before returning
    for (final JoinableTask task : tasks) {
      try {
        task.join();
      } catch (final InterruptedException ie) {
        // ignore
      }
    }
  }

  public void initExtensionsWindow(final NavigatorWindow context) {
    // This must be done sequentially due to menu lookup infrastructure.
    for (final Extension ei : this.extensions) {
      try {
        ei.initExtensionWindow(context);
      } catch (final Exception err) {
        logger.log(Level.SEVERE, "initExtensionsWindow(): Extension could not properly initialize a new window.", err);
      }
    }
  }

  public void shutdownExtensionsWindow(final NavigatorWindow context) {
    // This must be done sequentially due to menu lookup infrastructure.
    for (final Extension ei : this.extensions) {
      try {
        ei.shutdownExtensionWindow(context);
      } catch (final Exception err) {
        logger.log(Level.SEVERE, "initExtensionsWindow(): Extension could not properly process window shutdown.", err);
      }
    }
  }

  public Clientlet getClientlet(final ClientletRequest request, final ClientletResponse response) {
    final Collection<Extension> extensions = this.extensions;
    // Call all plugins once to see if they can select the response.
    for (final Extension ei : extensions) {
      try {
        final Clientlet clientlet = ei.getClientlet(request, response);
        if (clientlet != null) {
          return clientlet;
        }
      } catch (final Exception thrown) {
        logger.log(Level.SEVERE, "getClientlet(): Extension " + ei + " threw exception.", thrown);
      }
    }

    // None handled it. Call the last resort handlers in reverse order.
    for (final Extension ei : (Collection<Extension>) org.lobobrowser.util.CollectionUtilities.reverse(extensions)) {
      try {
        final Clientlet clientlet = ei.getLastResortClientlet(request, response);
        if (clientlet != null) {
          return clientlet;
        }
      } catch (final Exception thrown) {
        logger.log(Level.SEVERE, "getClientlet(): Extension " + ei + " threw exception.", thrown);
      }
    }
    return null;
  }

  public void handleError(final NavigatorFrame frame, final ClientletResponse response, final Throwable exception, final RequestType requestType) {
    final NavigatorExceptionEvent event = new NavigatorExceptionEvent(this, NavigatorEventType.ERROR_OCCURRED, frame, response, exception, requestType);
    EventQueue.invokeLater(() -> {
      final Collection<Extension> ext = extensions;
      // Call all plugins once to see if they can select the response.
      boolean dispatched = false;
      for (final Extension ei : ext) {
        if (ei.handleError(event)) {
          dispatched = true;
        }
      }
      if (!dispatched && logger.isLoggable(Level.INFO)) {
        logger.log(Level.WARNING, "No error handlers found for error that occurred while processing response=[" + response + "].",
            exception);
      }
    });
  }

  public void dispatchBeforeNavigate(final NavigationEvent event) throws NavigationVetoException {
    for (final Extension ei : extensions) {
      try {
        ei.dispatchBeforeLocalNavigate(event);
      } catch (final NavigationVetoException nve) {
        throw nve;
      } catch (final Exception other) {
        logger.log(Level.SEVERE, "dispatchBeforeNavigate(): Extension threw an unexpected exception.", other);
      }
    }
  }

  public void dispatchBeforeLocalNavigate(final NavigationEvent event) throws NavigationVetoException {
    for (final Extension ei : extensions) {
      try {
        ei.dispatchBeforeLocalNavigate(event);
      } catch (final NavigationVetoException nve) {
        throw nve;
      } catch (final Exception other) {
        logger.log(Level.SEVERE, "dispatchBeforeLocalNavigate(): Extension threw an unexpected exception.", other);
      }
    }
  }

  public void dispatchBeforeWindowOpen(final NavigationEvent event) throws NavigationVetoException {
    for (final Extension ei : extensions) {
      try {
        ei.dispatchBeforeWindowOpen(event);
      } catch (final NavigationVetoException nve) {
        throw nve;
      } catch (final Exception other) {
        logger.log(Level.SEVERE, "dispatchBeforeWindowOpen(): Extension threw an unexpected exception.", other);
      }
    }
  }

  public URLConnection dispatchPreConnection(URLConnection connection) {
    for (final Extension ei : extensions) {
      try {
        connection = ei.dispatchPreConnection(connection);
      } catch (final Exception other) {
        logger.log(Level.SEVERE, "dispatchPreConnection(): Extension threw an unexpected exception.", other);
      }
    }
    return connection;
  }

  public URLConnection dispatchPostConnection(URLConnection connection) {
    for (final Extension ei : extensions) {
      try {
        connection = ei.dispatchPostConnection(connection);
      } catch (final Exception other) {
        logger.log(Level.SEVERE, "dispatchPostConnection(): Extension threw an unexpected exception.", other);
      }
    }
    return connection;
  }

  private static class ExtFileFilter implements FileFilter {
    public boolean accept(final File file) {
      return file.isDirectory() || file.getName().toLowerCase().endsWith(".jar");
    }
  }
}

package org.lobobrowser.security;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.lobobrowser.ua.NavigationEntry;
import org.lobobrowser.ua.NavigatorFrame;
import org.lobobrowser.ua.UserAgentContext;
import org.lobobrowser.ua.UserAgentContext.Request;
import org.lobobrowser.ua.UserAgentContext.RequestKind;
import org.lobobrowser.util.gui.GUITasks;

public final class RequestManager {
  private static final Logger logger = Logger.getLogger(RequestManager.class.getName());

  private final NavigatorFrame frame;

  public RequestManager(final NavigatorFrame frame) {
    this.frame = frame;
  }

  private static class RequestCounters {
    private final int counters[] = new int[UserAgentContext.RequestKind.values().length];

    public void updateCounts(final RequestKind kind) {
      counters[kind.ordinal()]++;
    }

    @Override
    public String toString() {
      return Arrays.stream(RequestKind.values())
          .map(kind -> String.format(" %2d", counters[kind.ordinal()])).reduce((e, a) -> e + a)
          .orElse("");
    }
  }

  private Map<String, RequestCounters> hostToCounterMap = new HashMap<>();
  private Optional<PermissionSystem> permissionSystemOpt = Optional.empty();

  private synchronized void updateCounter(final Request request) {
    final String host = request.url.getHost().toLowerCase();
    ensureHostInCounter(host);
    hostToCounterMap.get(host).updateCounts(request.kind);
  }

  private void ensureHostInCounter(final String host) {
    if (!hostToCounterMap.containsKey(host)) {
      hostToCounterMap.put(host, new RequestCounters());
    }
  }

  private Optional<NavigationEntry> getFrameNavigationEntry() {
    final NavigationEntry currentNavigationEntry = frame.getCurrentNavigationEntry();
    return Optional.ofNullable(currentNavigationEntry);
  }

  private Optional<String> getFrameHost() {
    return getFrameNavigationEntry().map(e -> e.getUrl().getHost().toLowerCase());
  }

  private Optional<URL> getFrameURL() {
    return getFrameNavigationEntry().map(e -> e.getUrl());
  }

  private Request rewriteRequest(final Request request) {
    final Optional<String> frameHostOpt = getFrameHost();
    if (request.url.getProtocol().equals("data") && frameHostOpt.isPresent()) {
      try {
        return new Request(new URL("data", frameHostOpt.get(), "someDataPath"), request.kind);
      } catch (final MalformedURLException e) {
        throw new RuntimeException("Couldn't rewrite data request");
      }
    } else {
      return request;
    }
  }

  public boolean isRequestPermitted(final Request request) {
    final Request finalRequest = rewriteRequest(request);

    if (permissionSystemOpt.isPresent()) {
      final Boolean permitted = permissionSystemOpt.map(p -> p.isRequestPermitted(finalRequest)).orElse(false);
      updateCounter(finalRequest);
      // dumpCounters();
      return permitted;
    } else {
      logger.severe("Unexpected permission system state. Request without context!");
      return false;
    }
  }

  private void setupPermissionSystem(final String frameHost) {
    final RequestRuleStore permissionStore = RequestRuleStore.getStore();
    final PermissionSystem system = new PermissionSystem(frameHost, permissionStore);

    // Prime the boards with atleast one row
    system.getLastBoard().getRow(frameHost);

    permissionSystemOpt = Optional.of(system);
  }

  private synchronized void dumpCounters() {
    // Headers
    System.out.print(String.format("%30s  ", ""));
    getRequestKindNames().forEach(kindName -> System.out.print(" " + kindName.substring(0, 2)));
    System.out.println("");

    // Table rows
    hostToCounterMap.forEach((host, counters) -> {
      System.out.println(String.format("%30s: %s", "[" + host + "]", counters));
    });
  }

  private static Stream<String> getRequestKindNames() {
    return Arrays.stream(RequestKind.values()).map(kind -> kind.shortName);
  }

  public synchronized void reset(final URL frameUrl) {
    hostToCounterMap = new HashMap<>();
    final String frameHostOrig = frameUrl.getHost();
    final String frameHost = frameHostOrig == null ? "" : frameHostOrig.toLowerCase();
    ensureHostInCounter(frameHost);
    setupPermissionSystem(frameHost);
  }

  public void manageRequests(final JComponent initiatorComponent) {
    // permissionSystemOpt.ifPresent(r -> r.dump());
    final ManageDialog dlg = new ManageDialog(new JFrame(), getFrameURL().map(u -> u.toExternalForm()).orElse("Empty!"), initiatorComponent);
    dlg.setVisible(true);
  }

  private synchronized String[][] getRequestData() {
    // hostToCounterMap.keySet().stream().forEach(System.out::println);

    return hostToCounterMap.entrySet().stream().map(entry -> {
      final List<String> rowElements = new LinkedList<>();
      rowElements.add(entry.getKey());
      Arrays.stream(entry.getValue().counters).forEach(c -> rowElements.add(Integer.toString(c)));

      return rowElements.toArray(new String[0]);
    }).toArray(String[][]::new);
  }

  private static String[] getColumnNames() {
    final List<String> kindNames = getRequestKindNames().collect(Collectors.toList());
    kindNames.add(0, "All");
    return kindNames.toArray(new String[0]);
  }

  public final class ManageDialog extends JDialog implements ActionListener {
    private final JComponent initiator;

    public ManageDialog(final JFrame parent, final String title, final JComponent initiator) {
      super(parent, title, true);
      this.initiator = initiator;
      setUndecorated(true);
      if (parent != null) {
        final Dimension parentSize = parent.getSize();
        final Point p = parent.getLocation();
        setLocation(p.x + (parentSize.width / 4), p.y + (parentSize.height / 4));
      }

      final JComponent table = PermissionTable.makeTable(permissionSystemOpt.get(), getColumnNames(), getRequestData());
      final JScrollPane scrollTablePane = new JScrollPane(table);

      getContentPane().add(scrollTablePane);

      final JPanel buttonPane = new JPanel();
      final JButton button = new JButton("OK");
      buttonPane.add(button);
      button.addActionListener(this);
      getContentPane().add(buttonPane, BorderLayout.SOUTH);
      setDefaultCloseOperation(DISPOSE_ON_CLOSE);
      addWindowListener(new WindowListenerImpl());

      pack();
      updateLocation();
      initiator.addHierarchyBoundsListener(new HierarchyBoundsListener() {

        @Override
        public void ancestorResized(final HierarchyEvent e) {
          updateLocation();
        }

        @Override
        public void ancestorMoved(final HierarchyEvent e) {
          updateLocation();
        }
      });

      GUITasks.addEscapeListener(this);
    }

    public void actionPerformed(final ActionEvent e) {
      setVisible(false);
      dispose();
    }

    private void updateLocation() {
      final Point locationOnScreen = initiator.getLocationOnScreen();
      locationOnScreen.translate(initiator.getWidth() - getWidth(), initiator.getHeight());
      setLocation(locationOnScreen);
    }

    private final class WindowListenerImpl implements WindowListener {
      @Override
      public void windowOpened(final WindowEvent e) {
      }

      @Override
      public void windowIconified(final WindowEvent e) {
      }

      @Override
      public void windowDeiconified(final WindowEvent e) {
      }

      @Override
      public void windowDeactivated(final WindowEvent e) {
      }

      @Override
      public void windowClosing(final WindowEvent e) {
      }

      @Override
      public void windowClosed(final WindowEvent e) {
        frame.reload();
      }

      @Override
      public void windowActivated(final WindowEvent e) {
      }
    }

  }

}

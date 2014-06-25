package org.lobobrowser.security;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import org.lobobrowser.security.RequestRule.RequestRuleSet;
import org.lobobrowser.ua.NavigationEntry;
import org.lobobrowser.ua.NavigatorFrame;
import org.lobobrowser.ua.UserAgentContext;
import org.lobobrowser.ua.UserAgentContext.Request;
import org.lobobrowser.ua.UserAgentContext.RequestKind;

public final class RequestManager {
  private static final Logger logger = Logger.getLogger(RequestManager.class.getName());

  private final NavigatorFrame frame;

  public RequestManager(final NavigatorFrame frame) {
    this.frame = frame;
  }

  private static class RequestCounters {
    private final int counters[] = new int [UserAgentContext.RequestKind.values().length];

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
  private Optional<RequestRuleSet> ruleSetOpt = Optional.empty();

  private synchronized void updateCounter(final String host, final Request request) {
    if (!hostToCounterMap.containsKey(host)) {
      hostToCounterMap.put(host, new RequestCounters());
    }
    hostToCounterMap.get(host).updateCounts(request.kind);
  }

  public boolean isRequestPermitted(final Request request) {
    final NavigationEntry currentNavigationEntry = frame.getCurrentNavigationEntry();
    if (currentNavigationEntry != null) {
      if (!ruleSetOpt.isPresent()) {
        final String frameHost = currentNavigationEntry.getUrl().getHost();
        ruleSetOpt = Optional.of(RequestRuleSet.getRuleSet(frameHost));
      }
      final Boolean permitted = ruleSetOpt.map(ruleSet -> ruleSet.isRequestPermitted(request)).orElse(false);
      final String requestHost = request.url.getHost();
      updateCounter(requestHost, request);
      dumpCounters();
      return permitted;
    } else {
      logger.warning("Unexpected navigation state. Request without context!");
      return false;
    }
  }

  private synchronized void dumpCounters() {
    // Headers
    System.out.print(String.format("%30s  ", ""));
    getRequestKindNames().forEach(kindName -> System.out.print(" " + kindName.substring(0, 2)));
    System.out.println("");

    // Table rows
    hostToCounterMap.forEach((host, counters) -> {
      System.out.println(String.format("%30s: %s", host, counters));
    });
  }

  private static Stream<String> getRequestKindNames() {
    return Arrays.stream(RequestKind.values()).map(kind -> kind.name());
  }

  public synchronized void reset() {
    hostToCounterMap = new HashMap<>();
  }

  public void manageRequests() {
    System.out.println("Creating mg dialog");
    final ManageDialog dlg = new ManageDialog(new JFrame(), "title");
    dlg.setVisible(true);
  }

  public class ManageDialog extends JDialog implements ActionListener {
    public ManageDialog(final JFrame parent, final String title) {
      super(parent, title, true);
      if (parent != null) {
        final Dimension parentSize = parent.getSize();
        final Point p = parent.getLocation();
        setLocation(p.x + parentSize.width / 4, p.y + parentSize.height / 4);
      }

      final String[] columnNames = getColumnNames();
      final Object[][] requestData = getRequestData();

      final JTable table = new JTable(requestData, columnNames);
      final JScrollPane scrollTablePane = new JScrollPane(table);
      table.setFillsViewportHeight(true);

      getContentPane().add(scrollTablePane);

      final JPanel buttonPane = new JPanel();
      final JButton button = new JButton("OK");
      buttonPane.add(button);
      button.addActionListener(this);
      getContentPane().add(buttonPane, BorderLayout.SOUTH);
      setDefaultCloseOperation(DISPOSE_ON_CLOSE);

      pack();
    }

    private Object[][] getRequestData() {
      return hostToCounterMap.entrySet().stream().map(entry -> {
        final List<Object> rowElements = new LinkedList<>();
        rowElements.add(entry.getKey());
        Arrays.stream(entry.getValue().counters).forEach(c -> rowElements.add(c));

        return rowElements.toArray();
      }).toArray(Object[][]::new);
    }

    private String[] getColumnNames() {
      final List<String> kindNames = getRequestKindNames().collect(Collectors.toList());
      kindNames.add(0, "Host");
      return kindNames.toArray(new String[0]);
    }

    public void actionPerformed(final ActionEvent e) {
      setVisible(false);
      dispose();
    }

  }

}

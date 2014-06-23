package org.lobobrowser.security;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.lobobrowser.ua.NavigatorFrame;
import org.lobobrowser.ua.UserAgentContext;
import org.lobobrowser.ua.UserAgentContext.Request;

public class RequestManager {
  private final NavigatorFrame frame;

  public RequestManager(final NavigatorFrame frame) {
    this.frame = frame;
  }

  private static class RequestCounters {
    private int counters[] = new int [UserAgentContext.RequestKind.values().length];

    public void updateCounts(int id) {
      counters[id]++;
    }

    @Override
    public String toString() {
      return Arrays.stream(counters).mapToObj(c -> String.format(" %2d", c)).reduce((e,a) -> a + e).orElse("");
    }
  }

  private final Map<String, RequestCounters> hostToCounterMap = new HashMap<>();

  private synchronized void updateCounter(final String host, final Request request) {
    if (!hostToCounterMap.containsKey(host)) {
      hostToCounterMap.put(host, new RequestCounters());
    }
    hostToCounterMap.get(host).updateCounts(request.kind.ordinal());
  }

  public boolean isRequestPermitted(final Request request) {
    // final String frameHost = frame.getCurrentNavigationEntry().getUrl().getHost();
    final String requestHost = request.url.getHost();
    updateCounter(requestHost, request);
    dumpCounters();

    System.out.println("Checking :" + request);
    System.out.println("  context: " + frame.getCurrentNavigationEntry());
    System.out.println("  parent: " + frame.getParentFrame());
    boolean permitted = false;
    switch (request.kind) {
    case CSS:
      permitted = true;
      break;
    case Cookie:
      break;
    case ExternalScript:
      break;
    case Frame:
      break;
    case Image:
      permitted = true;
      break;
    case InlineScript:
      break;
    case XHR:
      break;
    default:
      break;
    }
    return permitted;
  }

  private synchronized void dumpCounters() {
    hostToCounterMap.forEach((host, counters) -> {
      System.out.println(String.format("%20s: %s", host, counters));
    });
  }

  public synchronized void reset() {
    hostToCounterMap = new HashMap<>();
  }

}

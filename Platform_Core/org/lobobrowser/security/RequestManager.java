package org.lobobrowser.security;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.lobobrowser.ua.NavigatorFrame;
import org.lobobrowser.ua.UserAgentContext;
import org.lobobrowser.ua.UserAgentContext.CSSRequest;
import org.lobobrowser.ua.UserAgentContext.CookieRequest;
import org.lobobrowser.ua.UserAgentContext.FrameRequest;
import org.lobobrowser.ua.UserAgentContext.ImageRequest;
import org.lobobrowser.ua.UserAgentContext.Request;

public class RequestManager {
  private final NavigatorFrame frame;

  public RequestManager(final NavigatorFrame frame) {
    this.frame = frame;
  }

  private static class RequestCounters {
    private int counters[] = new int [UserAgentContext.COUNT_REQUEST_ID];

    public void updateCounts(int id) {
      counters[id]++;
    }

    @Override
    public String toString() {
      return Arrays.stream(counters).mapToObj(c -> String.format(" %2d", c)).reduce((e,a) -> a + e).orElse("");
    }
  }

  private final Map<String, RequestCounters> hostToCounterMap = new HashMap();

  private synchronized void updateCounter(final String host, final Request request) {
    if (!hostToCounterMap.containsKey(host)) {
      hostToCounterMap.put(host, new RequestCounters());
    }
    hostToCounterMap.get(host).updateCounts(request.id);
  }

  public boolean isRequestPermitted(final Request request) {
    // final String frameHost = frame.getCurrentNavigationEntry().getUrl().getHost();
    final String requestHost = request.url.getHost();
    updateCounter(requestHost, request);
    dumpCounters();

    System.out.println("Checking :" + request);
    System.out.println("  context: " + frame.getCurrentNavigationEntry());
    System.out.println("  parent: " + frame.getParentFrame());
    if (request instanceof CookieRequest) {
      final CookieRequest cookieRequest = (CookieRequest) request;
      return true;
    } else if (request instanceof ImageRequest) {
      return true;
    } else if (request instanceof CSSRequest) {
      return true;
    } else if (request instanceof FrameRequest) {
      return false;
    }
    return false;
  }

  private synchronized void dumpCounters() {
    hostToCounterMap.forEach((host, counters) -> {
      System.out.println(String.format("%20s: %s", host, counters));
    });
  }

}
